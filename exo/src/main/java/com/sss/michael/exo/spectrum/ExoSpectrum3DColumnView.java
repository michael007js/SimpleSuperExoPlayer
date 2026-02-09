package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.IExoFFTCallBack;


/**
 * @author Michael by 61642
 * @date 2026/1/4 15:37
 * @Description 3D柱状图
 */
public class ExoSpectrum3DColumnView extends View implements IExoFFTCallBack {

    private Paint mMainPaint;
    private Paint mGlowPaint;
    private Paint mGridPaint;

    private float[] mMagnitudes;
    private float[] mDisplayMagnitudes;
    private float[] mVelocity;

    /**
     * 屏幕上显示的柱子总数。
     * 区别：数值越小，每根柱子越宽，视觉冲击力（力量感）越强；
     * 数值越大，频谱越细腻，像是一座密集的建筑群。
     */
    private final int COLUMN_COUNT = 24;

    /**
     * 3D 投影的倾斜程度（透视斜角）。
     * 范围：0.0f - 1.0f。
     * 区别：0.0f 是正视图（普通 2D）；
     * 0.5f 是标准等距视角；
     * 1.0f 会让侧面极度拉长，产生夸张的深度感。
     */
    private final float SKEW_ANGLE = 1.0f;

    /**
     * 柱子本身的厚度（Z 轴长度）。
     * 区别：数值越大，柱子看起来越像个长方体（稳重）；
     * 数值越小，柱子看起来越像一片薄板（轻盈）。
     */
    private final float BAR_DEPTH = 10f;

    /**
     * 弹性系统的“灵敏度”。
     * 范围：0.1f - 0.5f。
     * 区别：数值越高，柱子反应越快，紧贴节奏；
     * 数值越低，柱子动作越“肉”，有一种在液体中跳动的感觉。
     */
    private final float SMOOTH_FACTOR = 0.85f;

    /**
     * 落地回弹力（物理碰撞系数）。
     * 范围：0.0f - 1.0f。
     * 区别：1.0f 时，柱子落到底部网格会产生 100% 的反弹（像乒乓球在水泥地，停不下来）；
     * 0.0f 时，柱子直接贴死在地面（像橡皮泥掉地上）；
     * 0.4f - 0.6f 之间，能产生那种“微微震颤”的机械打击感。
     */
    private final float BOUNCE_FACTOR = 0.5f;

    /**
     * 底部 3D 参考网格的可见度。
     * 范围：0.0f - 1.0f。
     * 区别：0.0f 时网格消失，柱子像漂浮在虚空；
     * 0.3f 时若隐若现，增加空间层次感而不抢戏；
     * 1.0f 时网格极亮，适合赛博朋克风，强调地平线。
     */
    private final float GRID_OPACITY = 0.3f;

    // 赛博朋克调色盘
    private final int[] NEON_COLORS = {0xFF00F2FF, 0xFF0062FF, 0xFF7000FF, 0xFFFF00D4};

    public ExoSpectrum3DColumnView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mMainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGlowPaint.setMaskFilter(new BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER));

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(2f);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // BlurMaskFilter 需要关闭硬件加速
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {}

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        if (mMagnitudes == null) {
            mMagnitudes = new float[COLUMN_COUNT];
            mDisplayMagnitudes = new float[COLUMN_COUNT];
            mVelocity = new float[COLUMN_COUNT];
        }

        int step = magnitude.length / COLUMN_COUNT;

        for (int i = 0; i < COLUMN_COUNT; i++) {
            int index = Math.min(i * step, magnitude.length - 1);

            // 保留原始趋势，但增加瞬时响应幅度
            float mag = magnitude[index];

            // 动态增强高峰响应
            float peakBoost = 2.5f; // 峰值响应系数
            if (mag > mDisplayMagnitudes[i]) {
                mag *= peakBoost; // 只对上升趋势放大
            }

            mMagnitudes[i] = mag;
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDisplayMagnitudes == null) return;

        canvas.drawColor(Color.TRANSPARENT);

        float viewW = getWidth();
        float viewH = getHeight();

        canvas.save();
        canvas.translate(viewW * 0.15f, viewH * 0.9f); // 底部中心位置
        drawNeonGrid(canvas, viewW * 0.7f);

        float barGap = 8f;
        float barW = (viewW * 0.7f - (COLUMN_COUNT * barGap)) / COLUMN_COUNT;

        boolean animating = false;

        for (int i = 0; i < COLUMN_COUNT; i++) {
            // 即使没有新数据，也更新柱子
            if (updatePhysics(i)) animating = true;

            float h = mDisplayMagnitudes[i] * viewH * 0.6f;
            float x = i * (barW + barGap);

            draw3DIsometricBar(canvas, x, 0, barW, h, i);
        }

        canvas.restore();

        // 柱子仍在运动就继续刷新
        if (animating) postInvalidateOnAnimation();
    }

    private boolean updatePhysics(int i) {
        float target = mMagnitudes != null ? mMagnitudes[i] : 0f;
        float diff = target - mDisplayMagnitudes[i];

        if (diff > 0) {
            // 上升：跟随 FFT
            mVelocity[i] += diff * SMOOTH_FACTOR;
        } else {
            // 下降：模拟重力 + 回弹
            mVelocity[i] += diff * 0.1f;// 下落加速度
            mVelocity[i] *= 0.85f;// 阻尼
        }

        mDisplayMagnitudes[i] += mVelocity[i];

        // 触底回弹
        if (mDisplayMagnitudes[i] < 0) {
            mDisplayMagnitudes[i] = 0;
            mVelocity[i] *= -BOUNCE_FACTOR;
        }

        // 是否仍在运动
        return Math.abs(diff) > 0.000000001f || Math.abs(mVelocity[i]) > 0.000000001f;
    }

    private void drawNeonGrid(Canvas canvas, float totalWidth) {
        int lines = 12;
        float depth = 250f;
        long time = System.currentTimeMillis();

        // 纵向线
        for (int i = 0; i <= lines; i++) {
            float x = (totalWidth / lines) * i;
            int baseColor = NEON_COLORS[i % NEON_COLORS.length];
            int alpha = (int) (255 * GRID_OPACITY * (1 - (float) i / lines));

            // 动态闪烁效果
            double v = 0.3 * Math.sin(time * 0.002 + i);
            int r = (int)(Color.red(baseColor) * (0.7 + v));
            int g = (int)(Color.green(baseColor) * (0.7 + v));
            int b = (int)(Color.blue(baseColor) * (0.7 + v));
            mGridPaint.setColor(Color.rgb(r, g, b));
            mGridPaint.setAlpha(alpha);

            canvas.drawLine(x, 0, x + depth * SKEW_ANGLE, -depth * SKEW_ANGLE, mGridPaint);
        }

        // 横向线
        for (int i = 0; i <= lines; i++) {
            float z = (depth / lines) * i;
            int baseColor = NEON_COLORS[i % NEON_COLORS.length];
            int alpha = (int) (255 * GRID_OPACITY * (1 - (float) i / lines));

            double v = 0.7 + 0.3 * Math.sin(time * 0.002 + i);
            int r = (int)(Color.red(baseColor) * v);
            int g = (int)(Color.green(baseColor) * v);
            int b = (int)(Color.blue(baseColor) * v);
            mGridPaint.setColor(Color.rgb(r, g, b));
            mGridPaint.setAlpha(alpha);

            canvas.drawLine(z * SKEW_ANGLE, -z * SKEW_ANGLE, totalWidth + z * SKEW_ANGLE, -z * SKEW_ANGLE, mGridPaint);
        }
    }

    private void draw3DIsometricBar(Canvas canvas, float x, float y, float w, float h, int index) {
        if (h < 2) h = 2;

        float d = BAR_DEPTH;
        float offsetX = d * SKEW_ANGLE;
        float offsetY = d * SKEW_ANGLE;
        float top = y - h;

        int color = NEON_COLORS[index % NEON_COLORS.length];

        // 侧面
        Path sidePath = new Path();
        sidePath.moveTo(x + w, top);
        sidePath.lineTo(x + w + offsetX, top - offsetY);
        sidePath.lineTo(x + w + offsetX, y - offsetY);
        sidePath.lineTo(x + w, y);
        sidePath.close();
        mMainPaint.setColor(color);
        mMainPaint.setAlpha(150);
        canvas.drawPath(sidePath, mMainPaint);

        // 顶面 + 发光
        Path topPath = new Path();
        topPath.moveTo(x, top);
        topPath.lineTo(x + offsetX, top - offsetY);
        topPath.lineTo(x + w + offsetX, top - offsetY);
        topPath.lineTo(x + w, top);
        topPath.close();

        float glowRadius = 10f + h * 0.5f;
        mGlowPaint.setMaskFilter(new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.OUTER));
        mGlowPaint.setColor(color);
        canvas.drawPath(topPath, mGlowPaint);

        mMainPaint.setColor(Color.WHITE);
        mMainPaint.setAlpha(200);
        canvas.drawPath(topPath, mMainPaint);

        // 正面渐变
        LinearGradient gradient = new LinearGradient(
                x, top, x, y,
                Color.argb(220, 255, 255, 255), color,
                Shader.TileMode.CLAMP
        );
        mMainPaint.setShader(gradient);
        canvas.drawRect(x, top, x + w, y, mMainPaint);
        mMainPaint.setShader(null);
    }
}
