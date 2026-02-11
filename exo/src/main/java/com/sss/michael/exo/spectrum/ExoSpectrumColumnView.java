package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Px;

import com.sss.michael.exo.callback.IExoFFTCallBack;

/**
 * @author Michael by 61642
 * @date 2026/1/4 15:37
 * @Description 柱状图
 */
public class ExoSpectrumColumnView extends View implements IExoFFTCallBack {
    public enum SpectrumDrawMode {
        TOP_ONLY,
        BOTTOM_ONLY,
        BOTH_SIDES
    }

    // 柱体绘制画笔（支持纯色 / 渐变色）
    private final Paint columnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // 悬停点（peak/dot）绘制画笔
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // 顶部柱体 Rect（TOP_ONLY / BOTH_SIDES 模式使用）
    private RectF[] topRects;
    // 底部柱体 Rect（BOTTOM_ONLY / BOTH_SIDES 模式使用）
    private RectF[] bottomRects;
    // 当前柱体高度数组（每个 FFT bin 对应一个柱体）
    private float[] columnHeights;
    // 柱体回落速度数组（与 columnHeights 一一对应）
    private float[] columnFallSpeeds;
    // 悬停点当前高度（表示历史峰值位置）
    private float[] dotHeights;
    // 悬停点下落速度（重力模型）
    private float[] dotFallSpeeds;
    // 主色调（柱体 + 悬停点基础颜色）
    private int mainColor = Color.parseColor("#F53F3F");
    // 相邻柱体之间的间距（px）
    private int columnSpacing = 3;
    // 柱体最小宽度（防止 FFT bin 过多时柱体过细）
    private int columnMinWidth = 6;
    // 动态计算后的柱体实际宽度
    private float dynamicColumnWidth;
    // 柱体圆角半径（会自动限制不超过柱体宽度一半）
    private int columnCornerRadius = 10;
    // 中心偏移量（用于上下对称或防止柱体贴边）
    private int centerOffset = 4;
    //  悬停点下落重力加速度，数值越大，下落越快
    private float dotGravity = 0.05f;
    // 柱体回落加速度，控制柱体从峰值回落到 0 的速度
    private float columnFallAcceleration = 0.35f;
    // 悬停点高度（px）
    private int dotHeight = 2;
    // 悬停点与柱体之间的垂直偏移量
    private int dotOffset = 2;
    // 是否启用横向渐变色（中间亮、两侧透明）
    private boolean gradientColor = true;
    // 当前频谱绘制模式（顶部 / 底部 / 上下对称）
    private SpectrumDrawMode drawMode = SpectrumDrawMode.TOP_ONLY;
    // 当前是否仍有动画在进行（用于控制是否继续 invalidate）
    private boolean animating = false;


    public ExoSpectrumColumnView(Context context) {
        this(context, null);
    }

    public ExoSpectrumColumnView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoSpectrumColumnView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        columnPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(mainColor);
    }

    /* ==================== 外部配置 ==================== */

    public void setSpectrumDrawMode(SpectrumDrawMode mode) {
        this.drawMode = mode;
        invalidate();
    }

    public void setMainColor(int color) {
        this.mainColor = color;
        dotPaint.setColor(color);
        updateGradient();
        invalidate();
    }

    public void setColumnSpacing(@Px int spacing) {
        this.columnSpacing = Math.max(0, spacing);
        invalidate();
    }

    public void setColumnMinWidth(@Px int minWidth) {
        this.columnMinWidth = Math.max(1, minWidth);
        invalidate();
    }

    public void setColumnFallAcceleration(float acc) {
        this.columnFallAcceleration = Math.max(0.05f, acc);
    }

    public void setDotGravity(float gravity) {
        this.dotGravity = Math.max(0.05f, gravity);
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {

    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        if (magnitude == null || magnitude.length == 0 || !isAttachedToWindow()) return;
        final int count = magnitude.length;

        ensureArrays(count);

        float maxHeight = drawMode == SpectrumDrawMode.BOTH_SIDES
                ? getHeight() / 2f - 20
                : getHeight() - 20;

        for (int i = 0; i < count; i++) {
            float amp = magnitude[i];

            float norm = Math.min(amp, 0.9f);
            float eased = (float) Math.pow(norm, 0.52f);

            float target = eased * maxHeight * 0.75f;

            if (target > columnHeights[i]) {
                columnHeights[i] = target;
                columnFallSpeeds[i] = 0f;
            }
        }

        animating = true;
        postInvalidateOnAnimation();
    }

    private boolean updateAnimation() {
        boolean moving = false;
        calculateColumnWidth();

        for (int i = 0; i < columnHeights.length; i++) {

            // 柱体回落
            if (columnHeights[i] > 0f) {
                columnFallSpeeds[i] += columnFallAcceleration;
                columnHeights[i] -= columnFallSpeeds[i];
                if (columnHeights[i] < 0f) {
                    columnHeights[i] = 0f;
                    columnFallSpeeds[i] = 0f;
                }
            }

            // 悬停点
            if (columnHeights[i] > dotHeights[i]) {
                dotHeights[i] = columnHeights[i];
                dotFallSpeeds[i] = 0f;
            } else {
                dotFallSpeeds[i] += dotGravity;
                dotHeights[i] -= dotFallSpeeds[i];
                if (dotHeights[i] < 0f) dotHeights[i] = 0f;
            }

            if (columnHeights[i] > 0 || dotHeights[i] > 0) {
                moving = true;
            }
        }
        return moving;
    }


    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (columnHeights == null) return;

        animating = updateAnimation();

        int h = getHeight();
        float step = dynamicColumnWidth + columnSpacing;
        float radius = Math.min(columnCornerRadius, dynamicColumnWidth / 2f);

        for (int i = 0; i < columnHeights.length; i++) {
            float left = i * step;
            float height = columnHeights[i];
            if (height <= 0f) continue;

            if (drawMode != SpectrumDrawMode.BOTTOM_ONLY) {
                float base = drawMode == SpectrumDrawMode.BOTH_SIDES ? h / 2f - centerOffset : h - centerOffset;
                topRects[i].set(left, base - height, left + dynamicColumnWidth, base);
                canvas.drawRoundRect(topRects[i], radius, radius, columnPaint);
                drawDot(canvas, left, base - dotHeights[i] - dotOffset);
            }

            if (drawMode != SpectrumDrawMode.TOP_ONLY) {
                float base = drawMode == SpectrumDrawMode.BOTH_SIDES ? h / 2f + centerOffset : centerOffset;
                bottomRects[i].set(left, base, left + dynamicColumnWidth, base + height);
                canvas.drawRoundRect(bottomRects[i], radius, radius, columnPaint);
                drawDot(canvas, left, base + dotHeights[i] + dotOffset);
            }
        }

        if (animating) postInvalidateOnAnimation();
    }

    private void drawDot(Canvas canvas, float left, float y) {
        canvas.drawRoundRect(
                left,
                y,
                left + dynamicColumnWidth,
                y + dotHeight,
                columnCornerRadius,
                columnCornerRadius,
                dotPaint
        );
    }

    private void calculateColumnWidth() {
        int count = columnHeights.length;
        float total = getWidth() - (count - 1) * columnSpacing;
        dynamicColumnWidth = Math.max(total / count, columnMinWidth);
    }

    private void ensureArrays(int count) {
        if (columnHeights != null && columnHeights.length == count) return;

        columnHeights = new float[count];
        columnFallSpeeds = new float[count];
        dotHeights = new float[count];
        dotFallSpeeds = new float[count];

        topRects = new RectF[count];
        bottomRects = new RectF[count];

        for (int i = 0; i < count; i++) {
            topRects[i] = new RectF();
            bottomRects[i] = new RectF();
        }
    }

    private void updateGradient() {
        if (!gradientColor || getWidth() == 0) {
            columnPaint.setShader(null);
            columnPaint.setColor(mainColor);
            return;
        }

        LinearGradient shader = new LinearGradient(
                0, 0, getWidth(), 0,
                new int[]{
                        adjustAlpha(mainColor, 0.3f),
                        mainColor,
                        mainColor,
                        adjustAlpha(mainColor, 0.3f)
                },
                new float[]{0f, 0.2f, 0.8f, 1f},
                Shader.TileMode.CLAMP
        );
        columnPaint.setShader(shader);
    }

    private int adjustAlpha(int color, float alpha) {
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGradient();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animating = false;
        if (columnHeights == null) return;

        for (int i = 0; i < columnHeights.length; i++) {
            columnHeights[i] = 0f;
            dotHeights[i] = 0f;
            columnFallSpeeds[i] = 0f;
            dotFallSpeeds[i] = 0f;
        }
    }
}
