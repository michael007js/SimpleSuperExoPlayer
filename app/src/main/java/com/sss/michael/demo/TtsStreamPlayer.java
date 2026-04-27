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
import com.sss.michael.exo.callback.SimpleExoNotifyCallBack;
import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.util.ExoLog;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizer;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerListener;
import com.tencent.cloud.stream.tts.FlowingSpeechSynthesizerRequest;
import com.tencent.cloud.stream.tts.SpeechSynthesizerResponse;
import com.tencent.cloud.stream.tts.core.exception.SynthesizerException;
import com.tencent.cloud.stream.tts.core.ws.Credential;
import com.tencent.cloud.stream.tts.core.ws.SpeechClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 腾讯云流式 TTS demo 播放辅助类。
 *
 * <p>本类的接入方式刻意对齐腾讯云官方 stream_tts_demo 的调用顺序：
 * 创建 {@link FlowingSpeechSynthesizer}，先 {@link FlowingSpeechSynthesizer#start()}，
 * 再按分段结果多次调用 {@link FlowingSpeechSynthesizer#process(String)}，
 * 最后调用 {@link FlowingSpeechSynthesizer#stop()} 声明文本输入结束。
 *
 * <p>不同点在于，官方 demo 直接把 {@link FlowingSpeechSynthesizerListener#onAudioResult(ByteBuffer)}
 * 返回的 PCM 写入 {@code AudioTrack}；这里改为把 PCM 交给 {@link SimpleExoPlayerView} 新增的
 * PCM 流式链路，从而复用库里已有的频谱分析、均衡器处理和统一状态分发能力。
 *
 * <p>线程模型说明：
 * <ul>
 *     <li>公开方法建议由主线程调用，便于与页面生命周期和音频焦点保持一致。</li>
 *     <li>腾讯云 SDK 的文本提交在独立工作线程中执行，避免阻塞界面线程。</li>
 *     <li>PCM 数据只负责转交给播放器内部队列，不在腾讯云回调线程里做重逻辑。</li>
 * </ul>
 */
public class TtsStreamPlayer {

    /**
     * 腾讯云 demo 账号参数。
     *
     * <p>仅用于本地 demo 验证。实际业务接入时应替换为自己的 AppId / SecretId / SecretKey。
     */
    private static final String APP_ID = "";
    private static final String SECRET_ID = "";
    private static final String SECRET_KEY = "";

    /**
     * demo 当前固定使用的采样率。
     */
    private static final int SAMPLE_RATE_HZ = 16000;

    /**
     * demo 当前固定使用的声道数。
     */
    private static final int CHANNEL_COUNT = 1;

    /**
     * 默认音色类型，与腾讯云官方 demo 保持一致。
     */
    private static final int DEFAULT_VOICE_TYPE = 1001;

    /**
     * 一级分段后允许继续直接提交的最大 UTF-8 字节数。
     *
     * <p>该值来自业务侧已验证方案，用于避免单次 {@code process(...)} 对应的云端任务过大，
     * 从而在长文本场景下产生过于集中的 PCM 回包峰值。
     */
    private static final int MAX_TTS_CHUNK_BYTES = 150;

    /**
     * 二级分段后仍然过长时的强制切块字符数。
     *
     * <p>这里按字符数而不是字节数再切一次，是为了把极长逗号分句继续打散，
     * 让腾讯云侧的任务颗粒度更加均匀。
     */
    private static final int FORCED_TTS_CHUNK_CHAR_COUNT = 80;

    /**
     * 本地 PCM 排队达到该时长后，暂缓继续向腾讯云提交后续文本。
     *
     * <p>该阈值只用于音频回调侧的高低水位控制，不再参与文本提交流程。
     * 文本提交流程会严格对齐官方 demo：只做 {@code start -> process* -> stop}。
     */
    private static final long PCM_QUEUE_HIGH_WATER_MARK_MS = 12_000L;

    /**
     * demo 允许播放器缓存的最大 PCM 时长。
     *
     * <p>长文本朗读场景下，腾讯云在单个分段内仍可能连续回出较长音频，因此 demo 需要比默认值更宽的缓冲窗口，
     * 否则很容易在后半段进入“连续拒绝追加”的状态。
     */
    private static final long DEMO_MAX_QUEUED_DURATION_MS = 120_000L;

    /**
     * 背压等待轮询间隔。
     */
    private static final long PCM_QUEUE_WAIT_INTERVAL_MS = 40L;

    /**
     * 音频回调侧的阻塞阈值。
     *
     * <p>一旦播放器内部待播时长达到该阈值，就在腾讯云的音频回调侧短暂等待，
     * 用接近官方 demo 直接 {@code AudioTrack.write(...)} 的方式把“播放速度”反向传导给云端回包。
     */
    private static final long PCM_APPEND_BLOCK_HIGH_WATER_MARK_MS = 18_000L;

    /**
     * 音频回调侧解除阻塞的目标阈值。
     *
     * <p>使用高低水位而不是单点阈值，可以避免队列时长贴着单一边界来回抖动。
     */
    private static final long PCM_APPEND_BLOCK_LOW_WATER_MARK_MS = 12_000L;

    /**
     * 匹配“包含有效内容”的字符模式。
     *
     * <p>拆分后的片段如果只剩标点或空白，就不应该继续提交给 TTS。
     */
    private static final Pattern CONTENT_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]|[A-Z]|[a-z]|[0-9]");

    /**
     * 匹配“全是标点”的文本片段。
     */
    private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}+");

    /**
     * 复用全局 {@link SpeechClient}，避免每次 demo 调用都重复创建底层 WebSocket 代理。
     */
    private static final SpeechClient PROXY = new SpeechClient();

    private final Context appContext;
    private final SimpleExoPlayerView playerView;
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile FlowingSpeechSynthesizer synthesizer;
    private volatile Thread synthesizerThread;
    private volatile boolean isPaused;
    /**
     * 是否已经收到“主动停止 / 销毁 / 异常收尾”的终止指令。
     *
     * <p>该标记只表示当前会话是否还允许继续推进，不等同于“腾讯云已经回完全部 PCM”
     * 或“播放器底层已经把全部缓冲播放完成”。
     */
    private volatile boolean stopRequested = true;
    /**
     * 是否已经收到腾讯云“文本输入结束”的信号。
     *
     * <p>收到该状态后不再会有新的 PCM 进入播放器，但播放器底层可能仍有待播缓冲。
     */
    private volatile boolean synthesisCompleted;
    private OnTtsPlayerCallback onTtsPlayerCallback;

    public TtsStreamPlayer(@NonNull Context context, @NonNull SimpleExoPlayerView playerView) {
        this.appContext = context.getApplicationContext();
        this.playerView = playerView;
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        this.playerView.setExoNotifyCallBack(new SimpleExoNotifyCallBack() {
            @Override
            public void onExoRenderedFirstFrame() {
            }

            @Override
            public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
                if (playbackState == ExoPlaybackState.STATE_ENDED) {
                    handlePlaybackCompleted();
                }
            }

            @Override
            public void onShortVideoComponentChangedAction(boolean clearScreenMode,
                                                           ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView) {
            }
        });
        this.audioFocusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                onStop();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                onPause();
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                if (isPaused && !stopRequested) {
                    onResume();
                }
            }
        };
    }

    /**
     * 开始一次新的流式 TTS 合成与播放。
     *
     * <p>流程与腾讯云官方 demo 保持一致：
     * 1. 先对文本进行过滤和拆分；
     * 2. 初始化播放器的 PCM 流式模式；
     * 3. 创建腾讯云合成器并依次提交文本片段；
     * 4. 所有片段提交完成后调用 {@link FlowingSpeechSynthesizer#stop()}。
     *
     * @param text 待合成文本
     */
    public void start(String text) {
        String normalizedText = filterEmoji(text).trim();
        if (TextUtils.isEmpty(normalizedText)) {
            notifyError("请输入要合成的文本");
            return;
        }

        List<String> textChunks = split(normalizedText);
        if (textChunks.isEmpty()) {
            notifyError("输入文本在过滤后不包含可合成内容");
            return;
        }

        stopInternal(false);
        requestAudioFocus();
        playerView.startPcmStream(new ExoPcmStreamConfig()
                .setSampleRateHz(SAMPLE_RATE_HZ)
                .setChannelCount(CHANNEL_COUNT)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setMaxQueuedDurationMs(DEMO_MAX_QUEUED_DURATION_MS));

        isPaused = false;
        stopRequested = false;
        synthesisCompleted = false;
        dispatchOnStart();

        Credential credential = new Credential(APP_ID, SECRET_ID, SECRET_KEY, "");
        FlowingSpeechSynthesizerRequest request = buildRequest();
        FlowingSpeechSynthesizerListener listener = buildListener();
        Thread worker = new Thread(() -> runSynthesisLoop(textChunks, credential, request, listener),
                "TtsStreamDemoWorker");
        synthesizerThread = worker;
        worker.start();
    }

    /**
     * 暂停当前播放。
     *
     * <p>该操作只暂停本地播放器输出。腾讯云侧已经在途的网络回包不会回滚，
     * 但由于文本提交线程会感知本地排队长度，因此在暂停后会逐步进入等待状态。
     */
    public void onPause() {
        if (stopRequested || isPaused) {
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
        if (stopRequested || !isPaused) {
            return;
        }
        playerView.resume();
        isPaused = false;
        dispatchOnResume();
    }

    /**
     * 停止当前合成与播放。
     */
    public void onStop() {
        stopInternal(true);
    }

    /**
     * 销毁当前 helper。
     *
     * <p>调用后不应继续复用当前实例。
     */
    public void onDestroy() {
        stopInternal(false);
    }

    public void setOnTtsPlayerCallback(OnTtsPlayerCallback onTtsPlayerCallback) {
        this.onTtsPlayerCallback = onTtsPlayerCallback;
    }

    /**
     * 在独立工作线程中执行一次完整的文本提交流程。
     *
     * <p>这里保留了官方 demo “逐段调用 {@code process(...)}” 的基本结构，
     * 同时增加了本地 PCM 队列背压等待，避免长文本在后半段持续堆积。
     */
    private void runSynthesisLoop(List<String> textChunks,
                                  Credential credential,
                                  FlowingSpeechSynthesizerRequest request,
                                  FlowingSpeechSynthesizerListener listener) {
        try {
            FlowingSpeechSynthesizer localSynthesizer =
                    new FlowingSpeechSynthesizer(PROXY, credential, request, listener);
            synthesizer = localSynthesizer;
            localSynthesizer.start();

            for (String textChunk : textChunks) {
                if (shouldStopWorker()) {
                    break;
                }
                localSynthesizer.process(textChunk);
            }

            if (!stopRequested) {
                localSynthesizer.stop();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!stopRequested) {
                handleFailure("文本提交流程被中断");
            }
        } catch (SynthesizerException e) {
            if (!stopRequested) {
                ExoLog.log("TtsStream synthesizer exception: " + e.getMessage(), e);
                handleFailure("语音合成失败: " + e.getMessage());
            }
        } catch (Exception e) {
            if (!stopRequested) {
                ExoLog.log("TtsStream exception: " + e.getMessage(), e);
                handleFailure("流式播放异常: " + e.getMessage());
            }
        } finally {
            if (Thread.currentThread() == synthesizerThread) {
                synthesizerThread = null;
            }
        }
    }

    private boolean shouldStopWorker() {
        return stopRequested || Thread.currentThread().isInterrupted();
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
     * <p>回调里不直接处理 {@code AudioTrack}，而是统一把 PCM 转交给播放器的 PCM 流式核心，
     * 保证频谱与均衡器处理仍然由播放器内部链路负责。
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
                synthesisCompleted = true;
                playerView.completePcmStream();
                dispatchOnEnd();
            }

            @Override
            public void onAudioResult(ByteBuffer buffer) {
                if (stopRequested || buffer == null || !buffer.hasRemaining()) {
                    return;
                }
                byte[] audioData = new byte[buffer.remaining()];
                buffer.get(audioData);
                try {
                    waitForAppendCapacity();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (!stopRequested) {
                        handleFailure("音频回调线程等待播放器队列回落时被中断");
                    }
                    return;
                }
                if (stopRequested) {
                    return;
                }
                playerView.appendPcmData(audioData, 0, audioData.length);
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
                if (stopRequested) {
                    return;
                }
                String errorMessage = response == null
                        ? "未知错误"
                        : "code=" + response.getCode() + ", msg=" + response.getMessage();
                ExoLog.log("TtsStream onSynthesisFail: " + errorMessage);
                handleFailure("语音合成失败: " + errorMessage);
            }
        };
    }

    /**
     * 按业务侧已验证的规则拆分长文本。
     *
     * <p>拆分顺序与用户给定参考实现一致：
     * 先过滤 emoji，再按句末标点切一级；
     * 单段过长时，再按逗号和分号切二级；
     * 仍然过长时，按 80 个字符强制切块。
     *
     * @param text 原始待合成文本
     * @return 可直接逐段提交给腾讯云流式 TTS 的文本片段列表
     */
    private List<String> split(String text) {
        String filteredText = filterEmoji(text, " ");
        List<String> result = new ArrayList<>();
        List<String> primarySegments = separatedText(filteredText, "[:：。！？!?]");
        for (String primarySegment : primarySegments) {
            if (getUtf8ByteLength(primarySegment) > MAX_TTS_CHUNK_BYTES) {
                List<String> secondarySegments = separatedText(primarySegment, "[，；,;]");
                for (String secondarySegment : secondarySegments) {
                    appendChunkByCharLimit(result, secondarySegment);
                }
            } else if (!isEmpty(primarySegment) && !isPunct(primarySegment)) {
                result.add(primarySegment);
            }
        }
        return result;
    }

    /**
     * 按给定分隔符集合切分文本，并尽量保留分隔符本身。
     *
     * <p>该实现刻意保持与业务侧参考代码相同的处理语义：
     * 如果最后一段只包含无效字符，则尝试拼回前一段，避免孤立的无意义尾巴片段。
     *
     * @param text 待切分文本
     * @param splitChars 分隔符集合字符串
     * @return 切分后的文本片段
     */
    private List<String> separatedText(String text, String splitChars) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length(); index++) {
            String current = text.substring(index, index + 1);
            if (splitChars.contains(current)) {
                String segment = text.substring(start, index + 1);
                Matcher matcher = CONTENT_PATTERN.matcher(segment);
                if (matcher.find()) {
                    result.add(segment);
                    start = index + 1;
                }
            }
            if (index + 1 == text.length() && !splitChars.contains(current)) {
                String tail = text.substring(start, index + 1);
                Matcher matcher = CONTENT_PATTERN.matcher(tail);
                if (!matcher.find()) {
                    if (!result.isEmpty()) {
                        String lastSegment = result.get(result.size() - 1);
                        result.set(result.size() - 1, lastSegment + tail);
                    }
                } else {
                    result.add(tail);
                }
            }
        }
        return result;
    }

    /**
     * 将仍然过长的片段按固定字符数进一步切块。
     *
     * <p>这里按字符数切块是为了保持与业务侧参考实现一致，
     * 避免在长句场景下一次提交过大的语音合成任务。
     */
    private void appendChunkByCharLimit(List<String> container, String sentence) {
        int chunkCount = (int) Math.ceil(sentence.length() / (float) FORCED_TTS_CHUNK_CHAR_COUNT);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * FORCED_TTS_CHUNK_CHAR_COUNT;
            int end = Math.min(sentence.length(), start + FORCED_TTS_CHUNK_CHAR_COUNT);
            String chunk = sentence.substring(start, end);
            if (!isEmpty(chunk) && !isPunct(chunk)) {
                container.add(chunk);
            }
        }
    }

    /**
     * 在腾讯云音频回调侧等待本地播放器队列回落。
     *
     * <p>官方 demo 直接在 {@code onAudioResult(...)} 中调用 {@code AudioTrack.write(...)}，
     * 底层天然会因为设备播放速度而形成阻塞式背压。当前工程改为“腾讯云回调 -> 播放器内部队列”，
     * 因此需要在这里显式补回这层背压，否则云端回包可能长期快于本地播速，最终把 PCM 队列堆满。
     *
     * <p>这里使用高低水位控制：
     * 当待播时长达到高水位后开始等待，直到回落到低水位再继续追加，
     * 从而避免队列长度在单一阈值附近频繁抖动。
     *
     * @throws InterruptedException 当前回调线程在等待过程中被中断时抛出
     */
    private void waitForAppendCapacity() throws InterruptedException {
        while (!stopRequested && playerView.isPcmStreaming()) {
            long queuedDurationMs = playerView.getQueuedPcmDurationMs();
            if (queuedDurationMs < PCM_APPEND_BLOCK_HIGH_WATER_MARK_MS) {
                return;
            }
            do {
                Thread.sleep(PCM_QUEUE_WAIT_INTERVAL_MS);
                if (stopRequested || !playerView.isPcmStreaming()) {
                    return;
                }
                queuedDurationMs = playerView.getQueuedPcmDurationMs();
            } while (queuedDurationMs > PCM_APPEND_BLOCK_LOW_WATER_MARK_MS);
            return;
        }
    }

    /**
     * 判断是否存在空串或仅包含空白的片段。
     */
    private boolean isEmpty(String... values) {
        for (String value : values) {
            if (value == null || value.trim().length() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断片段是否仅由标点构成。
     */
    private boolean isPunct(String value) {
        return value != null && PUNCT_PATTERN.matcher(value).matches();
    }

    /**
     * 计算文本按 UTF-8 编码后的字节长度。
     */
    private int getUtf8ByteLength(String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 过滤 emoji，并默认以空格替换。
     */
    private String filterEmoji(String source) {
        return filterEmoji(source, " ");
    }

    /**
     * 过滤 emoji 或其他代理对字符，避免提交腾讯云 TTS 不支持的字符。
     *
     * @param source 原始文本
     * @param replacement 替换文本
     * @return 过滤后的结果；输入为空时返回空串
     */
    private String filterEmoji(String source, String replacement) {
        if (TextUtils.isEmpty(source)) {
            return "";
        }
        return source.replaceAll("[\\ud800\\udc00-\\udbff\\udfff\\ud800-\\udfff]", replacement);
    }

    /**
     * 统一处理流式合成失败场景。
     *
     * <p>腾讯云 SDK 的异常通常发生在其工作线程中，播放器停止与页面状态更新更适合切回主线程统一收口，
     * 避免在失败线程里再次触发阻塞等待或中断链路。
     *
     * @param errorMessage 需要回调给页面层的错误信息
     */
    private void handleFailure(String errorMessage) {
        mainHandler.post(() -> {
            stopInternal(false);
            if (onTtsPlayerCallback != null) {
                onTtsPlayerCallback.onError(errorMessage);
            }
        });
    }

    /**
     * 停止当前任务的合成与播放资源。
     *
     * <p>清理顺序必须先取消腾讯云会话与播放器 PCM 会话，再对旧线程补发中断，
     * 从而避免当前线程在等待播放器收尾时先把自己打断。
     *
     * @param notifyStop 是否向页面层派发“主动停止”回调
     */
    private void stopInternal(boolean notifyStop) {
        stopRequested = true;
        synthesisCompleted = false;
        isPaused = false;

        Thread threadToInterrupt = synthesizerThread;
        synthesizerThread = null;

        FlowingSpeechSynthesizer currentSynthesizer = synthesizer;
        synthesizer = null;
        if (currentSynthesizer != null) {
            try {
                currentSynthesizer.cancel();
            } catch (Exception e) {
                ExoLog.log("TtsStream cancel exception: " + e.getMessage(), e);
            }
        }

        playerView.cancelPcmStream();

        if (threadToInterrupt != null && threadToInterrupt != Thread.currentThread()) {
            threadToInterrupt.interrupt();
        }

        abandonAudioFocus();

        if (notifyStop) {
            dispatchOnStop();
        }
    }

    /**
     * 处理播放器真正进入结束态后的收尾逻辑。
     *
     * <p>只有当腾讯云已经结束输入，且播放器也已经把底层缓冲彻底播完，才会进入这里。
     */
    private void handlePlaybackCompleted() {
        if (!synthesisCompleted || stopRequested) {
            return;
        }
        stopRequested = true;
        isPaused = false;
        abandonAudioFocus();
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
     * demo 页面状态回调。
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
