package cn.sss.michael.exo.component;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.bean.ExoSpeedBean;
import cn.sss.michael.exo.component.adapter.ExoSpeedAdapter;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutExoComponentSpeedViewBinding;
import cn.sss.michael.exo.util.ExoDensityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by 61642
 * @date 2026/1/5 16:47
 * @Description 倍速播放
 */
public class ExoComponentSpeedView extends BaseExoControlComponent<LayoutExoComponentSpeedViewBinding> {
    private ValueAnimator openAnimator;
    private ValueAnimator closeAnimator;
    private ExoSpeedAdapter exoSpeedAdapter;
    private int speedWidth;

    public ExoComponentSpeedView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentSpeedView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentSpeedView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_speed_view;
    }

    @Override
    protected void init(Context context) {
        binding.linSpeed.setVisibility(INVISIBLE);
        speedWidth = ExoDensityUtil.dp2px(context, 320);
        binding.speedRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        binding.speedRecyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void closeSpeedMenu() {
        if (closeAnimator != null) {
            closeAnimator.cancel();
            closeAnimator.removeAllListeners();
            closeAnimator.removeAllUpdateListeners();
        }
        // 从0 → -speedWidth（隐藏到右侧）
        closeAnimator = ValueAnimator.ofInt(0, -speedWidth);
        closeAnimator.setDuration(200);
        closeAnimator.setInterpolator(new AccelerateInterpolator());
        closeAnimator.addUpdateListener(animation -> {
            int marginRight = (int) animation.getAnimatedValue();
            LayoutParams params = (LayoutParams) binding.linSpeed.getLayoutParams();
            params.rightMargin = marginRight;
            binding.linSpeed.setLayoutParams(params);
        });
        closeAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                binding.linSpeed.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                binding.linSpeed.setVisibility(INVISIBLE);
                LayoutParams params = (LayoutParams) binding.linSpeed.getLayoutParams();
                params.rightMargin = -speedWidth;
                binding.linSpeed.setLayoutParams(params);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        closeAnimator.start();
    }

    public void openSpeedMenu() {
        if (exoSpeedAdapter == null) {
            List<ExoSpeedBean> exoSpeedBeanList = new ArrayList<>();
            exoSpeedBeanList.add(new ExoSpeedBean(0.25f, "0.25X", false));
            exoSpeedBeanList.add(new ExoSpeedBean(0.5f, "0.5X", false));
            exoSpeedBeanList.add(new ExoSpeedBean(0.75f, "0.75X", false));
            exoSpeedBeanList.add(new ExoSpeedBean(1.0f, "正常", false));
            exoSpeedBeanList.add(new ExoSpeedBean(1.25f, "1.25X", false));
            exoSpeedBeanList.add(new ExoSpeedBean(1.5f, "1.5X", false));
            exoSpeedBeanList.add(new ExoSpeedBean(2.0f, "2.0X", false));
            for (ExoSpeedBean exoSpeedBean : exoSpeedBeanList) {
                exoSpeedBean.checked = exoControllerWrapper.getSpeed() == exoSpeedBean.speed;
            }
            exoSpeedAdapter = new ExoSpeedAdapter(getContext(), exoSpeedBeanList);
            exoSpeedAdapter.setOnSpeedAdapterCallBack((speenBean, position) -> {
                exoSpeedAdapter.setChecked(position);
                exoControllerWrapper.setSpeed(speenBean.speed);
            });
            binding.speedRecyclerView.setAdapter(exoSpeedAdapter);
        }

        binding.linSpeed.setVisibility(VISIBLE);
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator.removeAllListeners();
            openAnimator.removeAllUpdateListeners();
        }
        openAnimator = ValueAnimator.ofInt(-speedWidth, 0);
        openAnimator.setDuration(200);
        openAnimator.setInterpolator(new AccelerateInterpolator());
        openAnimator.addUpdateListener(animation -> {
            int marginRight = (int) animation.getAnimatedValue();
            LayoutParams params = (LayoutParams) binding.linSpeed.getLayoutParams();
            params.rightMargin = marginRight;
            binding.linSpeed.setLayoutParams(params);
            binding.linSpeed.requestLayout();
            binding.speedRecyclerView.requestLayout();
        });
        openAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                binding.linSpeed.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                int[] location = new int[2];
                binding.linSpeed.getLocationOnScreen(location);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        openAnimator.start();
    }

    long lastTouchTime;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        long time = System.currentTimeMillis();
        if (binding.linSpeed.getVisibility() == VISIBLE && time - lastTouchTime > 1000) {
            closeSpeedMenu();
            lastTouchTime = time;
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        super.onPlayerStateChanged(playerState, playerStateName, playerView);
        if (ExoPlayerMode.PLAYER_NORMAL == playerState) {
            binding.linSpeed.setVisibility(INVISIBLE);
            LayoutParams layoutParams = (LayoutParams) binding.linSpeed.getLayoutParams();
            layoutParams.rightMargin = -speedWidth;
            binding.linSpeed.setLayoutParams(layoutParams);
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_BUFFERING ||
                playbackState == ExoPlaybackState.STATE_PLAYING ||
                playbackState == ExoPlaybackState.STATE_PLAY_PAUSE)
            binding.linSpeed.setVisibility(INVISIBLE);
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (exoSpeedAdapter != null) {
            exoSpeedAdapter.clear();
        }
        exoSpeedAdapter = null;
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator.removeAllListeners();
            openAnimator.removeAllUpdateListeners();
        }
        openAnimator = null;

        if (closeAnimator != null) {
            closeAnimator.cancel();
            closeAnimator.removeAllListeners();
            closeAnimator.removeAllUpdateListeners();
        }
        closeAnimator = null;
    }
}