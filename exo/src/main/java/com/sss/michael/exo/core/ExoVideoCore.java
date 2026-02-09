package com.sss.michael.exo.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.ffmpeg.FfmpegLibrary;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.callback.IExoController;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.callback.IExoLifecycle;
import com.sss.michael.exo.callback.IExoNotifyCallBack;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.factory.ExoLoadControlFactory;
import com.sss.michael.exo.helper.ExoMonitorManager;
import com.sss.michael.exo.processor.ExoBaseAudioProcessor;
import com.sss.michael.exo.processor.ExoEqualizerProcessor;
import com.sss.michael.exo.processor.ExoSpectrumProcessor;
import com.sss.michael.exo.util.ExoLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by 61642
 * @date 2025/12/26 17:42
 * @Description exo核心
 */
public abstract class ExoVideoCore implements IExoController, IExoLifecycle {
    // 监控任务管理器
    protected ExoMonitorManager exoMonitorManager;
    // 播放器实时信息
    protected ExoPlayerInfo playerInfo = new ExoPlayerInfo();
    // 上下文
    protected Context mContext;
    // 内核
    protected ExoPlayer player;
    // 试看时间
    protected long experienceTimeMs;
    // 用于延时执行重试的任务
    protected Runnable retryRunnable;
    // 音频处理器列表
    protected List<ExoBaseAudioProcessor> audioProcessors = new ArrayList<>();
    // EQ处理器
    protected ExoEqualizerProcessor equalizerProcessor;
    protected Handler mainHandler = new Handler(Looper.getMainLooper());
    protected IExoNotifyCallBack iExoNotifyCallBack;
    // <editor-fold defaultstate="collapsed" desc="初始化构建">

    @OptIn(markerClass = UnstableApi.class)
    public ExoVideoCore(Context context, IExoNotifyCallBack iExoNotifyCallBack, IExoFFTCallBack IExoFFTCallBack) {
        this.mContext = context;
        this.iExoNotifyCallBack = iExoNotifyCallBack;
        if (ExoConfig.COMPONENT_EQ_ENABLE && equalizerProcessor == null) {
            equalizerProcessor = new ExoEqualizerProcessor();
            equalizerProcessor.setBandGains(ExoEqualizerPreset.CUSTOM.getGains());
            audioProcessors.add(equalizerProcessor);
        }
        if (ExoConfig.COMPONENT_SPECTRUM_ENABLE) {
            ExoSpectrumProcessor spectrumProcessor = new ExoSpectrumProcessor();

            spectrumProcessor.setExoFFTCallBack(new IExoFFTCallBack() {
                @Override
                public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
                    mainHandler.post(() -> {
                        if (IExoFFTCallBack != null) {
                            IExoFFTCallBack.onFFTReady(sampleRateHz, channelCount, fft);
                        }
                    });
                }

                @Override
                public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
                    mainHandler.post(() -> {
                        if (IExoFFTCallBack != null) {
                            IExoFFTCallBack.onMagnitudeReady(sampleRateHz, magnitude);
                        }
                    });
                }
            });
            audioProcessors.add(spectrumProcessor);
        }
        ExoLog.log("FFmpeg 扩展是否可用: " + FfmpegLibrary.isAvailable());
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(mContext) {
            @Override
            protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback, AudioSink audioSink, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
                // 保持原有的 AudioSink 逻辑，但注入自定义的 Processors
                AudioProcessor[] processors = audioProcessors.toArray(new AudioProcessor[0]);
                AudioSink customSink = new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(processors)
                        .build();

                super.buildAudioRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        customSink,
                        eventHandler,
                        eventListener,
                        out);
            }
        }
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(mContext);
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
                        .setRendererDisabled(C.TRACK_TYPE_AUDIO, false)
        );
        player = new ExoPlayer.Builder(mContext, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(ExoLoadControlFactory.getLoadControlByPlayMode(ExoPlayMode.VOD))
                .setHandleAudioBecomingNoisy(true)
                .build();

        exoMonitorManager = new ExoMonitorManager(context);
        exoMonitorManager.setMainThreadCallback(new ExoMonitorManager.MainThreadCallback() {
            @Override
            public void onMonitorResult(boolean success, long costTime, @Nullable Object extra) {
//                ExoLog.log("【实时网速】" + playerInfo.getCurrentSpeedText());
                if (iExoNotifyCallBack != null) {
                    iExoNotifyCallBack.onNetworkBytesChanged(playerInfo.getBytesInLastSecond(), playerInfo.getTotalBytes());
                    iExoNotifyCallBack.onPlayingProgressPositionChanged(player.getCurrentPosition(), player.getDuration(), player.getBufferedPosition(), player.getBufferedPercentage());
                    iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
                    if (experienceTimeMs > 0 && player.getCurrentPosition() >= experienceTimeMs) {
                        seekTo(experienceTimeMs);
                        if (ExoPlaybackState.STATE_PLAYING == playerInfo.getPlaybackState() || ExoPlaybackState.STATE_BUFFERING == playerInfo.getPlaybackState()) {
                            ExoLog.log("体验时间【" + experienceTimeMs + "】已到，即将强制停止播放");
                            stop();
                            iExoNotifyCallBack.onExperienceTimeout();
                        }
                    }
                }
                // 清零“秒统计”，开始统计下一秒
                playerInfo.setBytesInLastSecond(0);
            }
        });
        exoMonitorManager.startMonitor(new Runnable() {
            @Override
            public void run() {

            }
        });

    }
    // </editor-fold>
}
