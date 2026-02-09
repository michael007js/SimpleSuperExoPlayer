package com.sss.michael.exo.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by 61642
 * @date 2026/1/29 18:40
 * @Description 抖音流式箭头
 */
public class ExoDouyinArrowView extends View {
    // 箭头数量
    private int mArrowCount = 3;
    // 箭头的宽度（V形的横向尺寸）
    private float mArrowSize = 30;
    // 箭头之间的垂直间距
    private float mArrowGap = -8;
    private float mStrokeWidth = 3f;

    private int mArrowColor = Color.WHITE;
    private Paint mArrowPaint = new Paint();
    private List<Path> mArrowPaths = new ArrayList<>();
    private List<Integer> mArrowAlphas = new ArrayList<>();

    private List<ValueAnimator> mAnimatorList = new ArrayList<>();

    public ExoDouyinArrowView(Context context) {
        this(context, null);
    }

    public ExoDouyinArrowView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoDouyinArrowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mArrowPaint.setAntiAlias(true);
        mArrowPaint.setStyle(Paint.Style.STROKE);
        mArrowPaint.setStrokeWidth(mStrokeWidth);
        mArrowPaint.setColor(mArrowColor);
        mArrowPaint.setStrokeCap(Paint.Cap.ROUND);

    }

    public void run() {
        stopAllAnimations();
        startArrowGradientAnimation();
    }

    public void setArrowCount(int count) {
        if (count < 1) count = 1;
        if (this.mArrowCount == count) return;

        this.mArrowCount = count;
        stopAllAnimations();

        // 重置路径和透明度
        mArrowPaths.clear();
        mArrowAlphas.clear();
        mAnimatorList.clear();

        requestLayout();
        invalidate();
        post(this::run);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (int) (mArrowSize + getPaddingLeft() + getPaddingRight());
        float singleArrowHeight = mArrowSize / 2f;
        int desiredHeight = (int) (singleArrowHeight + (mArrowCount - 1) * (singleArrowHeight + mArrowGap)
                + getPaddingTop() + getPaddingBottom());

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int finalWidth;
        switch (widthMode) {
            case MeasureSpec.EXACTLY:
                finalWidth = widthSize;
                break;
            case MeasureSpec.AT_MOST:
                finalWidth = Math.min(desiredWidth, widthSize);
                break;
            default:
                finalWidth = desiredWidth;
                break;
        }

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int finalHeight;
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                finalHeight = heightSize;
                break;
            case MeasureSpec.AT_MOST:
                finalHeight = Math.min(desiredHeight, heightSize);
                break;
            default:
                finalHeight = desiredHeight;
                break;
        }

        setMeasuredDimension(finalWidth, finalHeight + 10);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mArrowCount == 0 || w == 0 || h == 0) return;

        float centerX = w / 2f;
        float singleArrowHeight = mArrowSize / 2f; // V形箭头的垂直高度
        // 计算所有箭头的总垂直占用高度
        float totalArrowHeight = singleArrowHeight + (mArrowCount - 1) * (singleArrowHeight + mArrowGap);
        // 计算第一个箭头的起始Y坐标（让所有箭头在View中垂直居中）
        float startY = (h - totalArrowHeight) / 2f + singleArrowHeight;

        mArrowPaths.clear();
        mArrowAlphas.clear();

        for (int i = 0; i < mArrowCount; i++) {
            Path path = new Path();
            // 计算当前箭头的顶点Y坐标（V形的最低点）
            float currentArrowTopY = startY + i * (singleArrowHeight + mArrowGap);
            // 绘制V形箭头（从左到右，从底边到顶点）
            path.moveTo(centerX - mArrowSize / 2f, currentArrowTopY - singleArrowHeight);
            path.lineTo(centerX, currentArrowTopY);
            path.lineTo(centerX + mArrowSize / 2f, currentArrowTopY - singleArrowHeight);
            mArrowPaths.add(path);
            // 初始透明度：交替明暗，保证动画初始效果
            mArrowAlphas.add(i % 2 == 0 ? 100 : 255);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int i = 0; i < mArrowPaths.size(); i++) {
            mArrowPaint.setAlpha(mArrowAlphas.get(i));
            canvas.drawPath(mArrowPaths.get(i), mArrowPaint);
        }
    }

    private void startArrowGradientAnimation() {
        if (mArrowPaths == null || mArrowPaths.isEmpty() || !ViewCompat.isAttachedToWindow(this)) {
            return;
        }

        for (int i = 0; i < mArrowCount; i++) {
            final int index = i;
            // 透明度在100（暗）和255（亮）之间循环渐变
            ValueAnimator animator = ValueAnimator.ofInt(100, 255, 100);
            animator.setDuration(1000);
            animator.setStartDelay(i * 300); // 每个箭头延迟300ms启动，形成向下流动
            animator.setRepeatCount(ValueAnimator.INFINITE); // 无限循环
            animator.setRepeatMode(ValueAnimator.RESTART);

            animator.addUpdateListener(animation -> {
                if (ViewCompat.isAttachedToWindow(ExoDouyinArrowView.this)
                        && index < mArrowAlphas.size()) {
                    mArrowAlphas.set(index, (int) animation.getAnimatedValue());
                    invalidate();
                }
            });

            mAnimatorList.add(animator);
            animator.start();
        }
    }

    private void stopAllAnimations() {
        if (mAnimatorList != null) {
            for (ValueAnimator animator : mAnimatorList) {
                if (animator != null) {
                    animator.removeAllUpdateListeners();
                    animator.removeAllListeners();
                    if (animator.isRunning()) {
                        animator.cancel();
                    }
                    animator.setRepeatCount(0);
                }
            }
            mAnimatorList.clear();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAllAnimations();
        if (mArrowPaths != null) {
            mArrowPaths.clear();
            mArrowPaths = null;
        }
        if (mArrowAlphas != null) {
            mArrowAlphas.clear();
            mArrowAlphas = null;
        }
        if (mAnimatorList != null) {
            mAnimatorList.clear();
            mAnimatorList = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mArrowPaths == null) mArrowPaths = new ArrayList<>();
        if (mArrowAlphas == null) mArrowAlphas = new ArrayList<>();
        if (mAnimatorList == null) mAnimatorList = new ArrayList<>();

        post(this::run);
    }

}