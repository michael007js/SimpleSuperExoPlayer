package cn.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutExoComponentLiveControlBarViewBinding;

/**
 * @author Michael by 61642
 * @date 2025/12/26 10:13
 * @Description 直播控制栏
 */
public class ExoLiveControlBarView extends BaseExoControlComponent<LayoutExoComponentLiveControlBarViewBinding> {
    public ExoLiveControlBarView(@NonNull Context context) {
        super(context);
    }

    public ExoLiveControlBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoLiveControlBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return ExoPlayMode.LIVE == lastExoPlayMode;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_live_control_bar_view;
    }

    @Override
    protected void init(Context context) {
        binding.debug.setVisibility(ExoConfig.COMPONENT_DEBUG_ENABLE ? VISIBLE : GONE);
        binding.eq.setVisibility(ExoConfig.COMPONENT_EQ_ENABLE ? VISIBLE : GONE);
        binding.ivPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.togglePlayPause();
            }
        });
        binding.ivFullScreen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.toggleFullScreen();
            }
        });
        binding.ivRefresh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.refresh();
            }
        });

        binding.eq.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ExoComponentEqView debugControlView = getExoControlComponentByClass(ExoComponentEqView.class);
                if (debugControlView != null) {
                    debugControlView.setVisibility(VISIBLE);
                }
            }
        });
        binding.debug.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ExoComponentDebugControlView debugControlView = getExoControlComponentByClass(ExoComponentDebugControlView.class);
                if (debugControlView != null) {
                    debugControlView.setVisibility(ExoConfig.COMPONENT_DEBUG_ENABLE ? VISIBLE : GONE);
                }
                ExoComponentSpectrumView spectrumView = getExoControlComponentByClass(ExoComponentSpectrumView.class);
                if (spectrumView != null) {
                    spectrumView.setVisibility(ExoConfig.COMPONENT_SPECTRUM_ENABLE ? VISIBLE : GONE);
                }
            }
        });
    }

    private ExoPlayMode lastExoPlayMode;

    @Override
    public void onExoRenderedFirstFrame() {

    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {
        if (lastExoPlayMode != exoPlayerInfo.getExoPlayMode()) {
            lastExoPlayMode = exoPlayerInfo.getExoPlayMode();
        }
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
        if (playbackState == ExoPlaybackState.STATE_PLAYING) {
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_pause);
        } else {
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_play);
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        super.onPlayerStateChanged(playerState, playerStateName, playerView);
        if (ExoPlayerMode.PLAYER_FULL_SCREEN == playerState) {
            binding.ivFullScreen.setImageResource(R.drawable.exo_ic_action_fullscreen_exit);
        } else {
            binding.ivFullScreen.setImageResource(R.drawable.exo_ic_action_fullscreen);

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
