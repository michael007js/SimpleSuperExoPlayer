package cn.sss.michael.exo.component;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutExoComponentShortVideoSimpleControlViewBinding;
import cn.sss.michael.exo.util.ExoDensityUtil;
import cn.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by 61642
 * @date 2026/1/29 19:22
 * @Description （类抖音）下拉控制栏
 */
public class ExoShortVideoSimpleControlBarView extends BaseExoControlComponent<LayoutExoComponentShortVideoSimpleControlViewBinding> {
    boolean playComplete;

    public ExoShortVideoSimpleControlBarView(@NonNull Context context) {
        super(context);
    }

    public ExoShortVideoSimpleControlBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoShortVideoSimpleControlBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return ExoPlayMode.SHORT_VIDEO == lastExoPlayMode;
    }

    @Override
    protected View[] includeResIdWhileShowWhileFingerTouched() {
        return new View[0];
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_short_video_simple_control_view;
    }

    @Override
    protected void init(Context context) {
        binding.flParent.setVisibility(GONE);
        binding.debug.setVisibility(ExoConfig.COMPONENT_DEBUG_ENABLE ? VISIBLE : GONE);
        binding.eq.setVisibility(ExoConfig.COMPONENT_EQ_ENABLE ? VISIBLE : GONE);
        binding.debugControl.setVisibility(ExoConfig.COMPONENT_DEBUG_ENABLE ? VISIBLE : GONE);
        binding.eqControl.setVisibility(ExoConfig.COMPONENT_EQ_ENABLE ? VISIBLE : GONE);
        binding.ivPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playComplete) {
                    exoControllerWrapper.rePlay();
                } else {
                    exoControllerWrapper.togglePlayPause();
                }
            }
        });
        binding.ivFullScreen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                exoControllerWrapper.toggleFullScreen();
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
        binding.eqControl.setOnClickListener(new OnClickListener() {
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
        binding.debugControl.setOnClickListener(new OnClickListener() {
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
        binding.ivClose.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.flParent.setVisibility(GONE);
                exoControllerWrapper.onShortVideoComponentChangedAction(false, ExoShortVideoSimpleControlBarView.this);
            }
        });
        binding.tvSpeed.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                float speed = exoControllerWrapper.getSpeed();
                if (speed == 0.25f) {
                    exoControllerWrapper.setSpeed(0.5f);
                } else if (speed == 0.5f) {
                    exoControllerWrapper.setSpeed(1.0f);
                } else if (speed == 1f) {
                    exoControllerWrapper.setSpeed(1.25f);
                } else if (speed == 1.25f) {
                    exoControllerWrapper.setSpeed(1.5f);
                } else if (speed == 1.5f) {
                    exoControllerWrapper.setSpeed(1.75f);
                } else if (speed == 1.75f) {
                    exoControllerWrapper.setSpeed(2.0f);
                } else if (speed == 2.0f) {
                    exoControllerWrapper.setSpeed(0.25f);
                }
                updateSpeedBtn();
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
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage) {

    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_PLAYING) {
            playComplete = false;
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_pause);
        } else if (playbackState == ExoPlaybackState.STATE_BUFFERING) {
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_play);
            playComplete = false;
        } else if (playbackState == ExoPlaybackState.STATE_ENDED) {
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_play);
            playComplete = true;
        } else {
            binding.ivPlay.setImageResource(R.drawable.exo_ic_action_play);
        }

    }


    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {
        int marginTop = ExoDensityUtil.dp2px(view.getContext(), 2);
        Rect videoInScreenRect = exoControllerWrapper.getExoPlayerInfo().getVideoInScreenRect();
        if (videoInScreenRect == null || videoInScreenRect.isEmpty()) {
            binding.fullParent.setVisibility(View.GONE);
            ExoLog.log("ivFullScreen 隐藏：视频屏幕Rect无效");
            return;
        }

        DisplayMetrics displayMetrics = view.getContext().getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;   // 屏幕宽度
        int screenHeight = displayMetrics.heightPixels; // 屏幕高度

        int ivWidth = binding.fullParent.getWidth();
        int ivHeight = binding.fullParent.getHeight();
        if (ivWidth == 0 || ivHeight == 0) {
            binding.fullParent.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            ivWidth = binding.fullParent.getMeasuredWidth();
            ivHeight = binding.fullParent.getMeasuredHeight();
        }
        // 计算全屏按钮控件的完整显示矩形（left/top是目标位置，right/bottom是left+宽度、top+高度）
        int targetIvLeft = videoInScreenRect.left + (videoInScreenRect.width() - ivWidth) / 2;
        int targetIvTop = videoInScreenRect.bottom + marginTop;
        Rect ivDisplayRect = new Rect(targetIvLeft, targetIvTop,
                targetIvLeft + ivWidth,
                targetIvTop + ivHeight);

        Rect invalidAreaRect = getInvalidAreaRect();
        boolean isIvNotInInvalidArea = true;
        if (invalidAreaRect != null && !invalidAreaRect.isEmpty()) {
            isIvNotInInvalidArea = !Rect.intersects(ivDisplayRect, invalidAreaRect);
        }

        boolean isPositionValid = videoInScreenRect.bottom + ivHeight <= screenHeight
                && videoInScreenRect.left >= 0
                && videoInScreenRect.right <= screenWidth
                && videoInScreenRect.width() > 0
                && videoInScreenRect.height() > 0
                && isIvNotInInvalidArea;

        if (isPositionValid) {
            binding.fullParent.setVisibility(View.VISIBLE);
            // 水平：与视频Rect居中对齐（视频left + (视频宽度 - 控件宽度)/2）
            int ivLeft = videoInScreenRect.left + (videoInScreenRect.width() - ivWidth) / 2;
            // 垂直：视频Rect的bottom（可加少量间距，比如10dp）
            int ivTop = videoInScreenRect.bottom + marginTop;
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) binding.fullParent.getLayoutParams();
            layoutParams.leftMargin = ivLeft;
            layoutParams.topMargin = ivTop;

            binding.fullParent.setLayoutParams(layoutParams);

            ExoLog.log("ivFullScreen 显示：定位到视频底部，位置 left=" + ivLeft + "，top=" + ivTop);
        } else {
            binding.fullParent.setVisibility(View.GONE);
            ExoLog.log("ivFullScreen 隐藏：视频位置超出屏幕（视频bottom=" + videoInScreenRect.bottom + "，屏幕高度=" + screenHeight + "）");
        }
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

    private static final float SPEED_NORMAL = 1.0f;   // 正常倍速
    private static final float SPEED_LOCKED = 2.0f;   // 锁定倍速
    private boolean currentQuickSpeedMode;
    private boolean normalSpeedModeBeforeTouched;
    private boolean isTouchInRect;
    private Rect rect = new Rect();

    @Override
    public void onSingleFingerPointTouchEvent(int action, float rawX, float rawY, boolean isEdge) {
        super.onSingleFingerPointTouchEvent(action, rawX, rawY, isEdge);

        if (!isEdge) {
            return;
        }

        boolean isCurrentViewVisible = binding.flParent.getVisibility() == VISIBLE;
        if (exoControllerWrapper == null || binding == null) {
            return;
        }

        boolean isRectValid = binding.flContainer.getGlobalVisibleRect(rect);
        if (!isRectValid) {
            return;
        }
        isTouchInRect = rect.contains((int) rawX, (int) rawY);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                normalSpeedModeBeforeTouched = exoControllerWrapper.getSpeed() == SPEED_NORMAL;
                if (isCurrentViewVisible) {
                    changeBottom(isTouchInRect);

                    binding.bottomControlContainer.setVisibility(View.GONE);
                    binding.bottomLockContainer.setVisibility(View.VISIBLE);

                    exoControllerWrapper.setSpeed(normalSpeedModeBeforeTouched ? SPEED_LOCKED : SPEED_NORMAL);
                    updateSpeedBtn();
                    changeBottom(isTouchInRect);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isCurrentViewVisible) {
                    changeBottom(isTouchInRect);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                binding.bottomControlContainer.setVisibility(View.VISIBLE);
                binding.bottomLockContainer.setVisibility(View.GONE);

                if (isTouchInRect) {
                    currentQuickSpeedMode = !currentQuickSpeedMode;
                    if (currentQuickSpeedMode) {
                        Toast.makeText(getContext(), "已锁定 2 倍速，长按边缘下滑取消", Toast.LENGTH_SHORT).show();
                    }
                }

                exoControllerWrapper.setSpeed(currentQuickSpeedMode ? SPEED_LOCKED : SPEED_NORMAL);
                updateSpeedBtn();
                break;
        }
    }

    void changeBottom(boolean isTouchInRect) {
        if (!normalSpeedModeBeforeTouched && exoControllerWrapper.getExoPlayerInfo().getSpeed() != SPEED_NORMAL) {
            if (isTouchInRect) {
                binding.label.setText("松手即恢复正常倍速");
                binding.ivUnlock.setImageResource(R.drawable.exo_ic_action_unlock);
                binding.ivUnlock.setVisibility(View.VISIBLE);
                binding.exoDouyinArrowView.setVisibility(View.INVISIBLE);
            } else {
                binding.label.setText("拖动至此处恢复正常倍速");
                binding.ivUnlock.setImageResource(R.drawable.exo_ic_action_unlock);
                binding.ivUnlock.setVisibility(View.GONE);
                binding.exoDouyinArrowView.setVisibility(View.VISIBLE);
            }
        } else {
            if (isTouchInRect) {
                binding.label.setText("松手锁定 2 倍速");
                binding.ivUnlock.setImageResource(R.drawable.exo_ic_action_lock);
                binding.ivUnlock.setVisibility(View.VISIBLE);
                binding.exoDouyinArrowView.setVisibility(View.INVISIBLE);
            } else {
                binding.label.setText("拖动至此处锁定 2 倍速");
                binding.ivUnlock.setImageResource(R.drawable.exo_ic_action_lock);
                binding.ivUnlock.setVisibility(View.GONE);
                binding.exoDouyinArrowView.setVisibility(View.VISIBLE);
            }
        }
    }

    void updateSpeedBtn() {
        float speed = exoControllerWrapper.getSpeed();
        String speedText = (speed + "").replace(".0", "") + "X";
        binding.tvSpeed.setText(speedText);
        if (speedText.length() == 2) {
            binding.tvSpeed.setTextSize(14f);
        } else if (speedText.length() == 4) {
            binding.tvSpeed.setTextSize(13f);
        } else {
            binding.tvSpeed.setTextSize(12f);
        }
    }

    @Override
    public void onScale(float totalScale) {
        super.onScale(totalScale);
        currentQuickSpeedMode = exoControllerWrapper.getExoPlayerInfo().getSpeed() != SPEED_NORMAL;
        updateSpeedBtn();
        changeBottom(isTouchInRect);
        if (totalScale > ExoConfig.GESTURE_MAX_SCALE) {
            binding.flParent.setVisibility(VISIBLE);
            binding.exoDouyinArrowView.requestLayout();
            binding.exoDouyinArrowView.post(new Runnable() {
                @Override
                public void run() {
                    binding.exoDouyinArrowView.run();
                }
            });
            exoControllerWrapper.onShortVideoComponentChangedAction(true, ExoShortVideoSimpleControlBarView.this);
        }
        if (totalScale <= ExoConfig.GESTURE_MIN_SCALE_WHILE_FINGER_TOUCHED) {
            if (binding.flParent.getVisibility() == VISIBLE) {
                binding.flParent.setVisibility(GONE);
                exoControllerWrapper.onShortVideoComponentChangedAction(false, ExoShortVideoSimpleControlBarView.this);
            }
        }
    }

    @Override
    public void onLongPressStart(int fingerCount, boolean isEdge) {
        super.onLongPressStart(fingerCount, isEdge);
        updateSpeedBtn();
        changeBottom(isTouchInRect);
        binding.bottomControlContainer.setVisibility(GONE);
        binding.bottomLockContainer.setVisibility(VISIBLE);

    }

    @Override
    public void onLongPressEnd(boolean isEdge) {
        super.onLongPressEnd(isEdge);
        binding.bottomControlContainer.setVisibility(VISIBLE);
        binding.bottomLockContainer.setVisibility(INVISIBLE);
    }

    public void reset() {
        setVisibility(VISIBLE);
        binding.flParent.setVisibility(GONE);

    }

    /**
     * 无效区域矩阵
     */
    protected Rect getInvalidAreaRect() {
        return null;
    }
}
