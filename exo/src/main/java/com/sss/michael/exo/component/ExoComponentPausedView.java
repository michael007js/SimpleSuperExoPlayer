package com.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.R;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.databinding.LayoutExoComponentPausedViewBinding;

/**
 * @author Michael by 61642
 * @date 2025/12/29 17:21
 * @Description 已暂停视图
 */
public class ExoComponentPausedView extends BaseExoControlComponent<LayoutExoComponentPausedViewBinding> {
    public ExoComponentPausedView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentPausedView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentPausedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_paused_view;
    }

    @Override
    protected void init(Context context) {
        binding.ivPaused.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.togglePlayPause();
            }
        });

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
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_PLAY_PAUSE) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
    }

    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {

    }

    @Override
    public void onExperienceTimeout() {

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
    public void setPlayLocationLastTime(String tip) {

    }
}
