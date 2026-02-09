package cn.sss.michael.exo.callback;

import android.view.View;

import androidx.annotation.NonNull;

import cn.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import cn.sss.michael.exo.constant.ExoEqualizerPreset;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by SSS
 * @date 2025/12/25 0025 21:33
 * @Description 此类的目的是为了在 IExoControlComponent 中既能调用 IExoController 的api又能调用 IExoControlComponent 的api，
 */
public class ExoControllerWrapper implements IExoController, IExoNotifyCallBack {
    private final IExoController iExoController;
    private final IExoNotifyCallBack iExoNotifyCallBack;


    public ExoControllerWrapper(IExoController iExoController, IExoNotifyCallBack iExoNotifyCallBack) {
        this.iExoController = iExoController;
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


    ////////////////////////////// 快捷函数 /////////////////////////////

    /**
     * 切换播放暂停
     */
    public void togglePlayPause() {
        if (isPlaying()) {
            pause(true);
        } else {
            resume();
        }
    }

    /**
     * 切换播放暂停
     */
    public void toggleFullScreen() {
        if (isFullScreen()) {
            stopFullScreen(true);
        } else {
            startFullScreen(true);
        }
    }

}
