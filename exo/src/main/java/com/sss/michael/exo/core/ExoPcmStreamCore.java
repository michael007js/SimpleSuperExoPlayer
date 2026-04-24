package com.sss.michael.exo.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.bean.ExoPcmStreamConfig;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.callback.IExoNotifyCallBack;
import com.sss.michael.exo.constant.ExoAudioSourceType;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.constant.ExoPlayerMode;
import com.sss.michael.exo.processor.ExoStandaloneAudioProcessorChain;
import com.sss.michael.exo.util.ExoLog;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 独立 PCM 流式播放核心。
 *
 * <p>该类负责管理基于 {@link AudioTrack} 的 PCM 流式播放链路，并与现有
 * {@code ExoVideoView} 的 URL / MediaSource 主链保持解耦，以保证新增流式能力不会影响原有
 * ExoPlayer 播放逻辑。
 *
 * <p>线程模型如下：
 *
 * <ul>
 *     <li>生产线程通过 {@code appendPcmData(...)} 追加原始 PCM 分片，调用本身只负责入队，不直接写设备。</li>
 *     <li>独立工作线程独占 AudioTrack，串行执行取队列、DSP 处理和阻塞写入。</li>
 *     <li>所有面向 UI 的状态回调都会切回主线程分发。</li>
 * </ul>
 */
@UnstableApi
public class ExoPcmStreamCore {

    private enum StreamState {
        IDLE,
        PREPARED,
        PLAYING,
        PAUSED,
        COMPLETED,
        CANCELED,
        RELEASED
    }

    private final Context appContext;
    private final View playerView;
    private final ExoPlayerInfo playerInfo;
    private final IExoNotifyCallBack iExoNotifyCallBack;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread workerThread;
    private final Handler workerHandler;
    private final Object queueLock = new Object();
    private final ArrayDeque<byte[]> pendingPcmQueue = new ArrayDeque<>();
    private final Runnable drainRunnable = this::drainPendingPcmQueue;
    private final IExoFFTCallBack mainThreadFftCallBack;

    private AudioTrack audioTrack;
    private ExoStandaloneAudioProcessorChain audioProcessorChain;
    private ExoPcmStreamConfig currentConfig;
    private StreamState streamState = StreamState.IDLE;
    private boolean drainScheduled;
    private boolean inputCompleted;
    private boolean firstFrameDispatched;
    private long queuedBytes;
    private long totalInputBytes;
    private long totalWrittenBytes;

    public ExoPcmStreamCore(@NonNull Context context,
                            @NonNull View playerView,
                            @NonNull ExoPlayerInfo playerInfo,
                            @NonNull IExoNotifyCallBack iExoNotifyCallBack) {
        this.appContext = context.getApplicationContext();
        this.playerView = playerView;
        this.playerInfo = playerInfo;
        this.iExoNotifyCallBack = iExoNotifyCallBack;
        this.mainThreadFftCallBack = new IExoFFTCallBack() {
            @Override
            public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
                mainHandler.post(() -> {
                    if (ExoPcmStreamCore.this.iExoNotifyCallBack instanceof IExoFFTCallBack) {
                        ((IExoFFTCallBack) ExoPcmStreamCore.this.iExoNotifyCallBack)
                                .onFFTReady(sampleRateHz, channelCount, fft);
                    }
                });
            }

            @Override
            public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
                mainHandler.post(() -> {
                    if (ExoPcmStreamCore.this.iExoNotifyCallBack instanceof IExoFFTCallBack) {
                        ((IExoFFTCallBack) ExoPcmStreamCore.this.iExoNotifyCallBack)
                                .onMagnitudeReady(sampleRateHz, magnitude);
                    }
                });
            }
        };
        workerThread = new HandlerThread("ExoPcmStreamWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    /**
     * 启动新的 PCM 流式会话，并完整重置上一轮独立流播放状态。
     *
     * @param config 本次会话的配置快照
     */
    public void startPcmStream(@NonNull ExoPcmStreamConfig config) {
        ensureUsable();
        currentConfig = new ExoPcmStreamConfig(config);
        validateConfig(currentConfig);
        runOnWorkerBlocking(() -> prepareNewStreamOnWorker(currentConfig));
    }

    /**
     * 复制并追加一段 PCM 数据，供异步工作线程后续处理和播放。
     *
     * @param data 原始 PCM 字节数组
     * @param offset 源数组偏移
     * @param length 需要入队的 PCM 字节数
     */
    public void appendPcmData(byte[] data, int offset, int length) {
        ensureUsable();
        if (data == null || length <= 0) {
            return;
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IndexOutOfBoundsException("appendPcmData range is out of bounds");
        }
        if (currentConfig == null || streamState == StreamState.RELEASED) {
            dispatchError("PCM 流尚未初始化，无法追加音频数据", null);
            return;
        }

        byte[] pcmChunk = new byte[length];
        System.arraycopy(data, offset, pcmChunk, 0, length);

        synchronized (queueLock) {
            if (streamState == StreamState.CANCELED || streamState == StreamState.COMPLETED) {
                dispatchError("当前 PCM 会话已经结束，无法继续追加音频数据", null);
                return;
            }

            long maxQueuedBytes = durationMsToBytes(currentConfig.getMaxQueuedDurationMs());
            if (maxQueuedBytes > 0 && queuedBytes + pcmChunk.length > maxQueuedBytes) {
                // 队列上限按“时长”控制，而不是仅按块数或字节数控制。这样不同采样率和声道
                // 的会话都能获得一致的背压语义，同时避免 AudioTrack 跟不上时持续堆内存。
                dispatchError("PCM 队列已达到上限，本次音频分片已被丢弃", null);
                ExoLog.log("PCM 队列超限，拒绝追加 " + pcmChunk.length + " bytes");
                return;
            }

            pendingPcmQueue.addLast(pcmChunk);
            queuedBytes += pcmChunk.length;
            totalInputBytes += pcmChunk.length;
            inputCompleted = false;
            updatePlayerInfoWithQueueStateLocked();
            scheduleDrainLocked();
        }
    }

    /**
     * 声明当前 PCM 输入已经结束。
     */
    public void completePcmStream() {
        ensureUsable();
        synchronized (queueLock) {
            if (streamState == StreamState.RELEASED || streamState == StreamState.CANCELED) {
                return;
            }
            inputCompleted = true;
            scheduleDrainLocked();
            if (queuedBytes == 0) {
                workerHandler.post(this::handleStreamCompletedOnWorker);
            }
        }
    }

    /**
     * 立即取消当前 PCM 会话，并清空所有待播数据。
     */
    public void cancelPcmStream() {
        if (streamState == StreamState.RELEASED) {
            return;
        }
        runOnWorkerBlocking(this::cancelStreamOnWorker);
    }

    /**
     * 暂停 AudioTrack 播放，同时保留尚未消费的 PCM 队列。
     *
     * @param callFromActive 为与既有播放器控制契约保持一致而保留
     */
    public void pause(boolean callFromActive) {
        if (streamState == StreamState.RELEASED) {
            return;
        }
        workerHandler.post(() -> {
            if (audioTrack == null || streamState == StreamState.CANCELED || streamState == StreamState.COMPLETED) {
                return;
            }
            try {
                audioTrack.pause();
                streamState = StreamState.PAUSED;
                dispatchPlaybackState(ExoPlaybackState.STATE_PLAY_PAUSE);
            } catch (Exception e) {
                dispatchError("PCM 流暂停失败", e);
            }
        });
    }

    /**
     * 恢复 AudioTrack 播放；若当前仍有待播数据，则继续启动队列消费。
     */
    public void resume() {
        if (streamState == StreamState.RELEASED) {
            return;
        }
        workerHandler.post(() -> {
            if (audioTrack == null || streamState == StreamState.CANCELED || streamState == StreamState.COMPLETED) {
                return;
            }
            try {
                audioTrack.play();
                if (queuedBytes > 0) {
                    streamState = StreamState.PLAYING;
                    dispatchPlaybackState(ExoPlaybackState.STATE_PLAYING);
                    drainPendingPcmQueue();
                } else {
                    streamState = StreamState.PREPARED;
                    dispatchPlaybackState(inputCompleted ? ExoPlaybackState.STATE_ENDED : ExoPlaybackState.STATE_BUFFERING);
                }
            } catch (Exception e) {
                dispatchError("PCM 流恢复播放失败", e);
            }
        });
    }

    /**
     * 停止独立流式播放，并回到空闲态。
     */
    public void stop() {
        cancelPcmStream();
    }

    /**
     * 将指定均衡器预设应用到独立 DSP 处理链。
     *
     * @param exoEqualizerPreset 需要生效的均衡器预设
     */
    public void setEqualizer(ExoEqualizerPreset exoEqualizerPreset) {
        if (streamState == StreamState.RELEASED || audioProcessorChain == null || exoEqualizerPreset == null) {
            return;
        }
        audioProcessorChain.setEqualizer(exoEqualizerPreset);
    }

    /**
     * 返回独立 PCM 链路当前是否仍处于可继续工作的流式状态。
     */
    public boolean isStreaming() {
        return streamState != StreamState.IDLE
                && streamState != StreamState.CANCELED
                && streamState != StreamState.RELEASED;
    }

    /**
     * 返回 AudioTrack 当前是否处于主动播放状态。
     */
    public boolean isPlaying() {
        return streamState == StreamState.PLAYING;
    }

    /**
     * 返回当前会话已经接收的 PCM 总时长。
     */
    public long getDuration() {
        if (currentConfig == null) {
            return 0L;
        }
        return bytesToDurationMs(totalInputBytes);
    }

    /**
     * 根据已写入 AudioTrack 的字节数估算当前已播放时长。
     */
    public long getCurrentPosition() {
        if (currentConfig == null) {
            return 0L;
        }
        return bytesToDurationMs(totalWrittenBytes);
    }

    /**
     * 返回当前排队待播 PCM 的预计时长。
     */
    public long getQueuedPcmDurationMs() {
        if (currentConfig == null) {
            return 0L;
        }
        synchronized (queueLock) {
            return bytesToDurationMs(queuedBytes);
        }
    }

    /**
     * 永久释放独立 AudioTrack 播放链路。
     */
    public void release() {
        if (streamState == StreamState.RELEASED) {
            return;
        }
        runOnWorkerBlocking(this::releaseInternalOnWorker);
        workerThread.quitSafely();
        streamState = StreamState.RELEASED;
    }

    private void prepareNewStreamOnWorker(ExoPcmStreamConfig config) {
        // startPcmStream 需要具备幂等性。无论此前是否已有流会话，都先完整回收旧资源，
        // 防止不同采样率/声道配置复用到上一个 AudioTrack 或 DSP 链导致格式错配。
        cancelStreamResourcesOnWorker();
        inputCompleted = false;
        firstFrameDispatched = false;
        totalInputBytes = 0L;
        totalWrittenBytes = 0L;
        queuedBytes = 0L;

        try {
            // 顺序固定为 EQ -> FFT。频谱分析看到的是“均衡器处理后的最终音频”，这样 UI
            // 展示与用户实际听到的声音保持一致，也避免维护第二套 DSP 逻辑。
            audioProcessorChain = new ExoStandaloneAudioProcessorChain(
                    config.getSampleRateHz(),
                    config.getChannelCount(),
                    mainThreadFftCallBack
            );
        } catch (AudioProcessor.UnhandledAudioFormatException e) {
            throw new IllegalStateException("PCM 处理链初始化失败", e);
        }

        audioTrack = buildAudioTrack(config);
        audioTrack.play();
        streamState = StreamState.PREPARED;

        playerInfo.setExoPlayMode(ExoPlayMode.MUSIC);
        playerInfo.setAudioSourceType(ExoAudioSourceType.PCM_STREAM);
        playerInfo.setFullScreen(false);
        playerInfo.setUri(null);
        dispatchPlayerState(ExoPlayerMode.PLAYER_NORMAL);
        dispatchPlaybackState(ExoPlaybackState.STATE_BUFFERING);
        dispatchProgress();
    }

    private AudioTrack buildAudioTrack(ExoPcmStreamConfig config) {
        int channelMask = getChannelMask(config.getChannelCount());
        int minBufferSize = AudioTrack.getMinBufferSize(
                config.getSampleRateHz(),
                channelMask,
                config.getEncoding()
        );
        if (minBufferSize <= 0) {
            throw new IllegalStateException("AudioTrack 最小缓冲区大小计算失败: " + minBufferSize);
        }

        int resolvedBufferSize = config.getBufferSizeInBytes() > 0
                ? config.getBufferSizeInBytes()
                : minBufferSize * 2;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            /*
             * API 23+ 使用 Builder 方式构建 AudioTrack，能够明确设置 AudioAttributes 和
             * AudioFormat，适合当前这条需要语义化配置的 PCM 流渲染链路。
             */
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(config.getAudioUsage())
                    .setContentType(config.getContentType())
                    .build();
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(config.getSampleRateHz())
                    .setEncoding(config.getEncoding())
                    .setChannelMask(channelMask)
                    .build();
            AudioTrack audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(resolvedBufferSize)
                    .build();
            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioTrack 初始化失败");
            }
            return audioTrack;
        }

        /*
         * API 21-22 无 Builder 构造方式，回退到传统构造函数。此时统一映射到 STREAM_MUSIC，
         * 以保证老设备上的兼容性和最小实现复杂度。
         */
        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                config.getSampleRateHz(),
                channelMask,
                config.getEncoding(),
                resolvedBufferSize,
                AudioTrack.MODE_STREAM
        );
        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioTrack 初始化失败");
        }
        return audioTrack;
    }

    private void drainPendingPcmQueue() {
        synchronized (queueLock) {
            drainScheduled = false;
        }
        if (audioTrack == null || audioProcessorChain == null) {
            return;
        }
        if (streamState == StreamState.PAUSED || streamState == StreamState.CANCELED
                || streamState == StreamState.COMPLETED || streamState == StreamState.RELEASED) {
            return;
        }

        byte[] pcmChunk = dequeueNextPendingPcmChunk();
        while (pcmChunk != null) {

            try {
                // PCM 分片始终先经过复用的音频处理链，再进入 AudioTrack。这样 URL 主链和
                // 流式主链共享同一套 EQ/频谱能力，行为保持一致。
                byte[] processedChunk = audioProcessorChain.process(pcmChunk, 0, pcmChunk.length);
                writeFully(processedChunk);
                totalWrittenBytes += processedChunk.length;

                if (!firstFrameDispatched) {
                    firstFrameDispatched = true;
                    mainHandler.post(iExoNotifyCallBack::onExoRenderedFirstFrame);
                }
                streamState = StreamState.PLAYING;
                dispatchPlaybackState(ExoPlaybackState.STATE_PLAYING);
                dispatchProgress();
            } catch (Exception e) {
                dispatchError("PCM 数据写入 AudioTrack 失败", e);
                cancelStreamOnWorker();
                return;
            }
            pcmChunk = dequeueNextPendingPcmChunk();
        }
        handleQueueDrainedOnWorker();
    }

    /**
     * 从待播队列中取出下一段 PCM 数据，并同步更新队列相关调试状态。
     *
     * <p>工作线程是队列的唯一消费者。将取队列逻辑单独抽出后，主循环可以直接表达为“持续取数直到
     * 队列为空”，避免使用难以审计的无限循环加中途返回。
     *
     * @return 下一段 PCM 数据；当队列已清空时返回 {@code null}
     */
    private byte[] dequeueNextPendingPcmChunk() {
        synchronized (queueLock) {
            byte[] pcmChunk = pendingPcmQueue.pollFirst();
            if (pcmChunk == null) {
                updatePlayerInfoWithQueueStateLocked();
                return null;
            }
            queuedBytes -= pcmChunk.length;
            updatePlayerInfoWithQueueStateLocked();
            return pcmChunk;
        }
    }

    /**
     * 在队列被完全消费后收口本轮工作线程的排空流程。
     *
     * <p>如果上游已经调用过 {@link #completePcmStream()}，则队列清空意味着本次会话可以进入
     * 完成态；否则播放器继续停留在已准备或等待新输入的状态。
     */
    private void handleQueueDrainedOnWorker() {
        if (inputCompleted) {
            handleStreamCompletedOnWorker();
        }
    }

    private void writeFully(byte[] audioBytes) {
        int offset = 0;
        while (offset < audioBytes.length) {
            int written = audioTrack.write(audioBytes, offset, audioBytes.length - offset);
            if (written <= 0) {
                throw new IllegalStateException("AudioTrack.write 返回异常值: " + written);
            }
            offset += written;
        }
    }

    private void handleStreamCompletedOnWorker() {
        if (streamState == StreamState.COMPLETED || streamState == StreamState.CANCELED || streamState == StreamState.RELEASED) {
            return;
        }
        try {
            if (audioTrack != null) {
                audioTrack.pause();
                audioTrack.flush();
            }
        } catch (Exception e) {
            ExoLog.log("PCM 流结束时刷新 AudioTrack 失败", e);
        }
        // complete 表示“不再接收新输入，但要把已排队数据正常播完”，因此这里只让底层
        // 设备回到完成态，不主动抹掉本次流会话的统计信息和模式信息。
        streamState = StreamState.COMPLETED;
        dispatchPlaybackState(ExoPlaybackState.STATE_ENDED);
        dispatchProgress();
    }

    private void cancelStreamOnWorker() {
        // cancel 是硬中断语义：立即丢弃尚未播放的 PCM 数据，适用于 TTS 取消、页面切换
        // 或销毁场景。它与 complete 的“播完剩余缓存再结束”语义刻意区分。
        cancelStreamResourcesOnWorker();
        streamState = StreamState.CANCELED;
        playerInfo.setQueuedPcmDurationMs(0L);
        dispatchPlaybackState(ExoPlaybackState.STATE_IDLE);
        dispatchProgress();
    }

    private void cancelStreamResourcesOnWorker() {
        synchronized (queueLock) {
            pendingPcmQueue.clear();
            queuedBytes = 0L;
            drainScheduled = false;
            inputCompleted = false;
            updatePlayerInfoWithQueueStateLocked();
        }
        if (audioTrack != null) {
            try {
                audioTrack.pause();
            } catch (Exception ignored) {
            }
            try {
                audioTrack.flush();
            } catch (Exception ignored) {
            }
            try {
                audioTrack.release();
            } catch (Exception ignored) {
            }
            audioTrack = null;
        }
        if (audioProcessorChain != null) {
            audioProcessorChain.release();
            audioProcessorChain = null;
        }
    }

    private void releaseInternalOnWorker() {
        // release 比 cancel 更进一步：不仅终止当前会话，还会把核心推进到不可复用终态，
        // 防止已经销毁的 View 继续向正在退出的工作线程投递任务。
        cancelStreamResourcesOnWorker();
        streamState = StreamState.RELEASED;
    }

    private void updatePlayerInfoWithQueueStateLocked() {
        playerInfo.setQueuedPcmDurationMs(bytesToDurationMs(queuedBytes));
        mainHandler.post(() -> iExoNotifyCallBack.onPlayerInfoChanged(playerInfo));
    }

    private void dispatchPlaybackState(int playbackState) {
        String playbackStateName = getPlaybackStateName(playbackState);
        playerInfo.setPlaybackState(playbackState);
        playerInfo.setPlaybackStateName(playbackStateName);
        playerInfo.setQueuedPcmDurationMs(getQueuedPcmDurationMs());
        mainHandler.post(() -> {
            iExoNotifyCallBack.onPlaybackStateChanged(playbackState, playbackStateName);
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        });
    }

    private void dispatchPlayerState(int playerState) {
        String playerStateName = playerState == ExoPlayerMode.PLAYER_FULL_SCREEN ? "全屏播放" : "普通播放";
        playerInfo.setPlayerState(playerState);
        playerInfo.setPlayerStateName(playerStateName);
        mainHandler.post(() -> {
            iExoNotifyCallBack.onPlayerStateChanged(playerState, playerStateName, playerView);
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        });
    }

    private void dispatchProgress() {
        long currentPositionMs = getCurrentPosition();
        long durationMs = inputCompleted ? getDuration() : 0L;
        long bufferedPositionMs = currentPositionMs + getQueuedPcmDurationMs();
        int bufferedPercent = durationMs > 0
                ? (int) Math.min(100L, bufferedPositionMs * 100L / Math.max(1L, durationMs))
                : 0;
        mainHandler.post(() -> {
            iExoNotifyCallBack.onPlayingProgressPositionChanged(
                    currentPositionMs,
                    durationMs,
                    bufferedPositionMs,
                    bufferedPercent
            );
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        });
    }

    private void dispatchError(String errorMsg, Throwable throwable) {
        mainHandler.post(() -> iExoNotifyCallBack.onPlayerError(errorMsg, throwable));
    }

    private void validateConfig(ExoPcmStreamConfig config) {
        if (config.getSampleRateHz() <= 0) {
            throw new IllegalArgumentException("PCM sampleRateHz 必须大于 0");
        }
        if (config.getChannelCount() != 1 && config.getChannelCount() != 2) {
            throw new IllegalArgumentException("PCM channelCount 仅支持单声道或双声道");
        }
        if (config.getEncoding() != AudioFormat.ENCODING_PCM_16BIT) {
            throw new IllegalArgumentException("当前版本仅支持 PCM_16BIT");
        }
        if (config.getMaxQueuedDurationMs() < 0) {
            throw new IllegalArgumentException("maxQueuedDurationMs 不能小于 0");
        }
    }

    private int getChannelMask(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            default:
                throw new IllegalArgumentException("仅支持 1 或 2 声道 PCM");
        }
    }

    private long durationMsToBytes(long durationMs) {
        if (currentConfig == null) {
            return 0L;
        }
        return (durationMs * currentConfig.getSampleRateHz() * currentConfig.getBytesPerFrame()) / 1000L;
    }

    private long bytesToDurationMs(long bytes) {
        if (currentConfig == null || currentConfig.getSampleRateHz() <= 0) {
            return 0L;
        }
        long bytesPerSecond = (long) currentConfig.getSampleRateHz() * currentConfig.getBytesPerFrame();
        if (bytesPerSecond <= 0) {
            return 0L;
        }
        return bytes * 1000L / bytesPerSecond;
    }

    private String getPlaybackStateName(int playbackState) {
        switch (playbackState) {
            case ExoPlaybackState.STATE_IDLE:
                return "空闲";
            case ExoPlaybackState.STATE_BUFFERING:
                return "缓冲中";
            case ExoPlaybackState.STATE_READY:
                return "就绪";
            case ExoPlaybackState.STATE_ENDED:
                return "结束";
            case ExoPlaybackState.STATE_PLAYING:
                return "播放中";
            case ExoPlaybackState.STATE_PLAY_PAUSE:
                return "暂停中";
            default:
                return "未知";
        }
    }

    private void scheduleDrainLocked() {
        if (streamState == StreamState.PAUSED || streamState == StreamState.RELEASED) {
            return;
        }
        if (!drainScheduled) {
            // 只允许一个 drain 任务在 worker 上串行运行，保证 AudioTrack 写入顺序稳定，
            // 也避免多个 append 调用并发提交同一队列导致重复消费。
            drainScheduled = true;
            workerHandler.post(drainRunnable);
        }
    }

    private void ensureUsable() {
        if (streamState == StreamState.RELEASED) {
            throw new IllegalStateException("PCM 流核心已经释放，不能继续使用");
        }
    }

    private void runOnWorkerBlocking(@NonNull Runnable action) {
        if (Looper.myLooper() == workerThread.getLooper()) {
            action.run();
            return;
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<RuntimeException> runtimeExceptionReference = new AtomicReference<>();
        workerHandler.post(() -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                runtimeExceptionReference.set(e);
            } catch (Exception e) {
                runtimeExceptionReference.set(new RuntimeException(e));
            } finally {
                countDownLatch.countDown();
            }
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待 PCM 工作线程执行完成时被中断", e);
        }
        RuntimeException runtimeException = runtimeExceptionReference.get();
        if (runtimeException != null) {
            throw runtimeException;
        }
    }
}
