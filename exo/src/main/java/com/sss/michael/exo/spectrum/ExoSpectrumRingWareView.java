package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.IExoFFTCallBack;

/**
 * @author Michael by SSS
 * @date 2026/1/5 0005 10:36
 * @Description 环形能量波纹
 */
public class ExoSpectrumRingWareView extends View implements IExoFFTCallBack {
    // 主体画笔（明亮实线）
    private Paint mainPaint;
    // 光晕画笔（模糊发光）
    private Paint glowPaint;
    // 圆环路径
    private Path ringPath;

    // 明亮的橙色核心
    private int mainColor = Color.parseColor("#FF7F27");
    // 深橙红色光晕
    private int glowColor = Color.parseColor("#FF4500");


    private float centerX, centerY;
    // 圆环最小半径（静止时）
    private float minRadius;
    // 最大跳动范围
    private float maxWaveRange;

    // 目标振幅（当前音频帧的平均音量）
    private float targetAmplitude = 0f;
    // 当前平滑后的振幅
    private float currentAmplitude = 0f;
    // 插值系数：控制跳动和回落的速度。越大反应越快，越小越平滑。
    private float lerpFactorUp = 0.3f;    // 上升速度快 (爆发感)
    private float lerpFactorDown = 0.1f;  // 下降速度慢 (余韵感)

    // 记录每帧FFT幅度，用于圆环折线振幅
    private float[] latestMagnitude;

    public ExoSpectrumRingWareView(Context context) {
        this(context, null);
    }

    public ExoSpectrumRingWareView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoSpectrumRingWareView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPath = new Path();

        mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setStyle(Paint.Style.STROKE);
        mainPaint.setColor(mainColor);
        mainPaint.setStrokeWidth(5f); // 核心线条宽度
        mainPaint.setStrokeCap(Paint.Cap.ROUND);
        mainPaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setColor(glowColor);
        glowPaint.setStrokeWidth(55f); // 光晕宽度
        glowPaint.setMaskFilter(new BlurMaskFilter(100f, BlurMaskFilter.Blur.NORMAL));
        glowPaint.setAlpha(180);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        int minSide = Math.min(w, h);
        // 设置基础半径和最大跳动范围
        minRadius = minSide * 0.25f;
        maxWaveRange = minSide * 0.15f;
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        if (magnitude == null || magnitude.length == 0) return;

        latestMagnitude = magnitude.clone();

        float sum = 0;
        for (float v : magnitude) sum += v;
        float avg = sum / magnitude.length;

        targetAmplitude = (float) (Math.log10(1 + avg * 50) * maxWaveRange);

        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (targetAmplitude > currentAmplitude) {
            // 上升阶段：快速爆发
            currentAmplitude += (targetAmplitude - currentAmplitude) * lerpFactorUp;
        } else {
            // 下降阶段：缓慢回落
            currentAmplitude -= (currentAmplitude - targetAmplitude) * lerpFactorDown;
        }

        float currentDynamicRadius = minRadius + currentAmplitude;


        float glowRadiusOffset = currentAmplitude * 0.5f; // 光晕半径扩张幅度

        buildEnergyRingPath(currentDynamicRadius + glowRadiusOffset);

        int baseAlpha = 180;
        int dynamicAlpha = (int) (baseAlpha + (255 - baseAlpha) * Math.min(currentAmplitude / maxWaveRange, 1f));
        glowPaint.setAlpha(dynamicAlpha);

        canvas.drawPath(ringPath, glowPaint);

        buildEnergyRingPath(currentDynamicRadius);
        canvas.drawPath(ringPath, mainPaint);

        // 如果还有动画未完成，持续刷新
        if (currentAmplitude > 0.1f || Math.abs(targetAmplitude - currentAmplitude) > 0.1f) {
            postInvalidateOnAnimation();
        }
    }

    private void buildEnergyRingPath(float baseRadius) {
        if (latestMagnitude == null || latestMagnitude.length == 0) return;
        ringPath.reset();

        int segmentCount = latestMagnitude.length; // 圆环分段数
        double angleStep = 2 * Math.PI / segmentCount;

        for (int i = 0; i < segmentCount; i++) {
            double angle = i * angleStep;

            // FFT映射索引
            int magIndex = i * latestMagnitude.length / segmentCount;

            // 中频增强 / 非线性映射
            float energy = latestMagnitude[magIndex];
            float freqFactor = (float) Math.sqrt((magIndex + 1) / (float) latestMagnitude.length);
            energy *= freqFactor;

            // 放大偏移，让波纹明显
            float energyOffset = energy * maxWaveRange *1.5f;

            float r = baseRadius + energyOffset;

            // 极坐标转笛卡尔坐标
            float x = centerX + (float) (r * Math.cos(angle));
            float y = centerY + (float) (r * Math.sin(angle));

            if (i == 0) ringPath.moveTo(x, y);
            else ringPath.lineTo(x, y);
        }
        ringPath.close();
    }


    /**
     * 允许外部修改颜色
     */
    public void setColors(int mainColor, int glowColor) {
        this.mainColor = mainColor;
        this.glowColor = glowColor;
        mainPaint.setColor(mainColor);
        glowPaint.setColor(glowColor);
        postInvalidate();
    }
}