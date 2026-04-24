package com.sss.michael.exo.callback;

import android.view.View;

import androidx.annotation.NonNull;

import com.sss.michael.exo.bean.ExoPcmStreamConfig;
import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.core.ExoPlayerInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放控制包装器。
 *
 * <p>该类为控制组件提供统一入口。组件侧只依赖一个包装器实例，包装器内部则同时代理传统
 * {@link IExoController} 能力和新增的 {@link IExoPcmStreamController} 能力，从而在不破坏
 * 既有组件接口的前提下支持 PCM 流式播放。
 */
public class ExoControllerWrapper implements IExoController, IExoPcmStreamController, IExoNotifyCallBack {
    private final IExoController iExoController;
    private final IExoPcmStreamController iExoPcmStreamController;
    private final IExoNotifyCallBack iExoNotifyCallBack;


    public ExoControllerWrapper(IExoController iExoController, IExoNotifyCallBack iExoNotifyCallBack) {
        this.iExoController = iExoController;
        this.iExoPcmStreamController = iExoController instanceof IExoPcmStreamController
                ? (IExoPcmStreamController) iExoController
                : null;
        this.iExoNotifyCallBack = iExoNotifyCallBack;
    }

    @Override
    public void setEqualizer(@NonNull ExoEqualizerPreset exoEqualizerPreset) {
        if (iExoController != null) {
            iExoController.setEqualizer(exoEqualizerPreset);
        }
    }

    @Override
    public void rePlay() {
        if (iExoController != null) {
            iExoController.rePlay();
        }
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        if (iExoController != null) {
            iExoController.setPlayWhenReady(playWhenReady);
        }
    }

    @Override
    public void play(ExoPlayMode mode, long lastPlayTime, String url) {
        if (iExoController != null) {
            iExoController.play(mode, lastPlayTime, url);
        }
    }

    @Override
    public void reset() {
        if (iExoController != null) {
            iExoController.reset();
        }
    }

    @Override
    public void refresh() {
        if (iExoController != null) {
            iExoController.refresh();
        }
    }

    @Override
    public void pause(boolean callFromActive) {
        if (iExoController != null) {
            iExoController.pause(callFromActive);
        }
    }

    @Override
    public void resume() {
        if (iExoController != null) {
            iExoController.resume();
        }
    }

    @Override
    public void stop() {
        if (iExoController != null) {
            iExoController.stop();
        }
    }

    @Override
    public boolean isPlaying() {
        return iExoController != null && iExoController.isPlaying();
    }

    @Override
    public long getDuration() {
        return iExoController == null ? 0 : iExoController.getDuration();
    }

    @Override
    public void seekTo(long positionMs) {
        if (iExoController != null) {
            iExoController.seekTo(positionMs);
        }
    }

    @Override
    public long getCurrentPosition() {
        return iExoController == null ? 0 : iExoController.getCurrentPosition();
    }

    @Override
    public void setScaleMode(int mode) {
        if (iExoController != null) {
            iExoController.setScaleMode(mode);
        }
    }

    @Override
    public int getScaleMode() {
        return iExoController == null ? 0 : iExoController.getScaleMode();
    }

    @Override
    public void setSpeed(float speed) {
        if (iExoController != null) {
            iExoController.setSpeed(speed);
        }
    }

    @Override
    public float getSpeed() {
        return iExoController == null ? 0 : iExoController.getSpeed();
    }

    @Override
    public void startFullScreen(boolean callFromActive) {
        if (iExoController != null) {
            iExoController.startFullScreen(callFromActive);
        }
    }

    @Override
    public void stopFullScreen(boolean callFromActive) {
        if (iExoController != null) {
            iExoController.stopFullScreen(callFromActive);
        }
    }

    @Override
    public boolean isFullScreen() {
        return iExoController != null && iExoController.isFullScreen();
    }

    @Override
    public ExoPlayerInfo getExoPlayerInfo() {
        return iExoController == null ? new ExoPlayerInfo() : iExoController.getExoPlayerInfo();
    }

    @Override
    public void setExperienceTime(long experienceTimeMs) {
        if (iExoController != null) {
            iExoController.setExperienceTime(experienceTimeMs);
        }
    }

    @Override
    public void startPcmStream(ExoPcmStreamConfig config) {
        if (iExoPcmStreamController != null) {
            iExoPcmStreamController.startPcmStream(config);
        }
    }

    @Override
    public void appendPcmData(ByteBuffer buffer) {
        if (iExoPcmStreamController != null) {
            iExoPcmStreamController.appendPcmData(buffer);
        }
    }

    @Override
    public void appendPcmData(byte[] data, int offset, int length) {
        if (iExoPcmStreamController != null) {
            iExoPcmStreamController.appendPcmData(data, offset, length);
        }
    }

    @Override
    public void completePcmStream() {
        if (iExoPcmStreamController != null) {
            iExoPcmStreamController.completePcmStream();
        }
    }

    @Override
    public void cancelPcmStream() {
        if (iExoPcmStreamController != null) {
            iExoPcmStreamController.cancelPcmStream();
        }
    }

    @Override
    public boolean isPcmStreaming() {
        return iExoPcmStreamController != null && iExoPcmStreamController.isPcmStreaming();
    }

    @Override
    public long getQueuedPcmDurationMs() {
        return iExoPcmStreamController == null ? 0 : iExoPcmStreamController.getQueuedPcmDurationMs();
    }


    @Override
    public List<IExoControlComponent> getExoComponents() {
        return iExoNotifyCallBack == null ? new ArrayList<>() : iExoNotifyCallBack.getExoComponents();
    }

    @Override
    public <T extends IExoControlComponent> T getExoControlComponentByClass(Class<T> cls) {
        return iExoNotifyCallBack == null ? null : iExoNotifyCallBack.getExoControlComponentByClass(cls);
    }

    @Override
    public void onExoRenderedFirstFrame() {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onExoRenderedFirstFrame();
        }
    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(exoPlayerInfo);
        }
    }

    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onNetworkBytesChanged(bytesInLastSecond, totalBytes);
        }
    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayingProgressPositionChanged(currentMs, durationMs, bufferedPositionMs, bufferedPercentage);
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlaybackStateChanged(playbackState, playbackStateName);
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerStateChanged(playerState, playerStateName, playerView);
        }
    }

    @Override
    public void onPlayerError(String errorMsg, Throwable throwable) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerError(errorMsg, throwable);
        }
    }

    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onVideoSizeChanged(view, pixelWidthHeightRatio, videoWidth, videoHeight, scaleMode);
        }
    }

    @Override
    public void onExperienceTimeout() {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onExperienceTimeout();
        }
    }

    @Override
    public void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView) {
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onShortVideoComponentChangedAction(clearScreenMode, exoShortVideoSimpleControlBarView);
        }
    }


    ////////////////////////////// 便捷函数 /////////////////////////////

    /**
     * 在播放与暂停之间切换。
     */
    public void togglePlayPause() {
        if (isPlaying()) {
            pause(true);
        } else {
            resume();
        }
    }

    /**
     * 在全屏与非全屏之间切换。
     */
    public void toggleFullScreen() {
        if (isFullScreen()) {
            stopFullScreen(true);
        } else {
            startFullScreen(true);
        }
    }

}
