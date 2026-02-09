package com.sss.michael.exo.callback;

import android.view.View;

import com.sss.michael.exo.core.ExoPlayerInfo;

import java.util.List;

/**
 * @author Michael by 61642
 * @date 2025/12/30 15:34
 * @Description 简化版播放器通知回调
 */
public abstract class SimpleExoNotifyCallBack implements IExoNotifyCallBack {
    @Override
    public List<IExoControlComponent> getExoComponents() {
        return null;
    }

    @Override
    public <T extends IExoControlComponent> T getExoControlComponentByClass(Class<T> cls) {
        return null;
    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {

    }

    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {

    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage) {

    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {

    }

    @Override
    public void onPlayerError(String errorMsg, Throwable throwable) {

    }

    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {

    }

    @Override
    public void onExperienceTimeout() {

    }
}
