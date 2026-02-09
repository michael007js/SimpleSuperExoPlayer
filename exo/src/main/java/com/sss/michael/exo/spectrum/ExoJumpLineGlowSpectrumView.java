package com.sss.michael.exo.spectrum;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.IExoFFTCallBack;

/**
 * @author Michael by SSS
 * @date 2026/1/7 0007 13:36
 * @Description 发光跳动线
 */
public class ExoJumpLineGlowSpectrumView extends View implements IExoFFTCallBack {

    // 频谱频段总数（界面上显示的垂直频谱条数量）
    private static final int BANDS = 10;
    // 低频段数量（前3个频段归为低频，做专属视觉效果）
    private static final int LOW_FREQ_BANDS = 3;
    // 频谱条最小高度比例（相对View高度，避免频谱完全消失）
    private static final float MIN_HEIGHT = 0.02f;

    // 频谱高度衰减系数（0~1，值越大衰减越慢，视觉更丝滑）
    private static final float DECAY = 0.75f;
    // 频谱高度弹性系数（0~1，值越大向目标高度趋近越快）
    private static final float ELASTIC = 0.10f;
    // 低频段惯性系数（值越小，低频频谱高度变化越平缓）
    private static final float LOW_INERTIA = 0.06f;
    // 高频段惯性系数（值越大，高频频谱高度变化越灵敏）
    private static final float HIGH_INERTIA = 0.22f;

    // 低频振荡上升系数（值越大，低频呼吸效果响应越快）
    private static final float OSC_ATTACK = 0.35f;
    // 低频振荡衰减系数（值越大，低频呼吸效果持续越久）
    private static final float OSC_DECAY = 0.20f;
    // 低频呼吸效果缩放比例（控制低频段整体缩放幅度）
    private static final float BREATH_SCALE = 0.10f;

    // 频谱发光基础模糊半径（发光效果的固定基础大小）
    private static final float BASE_GLOW_RADIUS = 10f;
    // 发光效果增益值（随音频能量动态增加发光半径）
    private static final float GLOW_GAIN = 20f;

    // 节拍检测阈值（能量差值超过此值判定为节拍/鼓点）
    private static final float BEAT_THRESHOLD = 0.12f;
    // 节拍闪烁衰减系数（值越大，节拍高亮效果持续越久）
    private static final float BEAT_FLASH_DECAY = 0.99f;

    // 历史路径缓存长度（存储最近5帧频谱路径，实现拖影效果）
    private static final int HISTORY_LEN = 2;

    // 主画笔：绘制频谱条主体轮廓
    private Paint mainPaint;
    // 发光画笔：绘制频谱条模糊发光效果
    private Paint glowPaint;
    // 历史路径数组：缓存最近HISTORY_LEN帧的频谱路径（拖影效果）
    private Path[] historyPaths;
    // 历史路径当前索引：循环复用historyPaths的指针
    private int historyIndex = 0;

    // 当前各频段频谱高度（0~1，最终绘制的实际高度）
    private float[] current;
    // 目标各频段频谱高度（0~1，由音频FFT计算的理论高度）
    private float[] target;
    // 各频段X坐标数组：缓存每个频段的水平位置，避免重复计算
    private float[] x;
    // 贝塞尔曲线控制点Y1：用于绘制平滑频谱曲线
    private float[] y1;
    // 贝塞尔曲线控制点Y2：配合Y1实现频谱曲线平滑过渡
    private float[] y2;
    // View宽度缓存：避免onDraw中频繁调用getWidth()
    private int w;
    // View高度缓存：避免onDraw中频繁调用getHeight()
    private int h;

    // 当前音频均方根能量：衡量整体音量/能量的核心指标
    private float rmsEnergy = 0f;
    // 上一帧音频均方根能量：用于计算能量差值检测节拍
    private float lastRmsEnergy = 0f;
    // 低频段能量值：前3个频段的能量均值，控制低频视觉效果
    private float lowFreqEnergy = 0f;

    // 低频振荡值：控制低频段呼吸效果的幅度
    private float lowFreqOsc = 0f;
    // 节拍闪烁强度：0~1，控制频谱高亮闪烁的强度
    private float beatFlash = 0f;
    // 低频脉冲值：节拍触发时，低频段额外增加的高度增量
    private float lowFreqPulse = 0f;

    public ExoJumpLineGlowSpectrumView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        init();
    }

    public ExoJumpLineGlowSpectrumView(Context c) {
        super(c);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setStyle(Paint.Style.STROKE);
        mainPaint.setStrokeWidth(3f);
        mainPaint.setStrokeCap(Paint.Cap.ROUND);
        mainPaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint = new Paint(mainPaint);
        glowPaint.setStrokeWidth(8f);

        current = new float[BANDS];
        target = new float[BANDS];
        x = new float[BANDS];
        y1 = new float[BANDS];
        y2 = new float[BANDS];

        historyPaths = new Path[HISTORY_LEN];
        for (int i = 0; i < HISTORY_LEN; i++) historyPaths[i] = new Path();

        resetFlat();
    }

    private void resetFlat() {
        for (int i = 0; i < BANDS; i++) {
            current[i] = MIN_HEIGHT;
            target[i] = MIN_HEIGHT;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        this.w = w;
        this.h = h;

        float step = w / (float) (BANDS - 1);
        for (int i = 0; i < BANDS; i++) x[i] = i * step;
    }

    @Override
    public void onMagnitudeReady(int sr, float[] mag) {
        float[] smooth = gaussianSmooth(mag);
        float[] bins = extractBins(smooth, BANDS);

        float rmsSum = 0f;
        float lowSum = 0f;

        for (int i = 0; i < BANDS; i++) {
            rmsSum += bins[i] * bins[i];
            if (i < LOW_FREQ_BANDS) lowSum += bins[i];
            float db = 20f * (float) Math.log10(bins[i] + 1e-8f);
            target[i] = Math.max(MIN_HEIGHT, Math.min(1f, (db + 60f) / 70f));
        }

        lastRmsEnergy = rmsEnergy;
        rmsEnergy = (float) Math.sqrt(rmsSum / BANDS);
        lowFreqEnergy = lowSum / LOW_FREQ_BANDS;

        detectBeat();
        exciteLowFreqOsc();
    }

    private void exciteLowFreqOsc() {
        lowFreqOsc += (lowFreqEnergy - lowFreqOsc) * OSC_ATTACK;
    }

    private void detectBeat() {
        float diff = rmsEnergy - lastRmsEnergy;
        if (diff > BEAT_THRESHOLD) {
            beatFlash = 1f;
            lowFreqPulse = 1f;
        }
    }

    private void updateHeights() {
        lowFreqOsc *= OSC_DECAY;
        beatFlash *= BEAT_FLASH_DECAY;
        lowFreqPulse *= 0.75f;

        float inertiaFactor = clamp(rmsEnergy * 4f, 0.6f, 1.4f);

        for (int i = 0; i < BANDS; i++) {
            float bandInertia = lerp(LOW_INERTIA, HIGH_INERTIA, i / (float) (BANDS - 1)) * inertiaFactor;
            float c = current[i];
            float t = target[i];

            if (t > MIN_HEIGHT + 0.001f) c = c * DECAY + t * ELASTIC;
            else c += (MIN_HEIGHT - c) * bandInertia;

            if (i == 0) c += lowFreqPulse * 0.3f;

            current[i] = Math.max(MIN_HEIGHT, c);
        }
    }

    private void buildCtrlPoints() {
        float baseScale = h * 0.8f;
        float breath = 1f + lowFreqOsc * BREATH_SCALE;

        for (int i = 0; i < BANDS; i++) {
            float scale = (i < LOW_FREQ_BANDS) ? baseScale * breath : baseScale;
            float y = h - current[i] * scale;

            if (i == 0 || i >= BANDS - 2) {
                y1[i] = y;
                y2[i] = y;
            } else {
                float prev = h - current[i - 1] * scale;
                float next = h - current[i + 1] * scale;
                float d = (prev - next) * ((i < LOW_FREQ_BANDS) ? 0.25f : 0.15f);
                y1[i] = y + d;
                y2[i] = y - d;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w == 0) return;

        updateHeights();
        buildCtrlPoints();

        float scale = h * 0.8f;

        // 当前path
        Path currentPath = historyPaths[historyIndex];
        currentPath.reset();
        currentPath.moveTo(x[0], h - current[0] * scale);
        for (int i = 0; i < BANDS - 1; i++) {
            float c1x = x[i] + (x[i + 1] - x[i]) * 0.33f;
            float c2x = x[i + 1] - (x[i + 1] - x[i]) * 0.33f;
            float y2p = h - current[i + 1] * scale;
            currentPath.cubicTo(c1x, y2[i], c2x, y1[i + 1], x[i + 1], y2p);
        }

        // 历史发光path
        float glowBase = BASE_GLOW_RADIUS + lowFreqOsc * GLOW_GAIN + lowFreqPulse * 40f;

        for (int i = 0; i < HISTORY_LEN; i++) {
            int idx = (historyIndex + i + 1) % HISTORY_LEN;
            Path p = historyPaths[idx];

            float alphaFactor = 1f - (i + 1) / (float) (HISTORY_LEN * 1.5f);  // 越旧越透明

            float radius = Math.max(1f, glowBase * alphaFactor); // 防止崩溃

            int[] glowColors = {
                    Color.argb((int) (150 * alphaFactor), 255, 200, 200),
                    Color.argb((int) (100 * alphaFactor), 255, 255, 200)
            };

            for (int color : glowColors) {
                glowPaint.setColor(color);
                glowPaint.setMaskFilter(new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
                canvas.drawPath(p, glowPaint);
            }
        }

        // 当前path主体 + Beat
        int mainBase = Color.WHITE;
        int flash = Color.argb((int) (beatFlash * 180), 255, 255, 255);
        mainPaint.setColor(blend(mainBase, flash, beatFlash));
        canvas.drawPath(currentPath, mainPaint);


        // 更新历史path索引
        historyIndex = (historyIndex + 1) % HISTORY_LEN;

        invalidate();
    }

    @Override
    public void onFFTReady(int sr, int ch, float[] fft) {
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int blend(int c1, int c2, float t) {
        int r = (int) (Color.red(c1) * (1 - t) + Color.red(c2) * t);
        int g = (int) (Color.green(c1) * (1 - t) + Color.green(c2) * t);
        int b = (int) (Color.blue(c1) * (1 - t) + Color.blue(c2) * t);
        return Color.rgb(r, g, b);
    }

    private float[] extractBins(float[] d, int bins) {
        float[] out = new float[bins];
        int size = d.length / bins;
        for (int i = 0; i < bins; i++) {
            float s = 0;
            int st = i * size;
            int ed = Math.min(d.length, st + size);
            for (int j = st; j < ed; j++) s += d[j];
            out[i] = s / Math.max(1, ed - st);
        }
        return out;
    }

    private float[] gaussianSmooth(float[] d) {
        float[] k = {0.1f, 0.2f, 0.4f, 0.2f, 0.1f};
        float[] o = new float[d.length];
        int r = k.length / 2;
        for (int i = 0; i < d.length; i++) {
            float s = 0, w = 0;
            for (int j = 0; j < k.length; j++) {
                int id = i + j - r;
                if (id >= 0 && id < d.length) {
                    s += d[id] * k[j];
                    w += k[j];
                }
            }
            o[i] = s / w;
        }
        return o;
    }
}
