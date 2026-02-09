package cn.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.callback.ExoControllerWrapper;
import cn.sss.michael.exo.callback.ExoGestureEnable;
import cn.sss.michael.exo.callback.IExoControlComponent;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by SSS
 * @date 2026/1/2 10:10
 * @Description 控制组件协调布局
 */
public abstract class IExoControlComponentLayout extends FrameLayout implements IExoControlComponent {

    protected ExoControllerWrapper exoControllerWrapper;
    // 动画时长（毫秒），400ms 比较柔和
    protected static final int ANIM_DURATION = 400;
    // 自动隐藏延时（毫秒）
    protected static final int HIDE_DELAY = 5000;
    // 隐藏任务
    private final Runnable hideRunnable = this::fadeOut;
    // 最后播放状态
    private int lastInformedState = -1;
    // 记录当前 UI 是否应该处于显示状态
    private boolean isShowing = false;
    // 进度条是否被拖动
    protected boolean isSeekbarDragging;

    public IExoControlComponentLayout(@NonNull Context context) {
        this(context, null);
    }

    public IExoControlComponentLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IExoControlComponentLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 手指触摸屏幕时是否跟随立即显示
     *
     * @return true 跟随 false 不跟随
     */
    protected abstract boolean showWhileFingerTouched();

    /**
     * 手指触摸时需要参与淡入淡出的 View 集合，默认是自身
     */
    protected View[] includeResIdWhileShowWhileFingerTouched() {
        return new View[]{this};
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        View[] includes = includeResIdWhileShowWhileFingerTouched();
        if (includes != null) {
            for (View v : includes) {
                if (v != null) v.animate().cancel();
            }
        }
        removeCallbacks(hideRunnable);
    }

    @Override
    public void onPlayerError(String errorMsg, Throwable throwable) {
        fadeIn(false);
    }

    /**
     * 播放状态被改变时回调
     *
     * @param playbackState     状态码
     * @param playbackStateName 状态名
     */
    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        if (lastInformedState == playbackState) return;
        lastInformedState = playbackState;

        // 当进入播放状态时，延时隐藏 UI
        if (playbackState == ExoPlaybackState.STATE_PLAYING) {
            if (showWhileFingerTouched()) {
                resetHideTimer();
            }
        } else if (playbackState == ExoPlaybackState.STATE_PLAY_PAUSE) {
            // 暂停时，保持 UI 显示
            removeCallbacks(hideRunnable);
        }
    }

    /**
     * 播放器形态被改变时回调
     *
     * @param playerState     播放器形态码
     * @param playerStateName 播放器形态名
     * @param playerView      播放器视图
     */
    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        if (ExoPlayerMode.PLAYER_FULL_SCREEN == playerState) {
            if (showWhileFingerTouched()) {
                fadeIn(true);
            }
        } else if (ExoPlayerMode.PLAYER_NORMAL == playerState) {
            // 只有正在播放时才切换回小屏自动隐藏
            if (exoControllerWrapper != null && exoControllerWrapper.isPlaying() && showWhileFingerTouched()) {
                fadeOut();
            }
        }
    }

    /**
     * 柔和显示：淡入
     *
     * @param autoHide 是否在显示后触发自动隐藏计时
     */
    protected void fadeIn(boolean autoHide) {
        removeCallbacks(hideRunnable);
        View[] includes = includeResIdWhileShowWhileFingerTouched();
        if (includes == null || includes.length == 0) return;

        ExoLog.log("UI淡入: " + getClass().getSimpleName());
        isShowing = true;

        for (int i = 0; i < includes.length; i++) {
            View child = includes[i];
            if (child == null) continue;

            child.animate().cancel();
            if (child.getVisibility() != VISIBLE) {
                child.setAlpha(0f);
                child.setVisibility(VISIBLE);
            }

            // 如果已经完全显示，不再重复跑动画
            if (child.getAlpha() == 1f) {
                if (i == includes.length - 1) onViewFadeInOutComplete(true);
                continue;
            }
            ViewPropertyAnimator animator = child.animate()
                    .alpha(1f)
                    .setDuration(ANIM_DURATION)
                    .setListener(null);

            if (i == includes.length - 1) {
                animator.withEndAction(() -> onViewFadeInOutComplete(true));
            }
            animator.start();
        }

        if (autoHide) {
            resetHideTimer();
        }
    }

    /**
     * 重置隐藏计时器
     */
    private void resetHideTimer() {
        removeCallbacks(hideRunnable);
        postDelayed(hideRunnable, HIDE_DELAY);
    }

    /**
     * 柔和隐藏：淡出
     */
    protected void fadeOut() {
        // 如果进度条正在拖动，拦截隐藏逻辑
        if (isSeekbarDragging) {
            ExoLog.log("进度条拖动中，不触发隐藏: " + getClass().getSimpleName());
            return;
        }
        removeCallbacks(hideRunnable);
        View[] includes = includeResIdWhileShowWhileFingerTouched();
        if (includes == null || includes.length == 0) return;

        ExoLog.log("UI淡出: " + getClass().getSimpleName());
        isShowing = false;

        for (int i = 0; i < includes.length; i++) {
            View child = includes[i];
            if (child == null) continue;

            child.animate().cancel();
            ViewPropertyAnimator animator = child.animate()
                    .alpha(0f)
                    .setDuration(ANIM_DURATION);

            if (i == includes.length - 1) {
                animator.withEndAction(() -> {
                    // 确保动画结束后，如果用户没在这期间点开，则隐藏
                    if (!isShowing) {
                        for (View v : includes) {
                            if (v != null) v.setVisibility(GONE);
                        }
                        onViewFadeInOutComplete(false);
                    }
                });
            }
            animator.start();
        }
    }

    /**
     * 视图完全淡入淡出时回调
     *
     * @param fadeIn true 淡入完毕  false 淡出完毕
     */
    protected void onViewFadeInOutComplete(boolean fadeIn) {

    }

    @Override
    public void attach(@NonNull ExoControllerWrapper exoControllerWrapper) {
        this.exoControllerWrapper = exoControllerWrapper;
    }

    /**
     * 手势触摸开始：第一根手指触摸时触发（ACTION_DOWN）
     * 用于 UI 层显示提示蒙层
     */
    @Override
    public void onGestureStart() {
        // 手势开始，移除隐藏任务
        removeCallbacks(hideRunnable);
    }

    /**
     * 手指触摸点击
     *
     * @param fingerCount 手指数量
     * @param singleClick true 单击 false 双击
     */
    @Override
    public void onFingerTouchClick(int fingerCount, boolean singleClick) {
        if (singleClick && fingerCount == 1) {
            if (showWhileFingerTouched() && exoControllerWrapper != null) {
                // 如果当前已经是显示状态，点击则隐藏；如果是隐藏状态，点击则显示
                if (isShowing) {
                    fadeOut();
                } else {
                    fadeIn(true);
                }
            }
        }
    }

    /**
     * 手势触摸结束：最后一根手指离开时触发（ACTION_UP / ACTION_CANCEL）
     * 用于 UI 层隐藏提示蒙层
     */
    @Override
    public void onGestureEnd() {
        // 手势结束，如果 UI 是显示的，重置 5s 倒计时
        if (isShowing && showWhileFingerTouched()) {
            resetHideTimer();
        }
    }

    /**
     * 单点触摸回调
     *
     * @param action MotionEvent意图
     * @param rawX   相当于屏幕左上角X值
     * @param rawY   相对于屏幕左上角Y值
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onSingleFingerPointTouchEvent(int action, float rawX, float rawY, boolean isEdge) {

    }

    /**
     * 缩放回调
     *
     * @param totalScale 所放量 范围受
     *                   {@link ExoConfig#GESTURE_MIN_SCALE}
     *                   {@link ExoConfig#GESTURE_MAX_SCALE}
     *                   限制
     */
    @Override
    public void onScale(float totalScale) {

    }

    /**
     * 长按开始（仅考虑单指）
     *
     * @param fingerCount 手指数量
     * @param isEdge      边缘触发
     *                    取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onLongPressStart(int fingerCount, boolean isEdge) {

    }

    /**
     * 长按结束（手指抬起）
     *
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onLongPressEnd(boolean isEdge) {

    }

    /**
     * FFT原始数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     * @param fft          FFT原始数据数组
     */
    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {

    }

    /**
     * 频谱幅度数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param magnitude    频谱幅度数组
     */
    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {

    }

    /**
     * 短视频组件有更改意图时回调（涉及外部列表适配器交互，外部组件的更改动作由外部执行）
     *
     * @param clearScreenMode                   清屏模式
     * @param exoShortVideoSimpleControlBarView 清屏控制组件
     */
    @Override
    public void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView) {

    }
}
