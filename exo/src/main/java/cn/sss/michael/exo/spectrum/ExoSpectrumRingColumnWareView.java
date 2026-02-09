package cn.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import cn.sss.michael.exo.callback.IExoFFTCallBack;

/**
 * @author Michael by SSS
 * @date 2026/1/7 0007 20:54
 * @Description 环形柱状水波纹
 */
public class ExoSpectrumRingColumnWareView extends View implements IExoFFTCallBack {

    private static final int BANDS = 64;
    private static final float DECAY_FAST = 0.82f;
    private static final float DECAY_SLOW = 0.90f;

    private boolean mirror = false;

    private Paint mFrontPaint;   // 前景实体柱
    private Paint mBackGlowPaint;// 背景能量面

    private float[] mFrontValues;
    private float[] mBackValues;

    private int mWidth, mHeight;
    private float mInnerRadius;

    public ExoSpectrumRingColumnWareView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFrontPaint.setStrokeCap(Paint.Cap.ROUND);

        mBackGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackGlowPaint.setStrokeCap(Paint.Cap.ROUND);

        mFrontValues = new float[BANDS];
        mBackValues = new float[BANDS];
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        mInnerRadius = Math.min(w, h) * 0.28f;
    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        if (magnitude == null) return;

        int limit = mirror ? BANDS / 2 : BANDS;

        for (int i = 0; i < limit; i++) {
            float power = magnitude[i % magnitude.length] * 5200f;
            power = (float) Math.pow(power, 0.85); // 酷狗味道的非线性

            if (power > mFrontValues[i]) {
                mFrontValues[i] = power;
                if (mirror) mFrontValues[BANDS - 1 - i] = power;
            }

            if (power > mBackValues[i]) {
                mBackValues[i] = power;
                if (mirror) mBackValues[BANDS - 1 - i] = power;
            }
        }
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mWidth == 0) return;

        canvas.translate(mWidth / 2f, mHeight / 2f);
        float angleStep = 360f / BANDS;

        for (int i = 0; i < BANDS; i++) {

            mFrontValues[i] *= DECAY_FAST;
            mBackValues[i] *= DECAY_SLOW;

            float front = mFrontValues[i];
            float back = mBackValues[i] * 1.35f;

            canvas.save();
            canvas.rotate(i * angleStep);

            // ===== 背景能量面（酷狗核心）=====
            float glowBlur = 30 + back * 0.08f;
            mBackGlowPaint.setMaskFilter(
                    new BlurMaskFilter(glowBlur, BlurMaskFilter.Blur.NORMAL)
            );
            mBackGlowPaint.setStrokeWidth(mWidth / 18f);

            mBackGlowPaint.setShader(new LinearGradient(
                    0, -mInnerRadius,
                    0, -(mInnerRadius + back),
                    new int[]{
                            Color.parseColor("#5520F6FF"),
                            Color.parseColor("#553A7BFF"),
                            Color.TRANSPARENT
                    },
                    new float[]{0f, 0.7f, 1f},
                    Shader.TileMode.CLAMP
            ));

            canvas.drawLine(
                    0, -mInnerRadius,
                    0, -(mInnerRadius + back),
                    mBackGlowPaint
            );

            // ===== 前景实体柱 =====
            mFrontPaint.setStrokeWidth(mWidth / 120f);
            mFrontPaint.setShader(new LinearGradient(
                    0, -mInnerRadius,
                    0, -(mInnerRadius + front),
                    new int[]{
                            Color.parseColor("#FFFFFF"),
                            Color.parseColor("#6FD0FF"),
                            Color.parseColor("#B56CFF")
                    },
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            ));

            canvas.drawLine(
                    0, -mInnerRadius,
                    0, -(mInnerRadius + front),
                    mFrontPaint
            );

            canvas.restore();
        }

        invalidate();
    }
}
