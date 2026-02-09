package com.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.R;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.constant.ExoPlayerMode;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.databinding.LayoutExoComponentCompleteViewBinding;

/**
 * @author Michael by SSS
 * @date 2025/12/28 0028 18:25
 * @Description 播放完视图
 */
public class ExoComponentCompleteView extends BaseExoControlComponent<LayoutExoComponentCompleteViewBinding> {
    public ExoComponentCompleteView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentCompleteView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentCompleteView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_complete_view;
    }

    @Override
    protected void init(Context context) {
        setVisibility(GONE);
        binding.stopFullscreen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.stopFullScreen(true);
            }
        });
        binding.ivReplay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.rePlay();
            }
        });
    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_ENDED) {
            setVisibility(VISIBLE);
            binding.stopFullscreen.setVisibility(exoControllerWrapper.isFullScreen() ? VISIBLE : GONE);
            bringToFront();
        } else {
            setVisibility(GONE);
        }

    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        super.onPlayerStateChanged(playerState, playerStateName, playerView);
        if (ExoPlayerMode.PLAYER_FULL_SCREEN == playerState) {
            binding.stopFullscreen.setVisibility(VISIBLE);
        } else {
            binding.stopFullscreen.setVisibility(GONE);
        }
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
