package com.sss.michael.exo.component;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.R;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.databinding.LayoutComponentExoGestureControlViewBinding;
import com.sss.michael.exo.util.ExoFormatUtil;

/**
 * @author Michael by SSS
 * @date 2025/12/28 0028 16:54
 * @Description 视频+直播手势控制视图
 */
@SuppressLint("SetTextI18n")
public class ExoComponentGestureVodControlView extends BaseExoControlComponent<LayoutComponentExoGestureControlViewBinding> {
    private boolean shown;
    private long currentPosition;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            binding.centerContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {


                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            binding.centerContainer.setVisibility(View.GONE);
                            shown = false;
                        }
                    })
                    .start();

        }
    };

    public ExoComponentGestureVodControlView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentGestureVodControlView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentGestureVodControlView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(runnable);
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_component_exo_gesture_control_view;
    }

    @Override
    protected void init(Context context) {


    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    private void show() {
        removeCallbacks(runnable);
        if (!shown) {
            shown = true;
            binding.centerContainer.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            super.onAnimationStart(animation);
                            binding.centerContainer.setVisibility(View.VISIBLE);
                        }
                    })
                    .start();
        }
    }

    @Override
    public void onProgressChange(long current, long total, long seekTo) {
        show();
        binding.proPercent.setVisibility(GONE);
        if (seekTo > currentPosition) {
            binding.ivIcon.setImageResource(R.drawable.exo_ic_action_fast_forward);
        } else {
            binding.ivIcon.setImageResource(R.drawable.exo_ic_action_fast_rewind);
        }
        binding.tvPercent.setText(String.format("%s/%s", ExoFormatUtil.stringForTime(seekTo), ExoFormatUtil.stringForTime(currentPosition)));
        exoControllerWrapper.seekTo(seekTo);
    }

    @Override
    public void onVolumeChange(int current, int max) {
        show();
        float rawPercent = current * 1.0f / max;
        int percent = (int) (rawPercent * 100);
        binding.proPercent.setVisibility(VISIBLE);
        if (percent <= 0) {
            binding.ivIcon.setImageResource(R.drawable.exo_ic_action_volume_off);
        } else {
            binding.ivIcon.setImageResource(R.drawable.exo_ic_action_volume_up);
        }
        binding.tvPercent.setText(percent + "%");
        binding.proPercent.setProgress(percent);
    }


    @Override
    public void onBrightnessChange(float percent) {
        show();
        float rawPercent = percent * 100;
        int value = (int) rawPercent;
        binding.proPercent.setVisibility(VISIBLE);
        binding.ivIcon.setImageResource(R.drawable.exo_ic_action_brightness);
        binding.tvPercent.setText(value + "%");
        binding.proPercent.setProgress(value);
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
    public void onGestureStart() {
        super.onGestureStart();
        currentPosition = exoControllerWrapper.getCurrentPosition();

    }

    @Override
    public void onGestureEnd() {
        super.onGestureEnd();
        if (shown) {
            postDelayed(runnable, 2000);
        }
    }

    @Override
    public void setPlayLocationLastTime(String tip) {

    }
}
