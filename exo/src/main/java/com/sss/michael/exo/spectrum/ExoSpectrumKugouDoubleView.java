package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.util.ExoDensityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by SSS
 * @date 2026/5/6 0007 15:05
 * @Description 细条酷狗风格频谱
 */
public class ExoSpectrumKugouDoubleView extends View implements IExoFFTCallBack {

    /**
     * 细竖柱画笔。
     */
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 顶部包络曲线画笔。
     */
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 顶部包络曲线路径。
     */
    private final Path curvePath = new Path();

    /**
     * 后景参差层顶部包络曲线路径。
     */
    private final Path rearCurvePath = new Path();

    /**
     * 复用的曲线点位，避免每帧重复创建过多对象。
     */
    private final List<PointF> curvePoints = new ArrayList<>();

    /**
     * 后景参差层复用的曲线点位。
     */
    private final List<PointF> rearCurvePoints = new ArrayList<>();

    /**
     * 实时频谱目标高度，来自播放器 SDK 回调。
     */
    private float[] targetLevels = new float[0];

    /**
     * 后景参差层的实时频谱目标高度，来自频谱数据后半段。
     */
    private float[] rearTargetLevels = new float[0];

    /**
     * 当前绘制高度，会平滑追赶 targetLevels。
     */
    private float[] drawLevels = new float[0];

    /**
     * 后景参差层当前绘制高度。
     */
    private float[] rearDrawLevels = new float[0];

    /**
     * 峰值拖尾数据，让频谱下降更柔和。
     */
    private float[] peakLevels = new float[0];

    /**
     * 后景参差层峰值拖尾数据。
     */
    private float[] rearPeakLevels = new float[0];

    /**
     * 横向平滑后的包络高度，用来消除频谱单点抖动。
     */
    private float[] envelopeLevels = new float[0];

    /**
     * 后景参差层横向平滑后的包络高度。
     */
    private float[] rearEnvelopeLevels = new float[0];

    /**
     * 包络平滑过程中的临时缓冲。
     */
    private float[] smoothScratchLevels = new float[0];

    /**
     * 后景参差层包络平滑过程中的临时缓冲。
     */
    private float[] rearSmoothScratchLevels = new float[0];

    /**
     * 原始频谱数据的降落阻尼，越接近 1 拖尾越长。
     */
    private float fallDamping = 0.39F;

    /**
     * 顶部包络曲线圆润度，0 保留更多细节，1 尽量圆润平滑。
     */
    private float curveSmoothness = 1F;

    /**
     * 前后双段参差强度，0 表示关闭后景错位层，1 表示后景更明显。
     */
    private float staggerIntensity = 0.62F;

    /**
     * 频谱高度放大倍率。
     */
    private float spectrumAmplification = 20F;

    /**
     * 目标刷新帧率。
     */
    private int targetFrameRate = 60;

    /**
     * 目标刷新间隔。
     */
    private long frameIntervalMs = 1000L / 30L;

    /**
     * 上一次接收频谱数据的时间。
     */
    private long lastFrameUpdateTimeMs;

    /**
     * View 开始动画的时间，用于 idle 呼吸效果。
     */
    private long animationStartTimeMs;
    /**
     * 当前是否已经收到有效音频频谱。
     */
    private boolean hasLiveSpectrumData;

    /**
     * 频谱横向渐变。
     */
    private LinearGradient spectrumGradient;

    public ExoSpectrumKugouDoubleView(Context context) {
        this(context, null);
    }

    public ExoSpectrumKugouDoubleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoSpectrumKugouDoubleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.BUTT);

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);

    }

    /**
     * 设置频谱高度放大倍率。
     *
     * @param amplification 放大倍率，1 表示默认高度
     */
    public void setSpectrumAmplification(float amplification) {
        spectrumAmplification = clamp(amplification, 0.1F, 20F);
        invalidate();
    }

    /**
     * 设置频谱下降阻尼。
     *
     * @param damping 阻尼值，0 表示快速回落，0.98 表示拖尾很长
     */
    public void setSpectrumFallDamping(float damping) {
        fallDamping = clamp(damping, 0F, 0.98F);
    }

    /**
     * 设置顶部包络曲线圆润度。
     *
     * @param smoothness 圆润度，0 表示更跟手，1 表示更平滑圆润
     */
    public void setSpectrumCurveSmoothness(float smoothness) {
        curveSmoothness = clamp(smoothness, 0F, 1F);
        invalidate();
    }

    /**
     * 设置酷狗风格前后双段参差强度。
     *
     * @param intensity 参差强度，0 表示关闭，1 表示后景错位层更明显
     */
    public void setSpectrumStaggerIntensity(float intensity) {
        staggerIntensity = clamp(intensity, 0F, 1F);
        invalidate();
    }

    /**
     * 设置频谱目标刷新帧率。
     *
     * @param frameRate 目标 FPS
     */
    public void setSpectrumFrameRate(int frameRate) {
        targetFrameRate = Math.max(1, Math.min(frameRate, 120));
        frameIntervalMs = Math.max(1L, 1000L / targetFrameRate);
    }

    /**
     * 一次性设置频谱调参项。
     */
    public void setSpectrumTuning(float amplification, float damping, int frameRate) {
        setSpectrumAmplification(amplification);
        setSpectrumFallDamping(damping);
        setSpectrumFrameRate(frameRate);
    }

    /**
     * 一次性设置频谱调参项，包含顶部曲线圆润度。
     */
    public void setSpectrumTuning(float amplification, float damping, int frameRate, float curveSmoothness) {
        setSpectrumTuning(amplification, damping, frameRate);
        setSpectrumCurveSmoothness(curveSmoothness);
    }

    /**
     * 一次性设置频谱调参项，包含顶部曲线圆润度和前后参差强度。
     */
    public void setSpectrumTuning(
            float amplification,
            float damping,
            int frameRate,
            float curveSmoothness,
            float staggerIntensity
    ) {
        setSpectrumTuning(amplification, damping, frameRate, curveSmoothness);
        setSpectrumStaggerIntensity(staggerIntensity);
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {

    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        if (magnitude == null || magnitude.length == 0) {
            hasLiveSpectrumData = false;
            postInvalidateOnAnimation();
            return;
        }
        long nowMs = SystemClock.uptimeMillis();
        if (lastFrameUpdateTimeMs > 0L && nowMs - lastFrameUpdateTimeMs < frameIntervalMs) {
            return;
        }
        lastFrameUpdateTimeMs = nowMs;
        ensureLevelCapacity(resolveBarCount(getWidth()));
        buildTargetLevels(magnitude);
        hasLiveSpectrumData = true;
        postInvalidateOnAnimation();
    }

    /**
     * 清空实时频谱数据，绘制时回到 idle 呼吸波形。
     */
    private void clearSpectrumData() {
        targetLevels = new float[0];
        rearTargetLevels = new float[0];
        drawLevels = new float[0];
        rearDrawLevels = new float[0];
        peakLevels = new float[0];
        rearPeakLevels = new float[0];
        envelopeLevels = new float[0];
        rearEnvelopeLevels = new float[0];
        smoothScratchLevels = new float[0];
        rearSmoothScratchLevels = new float[0];
        hasLiveSpectrumData = false;
        lastFrameUpdateTimeMs = 0L;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        clearSpectrumData();
        animationStartTimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        hasLiveSpectrumData = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        spectrumGradient = new LinearGradient(
                0F,
                0F,
                Math.max(1F, w),
                0F,
                new int[]{0xFF64DDF2, 0xFF8BB7F2, 0xFFC48BE8, 0xFFF06AAE},
                new float[]{0F, 0.38F, 0.68F, 1F},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(spectrumGradient);
        curvePaint.setShader(spectrumGradient);
        ensureLevelCapacity(resolveBarCount(w));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        drawThinKugouSpectrum(canvas);
        postInvalidateOnAnimation();
    }

    /**
     * 绘制 GIF 里那种细条横向酷狗频谱。
     */
    private void drawThinKugouSpectrum(Canvas canvas) {
        int barCount = resolveBarCount(getWidth());
        ensureLevelCapacity(barCount);

        long nowMs = SystemClock.uptimeMillis();
        boolean liveDataExpired = lastFrameUpdateTimeMs == 0L || nowMs - lastFrameUpdateTimeMs > 260L;
        boolean useIdle = !hasLiveSpectrumData || liveDataExpired;
        updateDrawLevels(nowMs, useIdle);
        updateSmoothEnvelopeLevels(drawLevels, envelopeLevels, smoothScratchLevels);
        updateSmoothEnvelopeLevels(rearDrawLevels, rearEnvelopeLevels, rearSmoothScratchLevels);

        float horizontalPadding = Math.max(ExoDensityUtil.dp2px(getContext(), 5F), getWidth() * 0.012F);
        float left = horizontalPadding;
        float right = getWidth() - horizontalPadding;
        float width = Math.max(1F, right - left);
        float step = width / Math.max(1, barCount - 1);
        float barWidth = Math.max(1F, Math.min(ExoDensityUtil.dp2px(getContext(), 1.2F), step * 0.42F));
        float bottomPadding = Math.max(ExoDensityUtil.dp2px(getContext(), 1F), barWidth * 0.5F);
        float baseY = getHeight() - bottomPadding;
        float maxAmplitude = Math.max(ExoDensityUtil.dp2px(getContext(), 4F), baseY - bottomPadding);

        barPaint.setStrokeWidth(barWidth);
        curvePaint.setStrokeWidth(Math.max(1F, barWidth * 1.18F));

        drawStaggeredRearBars(canvas, left, step, barCount, barWidth, baseY, maxAmplitude);

        curvePoints.clear();
        barPaint.setAlpha(255);
        for (int index = 0; index < barCount; index++) {
            float x = left + step * index;
            float top = baseY - maxAmplitude * envelopeLevels[index];
            canvas.drawLine(x, baseY, x, top, barPaint);
            curvePoints.add(new PointF(x, top));
        }

        buildCurvePath();
        canvas.drawPath(curvePath, curvePaint);

    }

    /**
     * 绘制半步错位的后景竖条，形成酷狗式前后双段参差层次。
     */
    private void drawStaggeredRearBars(
            Canvas canvas,
            float left,
            float step,
            int barCount,
            float barWidth,
            float baseY,
            float maxAmplitude
    ) {
        if (staggerIntensity <= 0F || barCount < 2) {
            return;
        }
        int savedAlpha = barPaint.getAlpha();
        float savedStrokeWidth = barPaint.getStrokeWidth();
        barPaint.setAlpha(Math.round(70F + staggerIntensity * 70F));
        barPaint.setStrokeWidth(Math.max(1F, barWidth * 0.72F));
        rearCurvePoints.clear();
        for (int index = 0; index < barCount - 1; index++) {
            float x = left + step * (index + 0.5F);
            float mixedEnvelope = (rearEnvelopeLevels[index] + rearEnvelopeLevels[index + 1]) * 0.5F;
            float heightScale = resolveRearStaggerHeightScale(index);
            float top = baseY - maxAmplitude * clamp(mixedEnvelope * heightScale, 0F, 1F);
            canvas.drawLine(x, baseY, x, top, barPaint);
            rearCurvePoints.add(new PointF(x, top));
        }
        int savedCurveAlpha = curvePaint.getAlpha();
        float savedCurveStrokeWidth = curvePaint.getStrokeWidth();
        curvePaint.setAlpha(Math.round(95F + staggerIntensity * 65F));
        curvePaint.setStrokeWidth(Math.max(1F, barWidth * 0.88F));
        buildCurvePath(rearCurvePoints, rearCurvePath);
        canvas.drawPath(rearCurvePath, curvePaint);
        curvePaint.setStrokeWidth(savedCurveStrokeWidth);
        curvePaint.setAlpha(savedCurveAlpha);
        barPaint.setStrokeWidth(savedStrokeWidth);
        barPaint.setAlpha(savedAlpha);
    }

    /**
     * 将原始频谱数组压缩为当前 View 宽度需要的柱子数量。
     */
    /**
     * 计算后景层高度缩放；圆润度越高，额外参差越弱，前后两段都更服从平滑包络。
     */
    private float resolveRearStaggerHeightScale(int index) {
        float staggerWave = 0.5F + 0.5F * (float) Math.sin(index * 0.83F + rearEnvelopeLevels[index] * 7.5F);
        float staggerDetail = (1F - curveSmoothness) * staggerWave * 0.12F;
        float baseScale = 0.76F + staggerIntensity * 0.16F;
        return baseScale + staggerDetail;
    }

    private void buildTargetLevels(float[] spectrumData) {
        int splitIndex = Math.max(1, spectrumData.length / 2);
        buildTargetLevelsFromRange(spectrumData, 0, splitIndex, targetLevels);
        buildTargetLevelsFromRange(spectrumData, splitIndex, spectrumData.length, rearTargetLevels);
    }

    /**
     * 将指定区间的频谱数据压缩到目标绘制数组。
     */
    private void buildTargetLevelsFromRange(float[] spectrumData, int rangeStart, int rangeEnd, float[] outputLevels) {
        int safeRangeEnd = Math.max(rangeStart + 1, rangeEnd);
        float maxValue = 0F;
        for (int dataIndex = rangeStart; dataIndex < safeRangeEnd && dataIndex < spectrumData.length; dataIndex++) {
            float value = spectrumData[dataIndex];
            maxValue = Math.max(maxValue, Math.max(0F, value));
        }
        if (maxValue <= 0.0001F) {
            for (int index = 0; index < outputLevels.length; index++) {
                outputLevels[index] = 0.035F;
            }
            return;
        }

        int rangeLength = Math.max(1, safeRangeEnd - rangeStart);
        for (int index = 0; index < outputLevels.length; index++) {
            int start = rangeStart + index * rangeLength / outputLevels.length;
            int end = rangeStart + Math.max(index * rangeLength / outputLevels.length + 1, (index + 1) * rangeLength / outputLevels.length);
            float sum = 0F;
            float localMax = 0F;
            for (int dataIndex = start; dataIndex < end && dataIndex < safeRangeEnd && dataIndex < spectrumData.length; dataIndex++) {
                float value = Math.max(0F, spectrumData[dataIndex]);
                sum += value;
                localMax = Math.max(localMax, value);
            }
            float average = sum / Math.max(1, end - start);
            float normalized = (average * 0.42F + localMax * 0.58F) / maxValue;
            normalized = (float) Math.sqrt(clamp(normalized * spectrumAmplification, 0F, 1F));
            float position = index / (float) Math.max(1, outputLevels.length - 1);
            float profile = resolveFrequencyProfile(position);
            outputLevels[index] = clamp(0.045F + normalized * profile * 0.90F, 0.035F, 1F);
        }
    }

    /**
     * 更新当前绘制高度：有实时数据时追赶目标，没有数据时生成轻微呼吸波。
     */
    private void updateDrawLevels(long nowMs, boolean useIdle) {
        float time = (nowMs - animationStartTimeMs) / 1000F;
        updateLevelGroup(targetLevels, drawLevels, peakLevels, time, useIdle, 0F);
        updateLevelGroup(rearTargetLevels, rearDrawLevels, rearPeakLevels, time, useIdle, 0.42F);
    }

    /**
     * 更新一组频谱高度，前景和后景分别维护，避免双段波形互相污染。
     */
    private void updateLevelGroup(float[] targets, float[] draws, float[] peaks, float time, boolean useIdle, float idlePhaseOffset) {
        for (int index = 0; index < draws.length; index++) {
            float target = useIdle ? resolveIdleLevel(index, draws.length, time + idlePhaseOffset) : targets[index];
            float speed = target > draws[index] ? 0.34F : 0.10F;
            draws[index] += (target - draws[index]) * speed;
            if (draws[index] > peaks[index]) {
                peaks[index] = draws[index];
            } else {
                peaks[index] = Math.max(draws[index], peaks[index] - (0.010F + (1F - fallDamping) * 0.018F));
            }
            draws[index] = clamp(draws[index] * 0.86F + peaks[index] * 0.14F, 0F, 1F);
        }
    }

    /**
     * 计算平滑包络，让顶部曲线呈现连续的大轮廓，避免单根频谱竖柱产生锯齿。
     */
    private void updateSmoothEnvelopeLevels(float[] sourceLevels, float[] outputLevels, float[] scratchLevels) {
        int levelCount = sourceLevels.length;
        if (levelCount == 0) {
            return;
        }
        System.arraycopy(sourceLevels, 0, scratchLevels, 0, levelCount);
        int blurRadius = 2 + Math.round(curveSmoothness * 12F);
        int smoothPassCount = 1 + Math.round(curveSmoothness * 2F);
        float sigma = 1.15F + curveSmoothness * 5.2F;
        for (int pass = 0; pass < smoothPassCount; pass++) {
            for (int index = 0; index < levelCount; index++) {
                float weightedSum = 0F;
                float weightTotal = 0F;
                for (int offset = -blurRadius; offset <= blurRadius; offset++) {
                    int sampleIndex = Math.max(0, Math.min(levelCount - 1, index + offset));
                    float normalizedOffset = offset / sigma;
                    float weight = (float) Math.exp(-normalizedOffset * normalizedOffset * 0.5F);
                    weightedSum += scratchLevels[sampleIndex] * weight;
                    weightTotal += weight;
                }
                outputLevels[index] = weightedSum / Math.max(0.0001F, weightTotal);
            }
            System.arraycopy(outputLevels, 0, scratchLevels, 0, levelCount);
        }
        float rawBlend = 1F - curveSmoothness;
        for (int index = 0; index < levelCount; index++) {
            outputLevels[index] = outputLevels[index] * curveSmoothness + sourceLevels[index] * rawBlend;
        }
    }

    /**
     * 构造平滑包络曲线，曲线会压住每根竖柱的顶部。
     */
    private void buildCurvePath() {
        buildCurvePath(curvePoints, curvePath);
    }

    /**
     * 构造指定点集的平滑包络曲线。
     */
    private void buildCurvePath(List<PointF> points, Path path) {
        path.reset();
        if (points.isEmpty()) {
            return;
        }
        path.moveTo(points.get(0).x, points.get(0).y);
        float tension = 0.12F + curveSmoothness * 0.24F;
        for (int index = 0; index < points.size() - 1; index++) {
            PointF previous = points.get(Math.max(0, index - 1));
            PointF current = points.get(index);
            PointF next = points.get(index + 1);
            PointF afterNext = points.get(Math.min(points.size() - 1, index + 2));
            float firstControlX = current.x + (next.x - previous.x) * tension;
            float firstControlY = current.y + (next.y - previous.y) * tension;
            float secondControlX = next.x - (afterNext.x - current.x) * tension;
            float secondControlY = next.y - (afterNext.y - current.y) * tension;
            path.cubicTo(firstControlX, firstControlY, secondControlX, secondControlY, next.x, next.y);
        }
    }

    /**
     * 根据控件宽度计算竖柱数量。
     */
    private int resolveBarCount(float width) {
        if (width <= 0F) {
            return 96;
        }
        return Math.max(48, Math.min(136, (int) (width / ExoDensityUtil.dp2px(getContext(), 3.2F))));
    }

    /**
     * 保证绘制数组容量和当前竖柱数量一致。
     */
    private void ensureLevelCapacity(int barCount) {
        if (targetLevels.length == barCount
                && rearTargetLevels.length == barCount
                && drawLevels.length == barCount
                && rearDrawLevels.length == barCount
                && peakLevels.length == barCount
                && rearPeakLevels.length == barCount
                && envelopeLevels.length == barCount
                && rearEnvelopeLevels.length == barCount
                && rearSmoothScratchLevels.length == barCount
                && smoothScratchLevels.length == barCount) {
            return;
        }
        targetLevels = new float[barCount];
        rearTargetLevels = new float[barCount];
        drawLevels = new float[barCount];
        rearDrawLevels = new float[barCount];
        peakLevels = new float[barCount];
        rearPeakLevels = new float[barCount];
        envelopeLevels = new float[barCount];
        rearEnvelopeLevels = new float[barCount];
        smoothScratchLevels = new float[barCount];
        rearSmoothScratchLevels = new float[barCount];
        for (int index = 0; index < barCount; index++) {
            float idle = resolveIdleLevel(index, barCount, 0F);
            float rearIdle = resolveIdleLevel(index, barCount, 0.42F);
            targetLevels[index] = idle;
            rearTargetLevels[index] = rearIdle;
            drawLevels[index] = idle;
            rearDrawLevels[index] = rearIdle;
            peakLevels[index] = idle;
            rearPeakLevels[index] = rearIdle;
            envelopeLevels[index] = idle;
            rearEnvelopeLevels[index] = rearIdle;
            smoothScratchLevels[index] = idle;
            rearSmoothScratchLevels[index] = rearIdle;
        }
    }

    /**
     * 空数据状态的默认波形：左侧有明显小峰，中后段保留轻微起伏。
     */
    private float resolveIdleLevel(int index, int count, float time) {
        float position = index / (float) Math.max(1, count - 1);
        float leftHump = gaussian(position, 0.16F, 0.18F);
        float middleHump = gaussian(position, 0.58F, 0.20F);
        float wave = 0.5F + 0.5F * (float) Math.sin(index * 0.14F + time * 1.25F);
        return clamp(0.035F + leftHump * 0.22F + middleHump * 0.11F + wave * 0.030F, 0.035F, 0.34F);
    }

    /**
     * 音频状态下的横向能量轮廓，让左中频更明显，右侧保留柔和尾波。
     */
    private float resolveFrequencyProfile(float position) {
        float lowMid = gaussian(position, 0.22F, 0.22F);
        float middle = gaussian(position, 0.58F, 0.24F);
        float rightTail = 1F - position * 0.38F;
        return clamp(0.30F + lowMid * 0.62F + middle * 0.24F + rightTail * 0.18F, 0.30F, 1F);
    }

    /**
     * 高斯函数，用于生成自然的波峰轮廓。
     */
    private float gaussian(float value, float center, float width) {
        float distance = (value - center) / Math.max(0.001F, width);
        return (float) Math.exp(-distance * distance * 2F);
    }

    /**
     * 限制数值范围。
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
