package com.sss.michael.demo;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.sss.michael.exo.SimpleExoPlayerView;
import com.sss.michael.exo.bean.ExoPcmStreamConfig;
import com.sss.michael.exo.util.ExoLog;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizer;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerListener;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerRequest;
import com.tencent.cloud.stream.tts.SpeechSynthesizerResponse;
import com.tencent.cloud.stream.tts.core.exception.SynthesizerException;
import com.tencent.cloud.stream.tts.core.ws.Credential;
import com.tencent.cloud.stream.tts.core.ws.SpeechClient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯云流式 TTS Demo 播放器。
 *
 * <p>该类参考业务项目中的流式 TTS 播放封装实现，但输出端不再直接写 {@code AudioTrack}，
 * 而是把腾讯云 SDK 回调的 PCM 数据持续推给 {@link SimpleExoPlayerView} 新增的 PCM 流式 API。
 * 这样 demo 能完整演示：
 *
 * <ol>
 *     <li>业务层如何调用腾讯云流式 TTS SDK。</li>
 *     <li>如何把 {@link FlowingSpeechSynthesizerListener#onAudioResult(ByteBuffer)} 中的 PCM
 *     数据接入播放器。</li>
 *     <li>如何复用播放器侧已有的频谱和 EQ 能力，而不是在 demo 里重复维护播放链路。</li>
 * </ol>
 *
 * <p>线程约束：
 *
 * <ul>
 *     <li>公开控制方法建议在主线程调用。</li>
 *     <li>腾讯云 SDK 的合成过程运行在独立工作线程中。</li>
 *     <li>PCM 数据到达后直接转交给播放器内部队列，避免在 SDK 回调线程里执行重阻塞逻辑。</li>
 * </ul>
 */
public class TtsStreamPlayer {

    /**
     * 腾讯云示例账号参数。
     *
     * <p>该 demo 直接沿用现有项目中的测试配置，便于本地快速验证。若迁移到其他环境，请替换为
     * 自己的 AppId / SecretId / SecretKey。
     */
    private static final String APP_ID = "";
    private static final String SECRET_ID = "";
    private static final String SECRET_KEY = "";

    /**
     * demo 当前固定使用的 PCM 采样率。
     */
    private static final int SAMPLE_RATE_HZ = 16000;

    /**
     * demo 当前固定使用的声道数。
     */
    private static final int CHANNEL_COUNT = 1;

    /**
     * 默认音色 ID，使用腾讯云常见女声音色。
     */
    private static final int DEFAULT_VOICE_TYPE = 1001;

    /**
     * 复用全局 {@link SpeechClient}，避免每次演示都重复创建底层 WebSocket 代理。
     */
    private static final SpeechClient PROXY = new SpeechClient();

    private final Context appContext;
    private final SimpleExoPlayerView playerView;
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FlowingSpeechSynthesizer synthesizer;
    private Thread synthesizerThread;
    private boolean isPaused;
    private boolean isStopped = true;
    private OnTtsPlayerCallback onTtsPlayerCallback;

    public TtsStreamPlayer(@NonNull Context context, @NonNull SimpleExoPlayerView playerView) {
        this.appContext = context.getApplicationContext();
        this.playerView = playerView;
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        this.audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    onStop();
                } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    onPause();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    if (isPaused && !isStopped) {
                        onResume();
                    }
                }
            }
        };
    }

    /**
     * 开始新的流式 TTS 合成与播放。
     *
     * <p>调用时会先停止上一轮任务，再初始化播放器的 PCM 模式，然后启动腾讯云 SDK 工作线程。
     * SDK 回调到的 PCM 数据会逐块推入播放器，播放完成后由播放器自行把剩余缓冲播放完毕。
     *
     * @param text 待合成文本
     */
    public void start(String text) {
        String normalizedText = filterEmoji(text).trim();
        if (TextUtils.isEmpty(normalizedText)) {
            notifyError("请输入要合成的文本");
            return;
        }

        stopInternal(false);
        requestAudioFocus();

        playerView.startPcmStream(new ExoPcmStreamConfig()
                .setSampleRateHz(SAMPLE_RATE_HZ)
                .setChannelCount(CHANNEL_COUNT)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT));

        isPaused = false;
        isStopped = false;
        dispatchOnStart();

        final List<String> textChunks = splitText(normalizedText);
        final Credential credential = new Credential(APP_ID, SECRET_ID, SECRET_KEY, "");
        final FlowingSpeechSynthesizerRequest request = buildRequest();
        final FlowingSpeechSynthesizerListener listener = buildListener();

        synthesizerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synthesizer = new FlowingSpeechSynthesizer(PROXY, credential, request, listener);
                    synthesizer.start();
                    for (String textChunk : textChunks) {
                        if (isStopped || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        synthesizer.process(textChunk);
                    }
                    if (!isStopped) {
                        synthesizer.stop();
                    }
                } catch (SynthesizerException e) {
                    ExoLog.log("TtsStream synthesizer exception: " + e.getMessage(), e);
                    handleFailure("语音合成失败: " + e.getMessage());
                } catch (Exception e) {
                    ExoLog.log("TtsStream exception: " + e.getMessage(), e);
                    handleFailure("流式播放异常: " + e.getMessage());
                }
            }
        }, "TtsStreamDemoWorker");
        synthesizerThread.start();
    }

    /**
     * 暂停当前播放。
     *
     * <p>暂停只影响播放器输出，腾讯云 SDK 侧合成会继续推进，因此该操作适用于短暂停顿演示，
     * 不适合长时间挂起。
     */
    public void onPause() {
        if (isStopped || isPaused) {
            return;
        }
        playerView.pause(false);
        isPaused = true;
        dispatchOnPause();
    }

    /**
     * 恢复当前播放。
     */
    public void onResume() {
        if (isStopped || !isPaused) {
            return;
        }
        playerView.resume();
        isPaused = false;
        dispatchOnResume();
    }

    /**
     * 停止当前播放与合成。
     */
    public void onStop() {
        stopInternal(true);
    }

    /**
     * 销毁 demo 播放器。
     *
     * <p>调用后当前 helper 不应再继续使用。
     */
    public void onDestroy() {
        stopInternal(false);
    }

    public void setOnTtsPlayerCallback(OnTtsPlayerCallback onTtsPlayerCallback) {
        this.onTtsPlayerCallback = onTtsPlayerCallback;
    }

    private FlowingSpeechSynthesizerRequest buildRequest() {
        FlowingSpeechSynthesizerRequest request = new FlowingSpeechSynthesizerRequest();
        request.setCodec("pcm");
        request.setSampleRate(SAMPLE_RATE_HZ);
        request.setEnableSubtitle(false);
        request.setSessionId(UUID.randomUUID().toString());
        request.setSpeed(0f);
        request.setVolume(0f);
        request.setVoiceType(DEFAULT_VOICE_TYPE);
        return request;
    }

    /**
     * 构建腾讯云流式 TTS 回调。
     *
     * <p>回调里不直接做音频解码或复杂状态机处理，只负责把 PCM 数据转交给播放器，并在异常/结束
     * 时同步维护 demo 状态。
     */
    private FlowingSpeechSynthesizerListener buildListener() {
        return new FlowingSpeechSynthesizerListener() {
            @Override
            public void onSynthesisStart(SpeechSynthesizerResponse response) {
                ExoLog.log("TtsStream onSynthesisStart: " + response.getSessionId());
            }

            @Override
            public void onSynthesisEnd(SpeechSynthesizerResponse response) {
                ExoLog.log("TtsStream onSynthesisEnd: " + response.getSessionId());
                isStopped = true;
                playerView.completePcmStream();
                abandonAudioFocus();
                dispatchOnEnd();
            }

            @Override
            public void onAudioResult(ByteBuffer buffer) {
                if (isStopped || buffer == null || !buffer.hasRemaining()) {
                    return;
                }
                playerView.appendPcmData(buffer);
            }

            @Override
            public void onTextResult(SpeechSynthesizerResponse response) {
            }

            @Override
            public void onSynthesisCancel() {
                ExoLog.log("TtsStream onSynthesisCancel");
                abandonAudioFocus();
            }

            @Override
            public void onSynthesisFail(SpeechSynthesizerResponse response) {
                String errorMessage = response == null
                        ? "未知错误"
                        : "code=" + response.getCode() + ", msg=" + response.getMessage();
                ExoLog.log("TtsStream onSynthesisFail: " + errorMessage);
                handleFailure("语音合成失败: " + errorMessage);
            }
        };
    }

    /**
     * 根据常见中文和英文标点拆分长文本。
     *
     * <p>腾讯云流式 TTS 支持长文本，但 demo 按句切分后更利于观察“分段提交、连续输出”的行为，
     * 也更接近业务项目中的真实接入方式。
     */
    private List<String> splitText(String source) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(source)) {
            return result;
        }

        StringBuilder sentenceBuilder = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            sentenceBuilder.append(current);
            if (isSentenceSeparator(current)) {
                appendSentence(result, sentenceBuilder.toString());
                sentenceBuilder.setLength(0);
            }
        }
        appendSentence(result, sentenceBuilder.toString());
        if (result.isEmpty()) {
            result.add(source);
        }
        return result;
    }

    private boolean isSentenceSeparator(char value) {
        return value == '。'
                || value == '！'
                || value == '？'
                || value == '；'
                || value == '.'
                || value == '!'
                || value == '?'
                || value == ';'
                || value == '\n';
    }

    private void appendSentence(List<String> container, String sentence) {
        if (TextUtils.isEmpty(sentence)) {
            return;
        }
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z0-9]").matcher(sentence);
        if (matcher.find()) {
            container.add(sentence);
        }
    }

    private String filterEmoji(String source) {
        if (TextUtils.isEmpty(source)) {
            return "";
        }
        return source.replaceAll("[\\ud800\\udc00-\\udbff\\udfff\\ud800-\\udfff]", " ");
    }

    /**
     * 统一处理失败场景。
     *
     * <p>失败时需要同时停止腾讯云会话、取消播放器当前 PCM 队列、释放音频焦点，并通知 demo UI。
     */
    private void handleFailure(String errorMessage) {
        stopInternal(false);
        notifyError(errorMessage);
    }

    private void stopInternal(boolean notifyStop) {
        isStopped = true;
        isPaused = false;

        if (synthesizer != null) {
            try {
                synthesizer.cancel();
            } catch (Exception e) {
                ExoLog.log("TtsStream cancel exception: " + e.getMessage(), e);
            }
            synthesizer = null;
        }
        if (synthesizerThread != null) {
            synthesizerThread.interrupt();
            synthesizerThread = null;
        }

        playerView.cancelPcmStream();
        abandonAudioFocus();

        if (notifyStop && onTtsPlayerCallback != null) {
            dispatchOnStop();
        }
    }

    private void requestAudioFocus() {
        if (audioManager != null) {
            audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }
    }

    private void abandonAudioFocus() {
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void notifyError(String errorMessage) {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onError(errorMessage);
            }
        });
    }

    private void dispatchOnStart() {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onStart();
            }
        });
    }

    private void dispatchOnPause() {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onPause();
            }
        });
    }

    private void dispatchOnResume() {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onResume();
            }
        });
    }

    private void dispatchOnStop() {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onStop();
            }
        });
    }

    private void dispatchOnEnd() {
        mainHandler.post(() -> {
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onEnd();
            }
        });
    }

    /**
     * demo 页状态回调。
     */
    public interface OnTtsPlayerCallback {
        void onStart();

        void onPause();

        void onResume();

        void onStop();

        void onEnd();

        void onError(String errorMessage);
    }
}
