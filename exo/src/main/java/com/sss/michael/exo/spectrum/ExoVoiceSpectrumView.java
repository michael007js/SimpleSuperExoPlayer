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
 * @author Michael by SSS
 * @date 2026/5/7 0007 15:50
 * @Description 轻量级语音频谱控件
 * <p>
 * 控件用于绘制类似语音录制、语音播放、语音识别监听状态中的 4 根红色竖向跳动条。
 * 四根线条以控件中心为基准上下对称绘制，左右两根较短，中间两根较高，
 * 形成紧凑、清晰的小尺寸语音动效。
 * <p>
 * 控件遵循“无有效数据不绘制”的原则：当外部传入 null、空数组、全 0 数据，
 * 或超过 {@link #DATA_TIMEOUT_MS} 未收到新数据时，onDraw 会直接返回，
 * 不会绘制默认直线、默认波形或历史残影。
 */
public class ExoVoiceSpectrumView extends View implements IExoFFTCallBack {

    /**
     * 语音条数量。
     * <p>
     * 固定为 4 根，和目标视觉图保持一致；如果未来要扩展为更多条，
     * 需要同时调整 {@link #resolveBarProfile(int)} 的造型比例。
     */
    private static final int BAR_COUNT = 4;

    /**
     * 频谱数据有效期，单位毫秒。
     * <p>
     * 频谱数据通常按较高频率持续回调。如果超过该时间没有收到新数据，
     * 说明当前音频状态可能已经暂停、停止或回调中断，此时控件停止绘制，
     * 避免上一帧画面停留在界面上造成误解。
     */
    private static final long DATA_TIMEOUT_MS = 100L;

    /**
     * 有效能量阈值。
     * <p>
     * 小于该值的数据视为静音或无效噪声。该阈值用于判断是否需要绘制，
     * 也用于跳过接近 0 的单根语音条，避免静音时出现极短的底线。
     */
    private static final float DATA_EPSILON = 0.0001F;

    /**
     * 语音条画笔。
     * <p>
     * 使用 {@link Paint.Style#STROKE} 绘制竖线，并设置 {@link Paint.Cap#ROUND}
     * 形成圆头效果。相比绘制矩形，圆头线条在 10dp~20dp 这类小尺寸场景中
     * 更接近常见语音状态指示器的视觉风格。
     */
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * 目标高度数组，范围 0~1。
     * <p>
     * 外部传入的任意长度音频能量数组会被压缩成 4 段，分别写入该数组。
     * targetLevels 只表达“期望到达的高度”，真正绘制时会通过 drawLevels
     * 做插值追赶，从而获得平滑跳动效果。
     */
    private final float[] targetLevels = new float[BAR_COUNT];

    /**
     * 当前绘制高度数组，范围 0~1。
     * <p>
     * 每一帧都会向 {@link #targetLevels} 靠近：能量上升时响应更快，
     * 能量下降时回落稍慢，让语音条看起来更自然，避免生硬闪烁。
     */
    private final float[] drawLevels = new float[BAR_COUNT];

    /**
     * 语音条颜色。
     * <p>
     * 默认颜色为项目当前使用的红色值，外部可通过 {@link #setBarColor(int)} 修改。
     */
    private int barColor = Color.parseColor("#E9302d");

    /**
     * 单根语音条线宽，单位 px。
     */
    private float barWidth;

    /**
     * 相邻语音条水平间距，单位 px。
     */
    private float barSpacing;

    /**
     * 最近一次收到有效音频数据的时间。
     * <p>
     * 使用 {@link SystemClock#uptimeMillis()} 记录，避免受系统时间调整影响。
     */
    private long lastUpdateTimeMs;

    /**
     * 当前是否已经收到有效实时频谱数据。
     */
    private boolean hasLiveSpectrumData;

    /**
     * 当前是否处于暂停冻结状态。
     * <p>
     * 暂停后控件会保持当前 drawLevels 对应的波形帧，不再消费新的音频数据，
     * 也不会因为 {@link #DATA_TIMEOUT_MS} 超时而清空画面。
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

        // 使用描边线条绘制语音条，圆头在小尺寸下能保持更柔和的视觉观感。
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
     * @param widthPx 线宽，单位 px，最小值为 1px
     */
    public void setBarWidth(float widthPx) {
        barWidth = Math.max(1F, widthPx);
        barPaint.setStrokeWidth(barWidth);
        invalidate();
    }

    /**
     * 设置相邻语音条间距。
     *
     * @param spacingPx 间距，单位 px，最小值为 0px
     */
    public void setBarSpacing(float spacingPx) {
        barSpacing = Math.max(0F, spacingPx);
        invalidate();
    }

    /**
     * 设置语音能量数据。
     * <p>
     * 该方法适合由录音音量、语音识别音量、FFT 回调或业务层自定义能量数组直接调用。
     * 输入数组长度不要求固定，方法内部会按区间压缩为 4 根语音条的目标高度。
     *
     * @param levels 音频能量数组，值越大表示该采样区间能量越强
     */
    public void setVoiceLevels(float[] levels) {
        if (spectrumPaused) {
            return;
        }
        if (!hasDrawableSpectrumData(levels)) {
            clearSpectrumData();
            postInvalidateOnAnimation();
            return;
        }
        buildTargetLevels(levels);
        hasLiveSpectrumData = true;
        lastUpdateTimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    /**
     * 清空频谱数据并停止绘制。
     * <p>
     * 清空时会同时重置目标高度和当前绘制高度。下一帧如果仍没有有效数据，
     * 控件会保持完全空白，不显示默认条形或上一帧残留。
     */
    public void clearSpectrumData() {
        for (int index = 0; index < BAR_COUNT; index++) {
            targetLevels[index] = 0F;
            drawLevels[index] = 0F;
        }
        hasLiveSpectrumData = false;
        lastUpdateTimeMs = 0L;
    }

    /**
     * 设置语音频谱是否暂停。
     * <p>
     * 暂停时保留当前已经绘制出来的波形帧；暂停期间即使播放器继续回调数据，
     * {@link #setVoiceLevels(float[])} 也会直接忽略，从而保证画面不再跳动。
     * 恢复时会刷新最近更新时间，避免刚恢复就因为暂停期间的时间流逝而立即被判定为过期。
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
     * 获取当前是否处于暂停冻结状态。
     *
     * @return true 表示当前正在保持暂停帧
     */
    public boolean isSpectrumPaused() {
        return spectrumPaused;
    }

    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
        // 当前控件使用 FFT 回调作为默认数据入口，将原始频谱数据压缩为 4 根语音条。
        setVoiceLevels(fft);
    }

    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // View 离屏后释放实时状态，避免重新 attach 时继续显示旧语音帧。
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
        if (nowMs - lastUpdateTimeMs > DATA_TIMEOUT_MS) {
            return;
        }

        // 先更新当前绘制高度，再按照最新 drawLevels 绘制当前帧。
        boolean moving = updateDrawLevels();
        drawBars(canvas);
        if (moving || hasLiveSpectrumData) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * 更新当前绘制高度。
     * <p>
     * 当目标高度高于当前高度时使用较大的追赶系数，让语音输入增强时立即反馈；
     * 当目标高度低于当前高度时使用较小的回落系数，让线条下降更柔和。
     *
     * @return true 表示仍存在可见动画位移，需要继续请求下一帧
     */
    private boolean updateDrawLevels() {
        boolean moving = false;
        for (int index = 0; index < BAR_COUNT; index++) {
            float speed = targetLevels[index] > drawLevels[index] ? 0.45F : 0.18F;
            drawLevels[index] += (targetLevels[index] - drawLevels[index]) * speed;
            if (Math.abs(targetLevels[index] - drawLevels[index]) > 0.002F) {
                moving = true;
            }
        }
        return moving;
    }

    /**
     * 绘制 4 根中心对齐的语音条。
     * <p>
     * 语音条以控件垂直中心为基准，上下对称延展。这样即使控件高度很小，
     * 也能保持视觉居中，不会贴顶或贴底。
     *
     * @param canvas 当前绘制画布
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
     * 将原始音频能量数组压缩为 4 根语音条的目标高度。
     * <p>
     * 每根语音条对应原数组中的一个连续区间。区间内同时参考平均值和峰值：
     * 平均值用于保证整体稳定，峰值用于保留瞬时语音冲击，避免声音尖峰被完全抹平。
     *
     * @param levels 原始音频能量数组
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

        for (int index = 0; index < BAR_COUNT; index++) {
            int start = index * levels.length / BAR_COUNT;
            int end = Math.max(start + 1, (index + 1) * levels.length / BAR_COUNT);
            float sum = 0F;
            float localMax = 0F;
            for (int dataIndex = start; dataIndex < end && dataIndex < levels.length; dataIndex++) {
                float value = Math.max(0F, levels[dataIndex]);
                sum += value;
                localMax = Math.max(localMax, value);
            }
            float average = sum / Math.max(1, end - start);
            float normalized = (average * 0.35F + localMax * 0.65F) / maxValue;

            // sqrt 非线性增强能提高小音量下的可见性，同时保持最大高度不超过 1。
            targetLevels[index] = clamp((float) Math.sqrt(normalized), 0F, 1F);
        }
    }

    /**
     * 获取每根语音条的造型高度系数。
     * <p>
     * 左右两根较短，中间两根较高，用固定轮廓还原设计图中的语音指示器造型。
     *
     * @param index 语音条下标
     * @return 当前语音条相对于最大高度的比例系数
     */
    private float resolveBarProfile(int index) {
        switch (index) {
            case 0:
            case 3:
                return 0.42F;
            default:
                return 0.88F;
        }
    }

    /**
     * 判断输入数组是否包含可绘制音频能量。
     * <p>
     * 如果数组为 null、长度为 0，或所有值都不超过 {@link #DATA_EPSILON}，
     * 则认为当前没有有效语音数据，控件应保持空白。
     *
     * @param levels 待检测的音频能量数组
     * @return true 表示至少存在一个有效能量值
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
     * 将数值限制在指定区间内。
     *
     * @param value 原始值
     * @param min   最小值
     * @param max   最大值
     * @return 限制后的值
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
