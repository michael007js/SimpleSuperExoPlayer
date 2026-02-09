package com.sss.michael.exo.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.sss.michael.exo.util.ExoDensityUtil;
import com.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by SSS
 * @date 2026/1/2
 * @Description 交互式进度条：支持普通模式和抖音模式（平时极细，触摸加粗弹出圆点）
 */
public class ExoSeekBar extends View {

    private Paint paint;
    private RectF rectF;

    // 颜色配置
    private int backgroundColor = Color.parseColor("#33FFFFFF");
    private int bufferedColor = Color.parseColor("#66FFFFFF");
    private int progressColor = Color.parseColor("#FCFFFFFF");
    private int thumbColor = Color.WHITE;

    // 数据
    private long max = 100;
    private long currentProgress = 0;
    private long bufferedProgress = 0;

    // 交互参数
    private boolean isDragging = false;
    private boolean isDouyinStyle = false; // 默认开启抖音模式

    // 尺寸定义
    private final float BAR_HEIGHT_NORMAL;   // 抖音模式下的默认高度
    private final float BAR_HEIGHT_EXPANDED; // 展开后的高度（也是普通模式的高度）
    private final float THUMB_RADIUS_MAX;    // 圆点最大半径

    // 动态计算值
    private float currentBarHeight;
    private float currentThumbRadius;

    private ValueAnimator animator;
    private float animFraction = 0f;

    private OnSeekBarChangeListener listener;

    public interface OnSeekBarChangeListener {
        void onProgressChanged(ExoSeekBar seekBar, long progress, boolean fromUser);

        void onStartTrackingTouch(ExoSeekBar seekBar);

        void onStopTrackingTouch(ExoSeekBar seekBar);
    }

    public ExoSeekBar(Context context) {
        this(context, null);
    }

    public ExoSeekBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rectF = new RectF();

        // 统一尺寸定义
        BAR_HEIGHT_NORMAL = ExoDensityUtil.dp2px(context, 1.5f);
        BAR_HEIGHT_EXPANDED = ExoDensityUtil.dp2px(context, 4f);
        THUMB_RADIUS_MAX = ExoDensityUtil.dp2px(context, 6f);

        // 初始化状态
        updateStyleConstants();
    }

    /**
     * 设置是否开启抖音风格模式
     *
     * @param isDouyinStyle true: 极细且动态弹出圆点, false: 普通模式常显圆点
     */
    public void setDouyinStyle(boolean isDouyinStyle) {
        this.isDouyinStyle = isDouyinStyle;
        updateStyleConstants();
        invalidate();
    }

    private void updateStyleConstants() {
        if (isDouyinStyle) {
            currentBarHeight = BAR_HEIGHT_NORMAL;
            currentThumbRadius = 0f;
            animFraction = 0f;
        } else {
            currentBarHeight = BAR_HEIGHT_EXPANDED;
            currentThumbRadius = THUMB_RADIUS_MAX;
            animFraction = 1.0f;
        }
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener) {
        this.listener = listener;
    }

    public void setMax(long max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public long getMax() {
        return max;
    }

    public void setProgress(long progress) {
        if (!isDragging) {
            this.currentProgress = Math.min(max, Math.max(0, progress));
            invalidate();
        }
    }

    public long getProgress() {
        return currentProgress;
    }

    public void setBufferedProgress(long bufferedProgress) {
        this.bufferedProgress = Math.min(max, Math.max(0, bufferedProgress));
        ExoLog.log("缓冲:" + bufferedProgress);
        invalidate();
    }

    private void startSwitchAnimation(boolean expand) {
        // 如果不是抖音模式，不需要动画
        if (!isDouyinStyle) return;

        if (animator != null) animator.cancel();
        float start = animFraction;
        float end = expand ? 1.0f : 0.0f;

        animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animFraction = (float) animation.getAnimatedValue();
            currentBarHeight = BAR_HEIGHT_NORMAL + (BAR_HEIGHT_EXPANDED - BAR_HEIGHT_NORMAL) * animFraction;
            currentThumbRadius = THUMB_RADIUS_MAX * animFraction;
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float centerY = height / 2f;

        float padding = THUMB_RADIUS_MAX;
        float usableWidth = width - 2 * padding;

        // 底层背景
        paint.setColor(backgroundColor);
        rectF.set(padding, centerY - currentBarHeight / 2, width - padding, centerY + currentBarHeight / 2);
        canvas.drawRoundRect(rectF, currentBarHeight, currentBarHeight, paint);

        // 缓冲层
        if (bufferedProgress > 0) {
            paint.setColor(bufferedColor);
            float bRight = padding + (usableWidth * bufferedProgress / max);
            rectF.set(padding, centerY - currentBarHeight / 2, bRight, centerY + currentBarHeight / 2);
            canvas.drawRoundRect(rectF, currentBarHeight, currentBarHeight, paint);
        }

        // 进度层
        paint.setColor(progressColor);
        float pRight = padding + (usableWidth * currentProgress / max);
        rectF.set(padding, centerY - currentBarHeight / 2, pRight, centerY + currentBarHeight / 2);
        canvas.drawRoundRect(rectF, currentBarHeight, currentBarHeight, paint);

        // 绘制拖动圆点
        if (currentThumbRadius > 0) {
            paint.setColor(thumbColor);
            canvas.drawCircle(pRight, centerY, currentThumbRadius, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;

        float x = event.getX();
        float padding = THUMB_RADIUS_MAX;
        float usableWidth = getWidth() - 2 * padding;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                startSwitchAnimation(true);
                if (listener != null) listener.onStartTrackingTouch(this);
                updateOnTouch(x, padding, usableWidth);
                return true;

            case MotionEvent.ACTION_MOVE:
                updateOnTouch(x, padding, usableWidth);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                startSwitchAnimation(false);
                if (listener != null) listener.onStopTrackingTouch(this);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateOnTouch(float x, float padding, float usableWidth) {
        if (usableWidth <= 0) return;
        float relativeX = Math.min(usableWidth + padding, Math.max(padding, x)) - padding;
        currentProgress = (long) (relativeX / usableWidth * max);
        if (listener != null) {
            listener.onProgressChanged(this, currentProgress, true);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minHeight = (int) (THUMB_RADIUS_MAX * 4);
        setMeasuredDimension(resolveSize(100, widthMeasureSpec), resolveSize(minHeight, heightMeasureSpec));
    }
}