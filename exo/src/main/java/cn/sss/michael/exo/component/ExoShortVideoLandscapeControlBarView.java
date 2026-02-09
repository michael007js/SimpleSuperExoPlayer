package cn.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.R;
import cn.sss.michael.exo.databinding.LayoutExoComponentShortVideoLandscapeControlViewBinding;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.util.ExoFormatUtil;
import cn.sss.michael.exo.widget.ExoSeekBar;

/**
 * @author Michael by 61642
 * @date 2026/2/5 15:47
 * @Description 短视频横屏控制栏
 */
public class ExoShortVideoLandscapeControlBarView extends BaseExoControlComponent<LayoutExoComponentShortVideoLandscapeControlViewBinding> {
    private boolean mIsDragging;

    public ExoShortVideoLandscapeControlBarView(@NonNull Context context) {
        super(context);
    }

    public ExoShortVideoLandscapeControlBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoShortVideoLandscapeControlBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return ExoPlayMode.SHORT_VIDEO == lastExoPlayMode;
    }

    @Override
    protected View[] includeResIdWhileShowWhileFingerTouched() {
        return new View[]{
                binding.bottomContainer
        };

    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_short_video_landscape_control_view;
    }

    @Override
    protected void init(Context context) {
        binding.seekBar.setDouyinStyle(true);
        binding.bottomProgress.setDouyinStyle(true);
        binding.debug.setVisibility(ExoConfig.COMPONENT_DEBUG_ENABLE ? VISIBLE : GONE);
        binding.eq.setVisibility(ExoConfig.COMPONENT_EQ_ENABLE ? VISIBLE : GONE);
        binding.bottomProgress.setVisibility(GONE);
        binding.speed.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ExoComponentSpeedView exoComponentSpeedView = getExoControlComponentByClass(ExoComponentSpeedView.class);
                if (exoComponentSpeedView != null) {
                    exoComponentSpeedView.openSpeedMenu();
                }
            }
        });
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
        binding.seekBar.setOnSeekBarChangeListener(new ExoSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(ExoSeekBar seekBar, long progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                long duration = exoControllerWrapper.getDuration();
                long newPosition = (duration * progress) / binding.seekBar.getMax();
                binding.currTime.setText(ExoFormatUtil.stringForTime((int) newPosition));
            }

            @Override
            public void onStartTrackingTouch(ExoSeekBar seekBar) {
                mIsDragging = true;
            }

            @Override
            public void onStopTrackingTouch(ExoSeekBar seekBar) {
                long duration = exoControllerWrapper.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / binding.seekBar.getMax();
                exoControllerWrapper.seekTo((int) newPosition);
                mIsDragging = false;
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
    protected void onViewFadeInOutComplete(boolean fadeIn) {
        super.onViewFadeInOutComplete(fadeIn);
        if (exoControllerWrapper.isFullScreen()) {
            binding.bottomProgress.setVisibility(fadeIn ? GONE : VISIBLE);
        } else {
            binding.bottomProgress.setVisibility(GONE);
        }
    }

    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {

    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPosition, int bufferedPercentage) {
        if (mIsDragging) {
            return;
        }

        if (durationMs > 0) {
            binding.seekBar.setMax(durationMs);
            binding.seekBar.setEnabled(true);
            int pos = (int) (currentMs * 1.0 / durationMs * binding.seekBar.getMax());
            binding.seekBar.setProgress(pos);
            binding.bottomProgress.setProgress(pos);
        } else {
            binding.seekBar.setEnabled(false);
        }

        binding.seekBar.setBufferedProgress(bufferedPosition);
        binding.bottomProgress.setBufferedProgress(bufferedPosition);


        binding.currTime.setText(ExoFormatUtil.stringForTime(currentMs));
        binding.totalTime.setText(ExoFormatUtil.stringForTime(durationMs));
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
        binding.tvPlayLocationLastTime.setVisibility(VISIBLE);
        binding.tvPlayLocationLastTime.setText(tip);
        binding.tvPlayLocationLastTime.postDelayed(new Runnable() {
            @Override
            public void run() {
                binding.tvPlayLocationLastTime.animate().cancel();
                if (binding.tvPlayLocationLastTime.getAlpha() == 1f)
                    binding.tvPlayLocationLastTime.setAlpha(0f);

                binding.tvPlayLocationLastTime.animate()
                        .alpha(0f)
                        .setDuration(ANIM_DURATION)
                        .setListener(null)
                        .start();
            }
        }, 2000);
    }
}
