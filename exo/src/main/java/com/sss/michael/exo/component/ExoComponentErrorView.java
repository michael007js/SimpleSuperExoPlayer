package com.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.R;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.databinding.LayoutComponentExoErrorViewBinding;

/**
 * @author Michael by SSS
 * @date 2025/12/28 0028 16:00
 * @Description 播放过程中出错视图
 */
public class ExoComponentErrorView extends BaseExoControlComponent<LayoutComponentExoErrorViewBinding> {
    public ExoComponentErrorView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentErrorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentErrorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_component_exo_error_view;
    }

    @Override
    protected void init(Context context) {
        setVisibility(GONE);
        binding.statusBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.refresh();
            }
        });
    }

    @Override
    public void onPlayerError(String errorMsg, Throwable throwable) {
        super.onPlayerError(errorMsg, throwable);
        exoControllerWrapper.stop();
        setVisibility(VISIBLE);
        binding.message.setText(errorMsg);
        bringToFront();
    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_BUFFERING ||
                playbackState == ExoPlaybackState.STATE_PLAYING ||
                playbackState == ExoPlaybackState.STATE_PLAY_PAUSE)
            setVisibility(GONE);
    }

    @Override
    public void onProgressChange(long current, long total, long seekTo) {

    }

    @Override
    public void onVolumeChange(int current, int max) {

    }

    @Override
    public void onBrightnessChange(float percent) {

    }

    @Override
    public void onExoRenderedFirstFrame() {

    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {

    }

    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {

    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPosition, int bufferedPercentage) {

    }

    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {

    }

    @Override
    public void onExperienceTimeout() {

    }


    @Override
    public void setPlayLocationLastTime(String tip) {

    }
}
