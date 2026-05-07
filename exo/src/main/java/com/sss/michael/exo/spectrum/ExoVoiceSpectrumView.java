package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.util.ExoDensityUtil;

/**
 * 轻量级语音频谱控件。
 * <p>
 * 该控件使用 4 根竖向细线模拟常见的语音输入/语音播放指示器。
 * 四根线以控件垂直中心线为基准向上下两侧对称绘制，整体风格偏轻量、紧凑。
 * <p>
 * 当前版本优先消费 {@link #onFFTReady(int, int, float[])} 回调中的原始 FFT 结果，
 * 会先把复数频谱转换为幅度，再映射到 4 个更贴近人声的频带。
 * 这样可以让每一根线都对应实际频段，而不是简单地把原数组平均切成 4 段。
 * <p>
 * 控件还带有两个关键特性：
 * 1. 静默或短暂断帧时，不会直接闪空，而是进入缓慢衰减。
 * 2. 线条回落使用带阻尼的速度模型，而不是简单插值到 0。
 */
public class ExoVoiceSpectrumView extends View implements IExoFFTCallBack {

    /**
     * 固定绘制 4 根语音条。
     */
    private static final int BAR_COUNT = 4;

    /**
     * 数据超时时间，单位毫秒。
     * <p>
     * 超过该时间仍未收到新的音频数据时，控件不会立刻清空，
     * 而是开始进入静默衰减阶段。
     */
    private static final long DATA_TIMEOUT_MS = 320L;

    /**
     * 判断有效数据的最小阈值。
     * <p>
     * 低于该阈值的值被视为无效或近似静音。
     */
    private static final float DATA_EPSILON = 0.0001F;

    /**
     * 有效语音状态下的最低目标高度。
     * <p>
     * 用于避免有效语音存在时柱子过于“瘦弱”。
     */
    private static final float MIN_LIVE_LEVEL = 0.1F;

    /**
     * 整体能量增益。
     * <p>
     * 值越大，小音量越容易被抬高到可见区间。
     */
    private static final float LEVEL_GAIN = 1.02F;

    /**
     * 能量曲线指数。
     * <p>
     * 大于 1 时会轻微压制低能量段，小于 1 时会放大小能量段。
     */
    private static final float LEVEL_GAMMA = 1.072F;

    /**
     * FFT 幅度的对数压缩因子。
     * <p>
     * 用于把动态范围较大的原始 FFT 幅度压缩到更适合 UI 动效的区间。
     */
    private static final float FFT_LOG_K = 0.18F;

    /**
     * 四根语音条对应的人声频带左边界，单位 Hz。
     */
    private static final float[] VOICE_BAND_START_HZ = new float[]{110F, 260F, 520F, 1200F};

    /**
     * 四根语音条对应的人声频带右边界，单位 Hz。
     */
    private static final float[] VOICE_BAND_END_HZ = new float[]{360F, 780F, 1800F, 4200F};

    /**
     * 每个频带的增益系数。
     * <p>
     * 右侧高频条适当给一点补偿，避免高频尾部太“死”。
     */
    private static final float[] VOICE_BAND_GAIN = new float[]{1.00F, 1.08F, 1.16F, 1.26F};

    /**
     * 归一化峰值衰减系数。
     * <p>
     * 用于让每一帧的归一化参考值平滑下降，减少“忽大忽小”的闪烁感。
     */
    private static final float NORMALIZATION_PEAK_DECAY = 0.92F;

    /**
     * 静默阶段回落速度相对正常回落速度的比例。
     */
    private static final float SILENCE_FALL_RATIO = 0.84F;

    /**
     * 语音条画笔。
     * <p>
     * 使用描边方式绘制竖线，并通过圆头端点模拟更轻巧的语音指示器视觉。
     */
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 每根语音条的目标高度，范围 0~1。
     */
    private final float[] targetLevels = new float[BAR_COUNT];

    /**
     * 每根语音条当前实际绘制高度，范围 0~1。
     */
    private final float[] drawLevels = new float[BAR_COUNT];

    private final float[] fallVelocities = new float[BAR_COUNT];

    private float normalizationPeak;

    /**
     * 回落速度。
     * <p>
     * 值越大，线条失去能量后下降得越快。
     */
    private float fallSpeed = 0.0150F;

    /**
     * 阻尼速度。
     * <p>
     * 越接近 1，回落拖尾越明显。
     */
    private float fallDamping = 0.85F;

    /**
     * 死区阈值。
     * <p>
     * 低于该阈值的微小高度直接视为不可见，用于抑制抖动和残影。
     */
    private float deadZone = 0.001F;

    /**
     * 语音条颜色。
     */
    private int barColor = Color.parseColor("#E9302d");

    /**
     * 单根语音条线宽，单位 px。
     */
    private float barWidth;

    /**
     * 相邻语音条的水平间距，单位 px。
     */
    private float barSpacing;

    /**
     * 最近一次收到有效数据的时间。
     */
    private long lastUpdateTimeMs;

    /**
     * 当前是否仍处于“有内容可绘制”的状态。
     * <p>
     * 即使输入刚进入静默，只要 drawLevels 还有可见高度，也会继续保持 true，
     * 直到衰减完成。
     */
    private boolean hasLiveSpectrumData;

    /**
     * 当前是否处于暂停冻结状态。
     * <p>
     * 暂停后保留当前帧形态，不继续消费新的输入数据。
     */
    private boolean spectrumPaused;

    public ExoVoiceSpectrumView(Context context) {
        this(context, null);
    }

    public ExoVoiceSpectrumView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoVoiceSpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        barWidth = ExoDensityUtil.dp2px(context, 3F);
        barSpacing = ExoDensityUtil.dp2px(context, 3F);

        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setStrokeWidth(barWidth);
        barPaint.setColor(barColor);
    }

    /**
     * 设置语音条颜色。
     *
     * @param color ARGB 颜色值
     */
    public void setBarColor(int color) {
        barColor = color;
        barPaint.setColor(color);
        invalidate();
    }

    /**
     * 设置单根语音条线宽。
     *
     * @param widthPx 线宽，单位 px
     */
    public void setBarWidth(float widthPx) {
        barWidth = Math.max(1F, widthPx);
        barPaint.setStrokeWidth(barWidth);
        invalidate();
    }

    /**
     * 设置相邻语音条间距。
     *
     * @param spacingPx 间距，单位 px
     */
    public void setBarSpacing(float spacingPx) {
        barSpacing = Math.max(0F, spacingPx);
        invalidate();
    }

    /**
     * 设置回落速度。
     *
     * @param speed 回落加速度，值越大下降越快
     */
    public void setFallSpeed(float speed) {
        fallSpeed = clamp(speed, 0.0005F, 0.05F);
    }

    /**
     * 设置阻尼速度。
     *
     * @param damping 阻尼系数，越接近 1 拖尾越明显
     */
    public void setFallDamping(float damping) {
        fallDamping = clamp(damping, 0.50F, 0.98F);
    }

    /**
     * 设置死区阈值。
     *
     * @param threshold 微小高度忽略阈值
     */
    public void setDeadZone(float threshold) {
        deadZone = clamp(threshold, 0.001F, 0.08F);
    }

    /**
     * 一次性设置语音条回落手感。
     *
     * @param speed     回落速度
     * @param damping   阻尼速度
     * @param threshold 死区阈值
     */
    public void setDynamics(float speed, float damping, float threshold) {
        setFallSpeed(speed);
        setFallDamping(damping);
        setDeadZone(threshold);
    }

    /**
     * 直接设置一组语音能量数据。
     * <p>
     * 适合录音音量、业务层自定义能量、或外部已做过预处理的实时音量数组。
     *
     * @param levels 输入能量数组
     */
    public void setVoiceLevels(float[] levels) {
        if (spectrumPaused) {
            return;
        }
        if (!hasDrawableSpectrumData(levels)) {
            beginSilenceDecay();
            postInvalidateOnAnimation();
            return;
        }
        buildTargetLevels(levels);
        hasLiveSpectrumData = true;
        lastUpdateTimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    /**
     * 清空当前频谱状态。
     * <p>
     * 该方法会重置目标高度、当前绘制高度、回落速度和归一化参考峰值。
     */
    public void clearSpectrumData() {
        for (int index = 0; index < BAR_COUNT; index++) {
            targetLevels[index] = 0F;
            drawLevels[index] = 0F;
            fallVelocities[index] = 0F;
        }
        normalizationPeak = 0F;
        hasLiveSpectrumData = false;
        lastUpdateTimeMs = 0L;
    }

    /**
     * 进入静默衰减状态。
     * <p>
     * 与“立即清空”不同，这里只把目标值推向 0，
     * 让当前已绘制出来的柱子按阻尼模型慢慢落下。
     */
    private void beginSilenceDecay() {
        for (int index = 0; index < BAR_COUNT; index++) {
            targetLevels[index] = 0F;
        }
        if (hasVisibleLevels(drawLevels)) {
            hasLiveSpectrumData = true;
            lastUpdateTimeMs = SystemClock.uptimeMillis();
        } else {
            clearSpectrumData();
        }
    }

    /**
     * 设置是否暂停频谱绘制。
     *
     * @param paused true 表示冻结当前帧，false 表示恢复实时绘制
     */
    public void setSpectrumPaused(boolean paused) {
        if (spectrumPaused == paused) {
            return;
        }
        spectrumPaused = paused;
        if (!spectrumPaused && hasLiveSpectrumData) {
            lastUpdateTimeMs = SystemClock.uptimeMillis();
        }
        postInvalidateOnAnimation();
    }

    /**
     * 暂停并保持当前波形帧。
     */
    public void pauseSpectrum() {
        setSpectrumPaused(true);
    }

    /**
     * 恢复实时频谱绘制。
     */
    public void resumeSpectrum() {
        setSpectrumPaused(false);
    }

    /**
     * 获取当前是否处于暂停状态。
     */
    public boolean isSpectrumPaused() {
        return spectrumPaused;
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
        if (spectrumPaused) {
            return;
        }
        if (!hasDrawableFftData(fft)) {
            return;
        }
        buildTargetLevelsFromFft(sampleRateHz, channelCount, fft);
        hasLiveSpectrumData = true;
        lastUpdateTimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        // 当前控件优先使用原始 FFT 数据，不消费 magnitude 回调。
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearSpectrumData();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        long nowMs = SystemClock.uptimeMillis();
        if (!hasLiveSpectrumData || lastUpdateTimeMs == 0L) {
            return;
        }

        if (spectrumPaused) {
            drawBars(canvas);
            return;
        }

        boolean silenceDecay = nowMs - lastUpdateTimeMs > DATA_TIMEOUT_MS;
        boolean moving = updateDrawLevels(silenceDecay);

        if (hasVisibleLevels(drawLevels)) {
            drawBars(canvas);
        }

        if (moving) {
            postInvalidateOnAnimation();
        } else if (!hasVisibleLevels(drawLevels)) {
            clearSpectrumData();
        }
    }

    /**
     * 更新当前绘制高度。
     * <p>
     * 上升阶段使用较快追赶，让输入变化能快速反映到 UI；
     * 下降阶段则使用“加速度 + 阻尼”的方式下落，形成更自然的回落尾巴。
     *
     * @param silenceDecay 是否已经进入静默衰减阶段
     * @return true 表示当前仍有明显动画位移，需要继续请求下一帧
     */
    private boolean updateDrawLevels(boolean silenceDecay) {
        boolean moving = false;
        for (int index = 0; index < BAR_COUNT; index++) {
            float target = silenceDecay ? 0F : targetLevels[index];

            if (target >= drawLevels[index]) {
                drawLevels[index] += (target - drawLevels[index]) * 0.62F;
                fallVelocities[index] = 0F;
            } else {
                float acceleration = silenceDecay ? fallSpeed * SILENCE_FALL_RATIO : fallSpeed;
                fallVelocities[index] = (fallVelocities[index] + acceleration) * fallDamping;
                drawLevels[index] = Math.max(target, drawLevels[index] - fallVelocities[index]);
            }

            drawLevels[index] = clamp(drawLevels[index], 0F, 1F);

            if (drawLevels[index] <= deadZone && target <= DATA_EPSILON) {
                drawLevels[index] = 0F;
                fallVelocities[index] = 0F;
            }

            if (Math.abs(target - drawLevels[index]) > 0.002F
                    || drawLevels[index] > deadZone
                    || fallVelocities[index] > 0.001F) {
                moving = true;
            }
        }
        return moving;
    }

    /**
     * 绘制 4 根中心对齐的语音条。
     *
     * @param canvas 当前画布
     */
    private void drawBars(Canvas canvas) {
        float totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * barSpacing;
        float startX = (getWidth() - totalWidth) * 0.5F + barWidth * 0.5F;
        float centerY = getHeight() * 0.5F;
        float maxBarHeight = Math.max(1F, getHeight() - barWidth);

        for (int index = 0; index < BAR_COUNT; index++) {
            float level = drawLevels[index];
            if (level <= DATA_EPSILON) {
                continue;
            }
            float x = startX + index * (barWidth + barSpacing);
            float halfHeight = maxBarHeight * resolveBarProfile(index) * level * 0.5F;
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, barPaint);
        }
    }

    /**
     * 将普通能量数组压缩为 4 根语音条目标高度。
     *
     * @param levels 原始能量数组
     */
    private void buildTargetLevels(float[] levels) {
        float maxValue = 0F;
        for (float level : levels) {
            maxValue = Math.max(maxValue, Math.max(0F, level));
        }
        if (maxValue <= DATA_EPSILON) {
            clearSpectrumData();
            return;
        }

        float[] rawLevels = new float[BAR_COUNT];
        for (int index = 0; index < BAR_COUNT; index++) {
            int start = Math.min(levels.length - 1, Math.max(0, Math.round(levels.length * resolveRangeStartRatio(index))));
            int end = Math.min(levels.length, Math.max(start + 1, Math.round(levels.length * resolveRangeEndRatio(index))));
            float sum = 0F;
            float localMax = 0F;
            for (int dataIndex = start; dataIndex < end && dataIndex < levels.length; dataIndex++) {
                float value = Math.max(0F, levels[dataIndex]);
                sum += value;
                localMax = Math.max(localMax, value);
            }
            float average = sum / Math.max(1, end - start);
            float normalized = (average * 0.30F + localMax * 0.70F) / maxValue;
            rawLevels[index] = (float) Math.pow(
                    clamp(normalized * LEVEL_GAIN * resolveBandGain(index), 0F, 1F),
                    LEVEL_GAMMA
            );
        }

        for (int index = 0; index < BAR_COUNT; index++) {
            float blend = rawLevels[index];
            if (index > 0) {
                blend = blend * 0.82F + rawLevels[index - 1] * 0.18F;
            }
            if (index < BAR_COUNT - 1) {
                blend = blend * 0.90F + rawLevels[index + 1] * 0.10F;
            }
            targetLevels[index] = clamp(MIN_LIVE_LEVEL + blend * (1F - MIN_LIVE_LEVEL), 0F, 1F);
        }
    }

    /**
     * 根据原始 FFT 数据构建 4 根语音条目标高度。
     * <p>
     * 输入数据格式为复数频谱交错数组：
     * `fft[2i]` 是实部，`fft[2i+1]` 是虚部。
     *
     * @param sampleRateHz 采样率
     * @param channelCount 声道数
     * @param fft          原始 FFT 数据
     */
    private void buildTargetLevelsFromFft(int sampleRateHz, int channelCount, float[] fft) {
        int fftSize = fft != null ? fft.length : 0;
        int binCount = fftSize / 2;
        if (sampleRateHz <= 0 || channelCount <= 0 || binCount <= 0) {
            clearSpectrumData();
            return;
        }

        float[] magnitudes = new float[binCount];
        float amplitudeScale = 2F / Math.max(1, fftSize);
        float maxVoiceBandValue = 0F;
        float maxTrackedHz = Math.min(sampleRateHz * 0.5F, VOICE_BAND_END_HZ[VOICE_BAND_END_HZ.length - 1]);
        float binWidthHz = sampleRateHz / (float) fftSize;

        for (int binIndex = 0; binIndex < binCount; binIndex++) {
            int realIndex = binIndex << 1;
            int imaginaryIndex = realIndex + 1;
            if (imaginaryIndex >= fft.length) {
                break;
            }
            float real = fft[realIndex];
            float imaginary = fft[imaginaryIndex];
            float magnitude = (float) Math.sqrt(real * real + imaginary * imaginary) * amplitudeScale;
            magnitude = magnitude / (magnitude + FFT_LOG_K);
            magnitudes[binIndex] = magnitude;

            float frequencyHz = binIndex * binWidthHz;
            if (frequencyHz <= maxTrackedHz) {
                maxVoiceBandValue = Math.max(maxVoiceBandValue, magnitude);
            }
        }

        if (maxVoiceBandValue <= DATA_EPSILON) {
            return;
        }

        if (maxVoiceBandValue > normalizationPeak) {
            normalizationPeak = maxVoiceBandValue;
        } else {
            normalizationPeak = Math.max(maxVoiceBandValue, normalizationPeak * NORMALIZATION_PEAK_DECAY);
        }
        float stablePeak = Math.max(normalizationPeak, DATA_EPSILON);

        float[] rawLevels = new float[BAR_COUNT];
        for (int index = 0; index < BAR_COUNT; index++) {
            int startBin = frequencyToBin(VOICE_BAND_START_HZ[index], binWidthHz, binCount);
            int endBin = frequencyToBin(VOICE_BAND_END_HZ[index], binWidthHz, binCount);
            if (endBin < startBin) {
                endBin = startBin;
            }

            float sum = 0F;
            float localMax = 0F;
            int sampleCount = 0;
            for (int binIndex = startBin; binIndex <= endBin && binIndex < binCount; binIndex++) {
                float magnitude = magnitudes[binIndex];
                sum += magnitude;
                localMax = Math.max(localMax, magnitude);
                sampleCount++;
            }

            float average = sum / Math.max(1, sampleCount);
            float normalized = (average * 0.40F + localMax * 0.60F) / stablePeak;
            rawLevels[index] = (float) Math.pow(
                    clamp(normalized * LEVEL_GAIN * VOICE_BAND_GAIN[index], 0F, 1F),
                    LEVEL_GAMMA
            );
        }

        for (int index = 0; index < BAR_COUNT; index++) {
            float blended = rawLevels[index];
            if (index > 0) {
                blended = blended * 0.84F + rawLevels[index - 1] * 0.16F;
            }
            if (index < BAR_COUNT - 1) {
                blended = blended * 0.92F + rawLevels[index + 1] * 0.08F;
            }
            float desiredLevel = clamp(MIN_LIVE_LEVEL + blended * (1F - MIN_LIVE_LEVEL), 0F, 1F);
            if (desiredLevel >= targetLevels[index]) {
                targetLevels[index] += (desiredLevel - targetLevels[index]) * 0.72F;
            } else {
                targetLevels[index] += (desiredLevel - targetLevels[index]) * 0.22F;
            }
        }
    }

    /**
     * 每根语音条的固定造型比例。
     * <p>
     * 左右两根较短，中间两根较高。
     */
    private float resolveBarProfile(int index) {
        switch (index) {
            case 0:
                return 0.58F;
            case 3:
                return 0.72F;
            default:
                return 1F;
        }
    }

    /**
     * 普通能量数组映射时，每根语音条的取样区间起始比例。
     */
    private float resolveRangeStartRatio(int index) {
        switch (index) {
            case 0:
                return 0.03F;
            case 1:
                return 0.16F;
            case 2:
                return 0.34F;
            default:
                return 0.52F;
        }
    }

    /**
     * 普通能量数组映射时，每根语音条的取样区间结束比例。
     */
    private float resolveRangeEndRatio(int index) {
        switch (index) {
            case 0:
                return 0.31F;
            case 1:
                return 0.52F;
            case 2:
                return 0.74F;
            default:
                return 0.90F;
        }
    }

    /**
     * 普通能量映射时，不同语音条的附加增益。
     */
    private float resolveBandGain(int index) {
        switch (index) {
            case 0:
                return 0.96F;
            case 1:
                return 1.04F;
            case 2:
                return 1.10F;
            default:
                return 1.18F;
        }
    }

    /**
     * 判断普通能量数组是否包含可绘制数据。
     */
    private boolean hasDrawableSpectrumData(float[] levels) {
        if (levels == null || levels.length == 0) {
            return false;
        }
        for (float level : levels) {
            if (level > DATA_EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据频率值换算对应的 FFT bin 下标。
     */
    private int frequencyToBin(float frequencyHz, float binWidthHz, int binCount) {
        if (binWidthHz <= 0F) {
            return 0;
        }
        return Math.max(0, Math.min(binCount - 1, Math.round(frequencyHz / binWidthHz)));
    }

    /**
     * 判断原始 FFT 数组是否包含有效频谱数据。
     */
    private boolean hasDrawableFftData(float[] fft) {
        if (fft == null || fft.length < 4) {
            return false;
        }
        for (float value : fft) {
            if (Math.abs(value) > DATA_EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前是否仍有肉眼可见的柱体高度。
     */
    private boolean hasVisibleLevels(float[] levels) {
        if (levels == null || levels.length == 0) {
            return false;
        }
        for (float level : levels) {
            if (level > deadZone) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将数值限制在指定区间内。
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
