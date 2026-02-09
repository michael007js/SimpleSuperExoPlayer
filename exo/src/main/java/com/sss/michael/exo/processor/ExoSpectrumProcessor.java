package com.sss.michael.exo.processor;

import android.annotation.SuppressLint;
import android.os.SystemClock;

import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.util.ExoLog;

import java.nio.ByteBuffer;
import java.util.Arrays;

import be.tarsos.dsp.util.fft.FFT;

/**
 * @author Michael by SSS
 * @date 2026/1/2 21:15
 * @Description 频谱处理器
 */
@UnstableApi
public class ExoSpectrumProcessor extends ExoBaseAudioProcessor {

    // FFT样本大小（必须为2的幂，影响频谱分辨率）
    private int sampleSize;
    // FFT计算间隔（毫秒），由最大帧率推导得出
    private long fftIntervalMs;

    // 存储待进行FFT分析的音频浮点数组
    private float[] audioFloats;
    // TarsosDSP的FFT工具类实例
    private FFT fft;
    // FFT数据回调监听器
    private IExoFFTCallBack iExoFFTCallBack;
    // 上一次执行FFT的时间戳，用于控制帧率
    private long lastFftTime = 0;
    // 环形缓冲区，用于缓存音频样本（避免数据溢出，高效存取）
    private CircularShortBuffer sampleBuffer;
    // 汉宁窗数组，用于抑制FFT频谱泄漏
    private float[] hanningWindow;

    // process方法耗时统计（单位：纳秒）
    private long processTotalTimeNs = 0;
    private long processCallCount = 0;
    private long processMaxTimeNs = 0;
    private long processMinTimeNs = Long.MAX_VALUE;
    // performFftAnalysis方法耗时统计
    private long fftTotalTimeNs = 0;
    private long fftCallCount = 0;
    private long fftMaxTimeNs = 0;
    private long fftMinTimeNs = Long.MAX_VALUE;
    // calculateAndCallbackMagnitude方法耗时统计
    private long magnitudeTotalTimeNs = 0;
    private long magnitudeCallCount = 0;
    private long magnitudeMaxTimeNs = 0;
    private long magnitudeMinTimeNs = Long.MAX_VALUE;
    // 统计输出间隔
    private long lastStatPrintTimeMs = 0;

    public ExoSpectrumProcessor() {
        this.sampleSize = ExoConfig.FFT_SAMPLE_SIZE;
        // 计算FFT执行间隔：1000毫秒 / 最大帧率
        this.fftIntervalMs = 1000 / ExoConfig.FFT_MAX_FPS;
        initSampleConfig(sampleSize);
    }

    /**
     * 动态修改FFT样本大小
     *
     * @param sampleSize 新的样本大小，需满足2的幂 + [64,2048]范围
     */
    public void setSampleSize(int sampleSize) {
        // 参数合法性校验
        if ((sampleSize & (sampleSize - 1)) != 0 || sampleSize < 64 || sampleSize > 2048) {
            ExoLog.log("ExoSpectrumProcessor 无效样本大小：" + sampleSize + "，范围[64,2048]且为2的幂，本次设置跳过");
            return;
        }
        this.sampleSize = sampleSize;
        // 计算FFT执行间隔
        this.fftIntervalMs = 1000 / ExoConfig.FFT_MAX_FPS;
        initSampleConfig(sampleSize);
    }

    /**
     * 初始化FFT相关的样本配置
     *
     * @param sampleSize FFT样本大小
     */
    private void initSampleConfig(int sampleSize) {
        this.audioFloats = new float[sampleSize];
        this.fft = new FFT(sampleSize);
        this.sampleBuffer = new CircularShortBuffer(getNextPowerOfTwo(sampleSize * 2));
        this.hanningWindow = createHanningWindow(sampleSize);
    }

    /**
     * 创建汉宁窗（Hanning Window）
     * 作用：减少FFT分析时的频谱泄漏，让频谱结果更准确
     * 公式：w(n) = 0.5 * (1 - cos(2πn / (size - 1)))
     *
     * @param size 窗函数大小（与FFT样本大小一致）
     * @return 汉宁窗数组
     */
    private float[] createHanningWindow(int size) {
        float[] window = new float[size];
        for (int i = 0; i < size; i++) {
            window[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (size - 1))));
        }
        return window;
    }

    /**
     * 获取大于等于目标值的最近2的幂
     * 用于初始化环形缓冲区容量，保证缓冲区容量为2的幂（便于位运算提高存取效率）
     *
     * @param target 目标值
     * @return 最近的2的幂
     */
    private int getNextPowerOfTwo(int target) {
        if (target <= 0) {
            return ExoConfig.FFT_SAMPLE_SIZE;
        }
        int power = 1;
        // 左移运算（等价于power *= 2），直到大于等于target
        while (power < target) {
            power <<= 1;
        }
        return power;
    }

    /**
     * 设置FFT数据回调监听器
     *
     * @param iExoFFTCallBack 监听器实例，用于接收FFT数据和幅度数据
     */
    public void setExoFFTCallBack(IExoFFTCallBack iExoFFTCallBack) {
        this.iExoFFTCallBack = iExoFFTCallBack;
    }

    /**
     * 音频数据透传 + 样本缓存 + FFT分析
     *
     * @param input        输入音频缓冲区
     * @param output       输出音频缓冲区
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     */
    @Override
    protected void process(ByteBuffer input, ByteBuffer output, int sampleRateHz, int channelCount) {
        long processStartNs = System.nanoTime();
        try {
            // 记录输入缓冲区的原始位置和限制，避免修改外部缓冲区状态
            int position = input.position();
            int limit = input.limit();
            int tempPos = position;

            while (tempPos + channelCount * 2 <= limit) {
                for (int c = 0; c < channelCount; c++) {
                    // 读取16位音频样本（short类型，占2字节）
                    short sample = input.getShort(tempPos + c * 2);
                    sampleBuffer.put(sample);
                }
                tempPos += channelCount * 2;
            }
            // 帧率控制的容错
            long now = SystemClock.elapsedRealtime();

            // 如果检测到当前时间与上次 FFT 时间间隔过大（比如暂停了很久）
            // 强制重置 lastFftTime，防止暂停后的第一帧因为计算间隔错误而被跳过
            if (now - lastFftTime > 1000) {
                lastFftTime = now;
            }
            // 帧率控制：判断是否达到FFT执行间隔，且监听器已设置
            if (iExoFFTCallBack != null && SystemClock.elapsedRealtime() - lastFftTime > fftIntervalMs) {
                // 校验缓冲区可用数据是否满足FFT样本要求
                if (sampleBuffer.getAvailableDataSize() >= sampleSize * 2) {
                    performFftAnalysis(sampleRateHz, channelCount);
                    lastFftTime = now;
                }
            }
            output.put(input);
        } catch (Exception e) {
            // 异常时仍透传数据，避免音频播放中断
            ExoLog.log("ExoSpectrumProcessor 处理异常，已透传数据", e);
            output.put(input);
        } finally {
            updateProcessTimeStat(System.nanoTime() - processStartNs);
            long currentTimeMs = System.currentTimeMillis();
            if (currentTimeMs - lastStatPrintTimeMs >= ExoConfig.FFT_STAT_PRINT_INTERVAL_MS) {
                printCpuTimeStat();
                lastStatPrintTimeMs = currentTimeMs;
            }
        }
    }

    /**
     * 执行FFT分析核心逻辑
     *
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     */
    private void performFftAnalysis(int sampleRateHz, int channelCount) {
        long fftStartNs = System.nanoTime();
        // 缓冲区当前可用的总采样点数
        int availableTotalSamples = sampleBuffer.getAvailableDataSize() / (channelCount * 2);

        // 如果样本不足以支撑一次完整的连续 FFT，直接跳过
        if (availableTotalSamples < sampleSize) {
            ExoLog.log("ExoSpectrumProcessor 可用样本数不足：" + availableTotalSamples + " < " + sampleSize + "，跳过FFT");
            return;
        }

        int startSampleIndex = availableTotalSamples - sampleSize;
        // 顺序读取连续样本
        for (int i = 0; i < sampleSize; i++) {
            // 计算在逻辑上的样本索引
            int logicalIndex = startSampleIndex + i;
            // 转换为字节偏移量
            int byteOffset = logicalIndex * channelCount * 2;

            // 合并多声道
            float sum = 0;
            for (int c = 0; c < channelCount; c++) {
                short sample = sampleBuffer.get(byteOffset + c * 2);
                sum += sample;
            }
            // 转换为浮点型（归一化到[-1,1]） + 应用汉宁窗，抑制频谱泄漏
            audioFloats[i] = (sum / channelCount) / 32768.0f * hanningWindow[i];
        }

        // 执行正向FFT变换
        fft.backwardsTransform(audioFloats);

        // 传递数据副本
        float[] fftCopy = Arrays.copyOf(audioFloats, audioFloats.length);
        if (iExoFFTCallBack != null) {
            iExoFFTCallBack.onFFTReady(sampleRateHz, channelCount, fftCopy);
        }

        updateFftTimeStat(System.nanoTime() - fftStartNs);

        if (ExoConfig.FFT_CALCULATE_MAGNITUDE) {
            calculateAndCallbackMagnitude(sampleRateHz);
        }
    }


    /**
     * 计算音频 FFT 幅度并回调。
     * 数组结构说明：
     * audioFloats：FFT 输出，偶数位为实部 (Re)，奇数位为虚部 (Im)
     * half = audioFloats.length / 2：有效 FFT bin 数量
     * mag：原始幅度（归一化 + log 压缩 + 增强）
     * smooth：平滑后的幅度数组
     *
     * @param sampleRateHz 音频采样率，回调时传递给监听者
     */
    private void calculateAndCallbackMagnitude(int sampleRateHz) {
        long startNs = System.nanoTime();

        final float[] fftData = audioFloats; // FFT 输出数据
        final int n = fftData.length;
        final int half = n >> 1; // 只取前半部分有效频率

        float[] mag = new float[half];

        final float scale = 2f / n;          // 幅度缩放系数
        final float ampBoost = ExoConfig.FFT_AMPLITUDE_BOOST; // 全局增益系数

        float maxL = 1e-6f; // 左声道峰值
        float maxR = 1e-6f; // 右声道峰值
        final int mid = half >> 1;

        // 基础幅度计算 + log 压缩
        for (int i = 0; i < half; i++) {
            float re = fftData[i << 1];       // 实部
            float im = fftData[(i << 1) + 1]; // 虚部

            // 幅度计算
            float v = (float) Math.sqrt(re * re + im * im) * scale;

            // log 压缩，控制动态范围
            v = v / (v + ExoConfig.SPECTRUM_LOG_K);

            mag[i] = v;

            // 分别记录左右声道峰值
            if (i < mid) maxL = Math.max(maxL, v);
            else maxR = Math.max(maxR, v);
        }

        // 归一化
        float globalMax = Math.max(maxL, maxR);
        if (globalMax < 1e-6f) globalMax = 1e-6f; // 防止除 0

        for (int i = 0; i < half; i++) {
            float x = i / (float) half;  // 归一化频率 (0~1)
            float v = mag[i] / globalMax; // 归一化幅度 0~1

            // 低频增强
            float lowBoost = 1f;
            if (x < ExoConfig.SPECTRUM_LOW_FREQ_END) {
                float t = x / ExoConfig.SPECTRUM_LOW_FREQ_END;
                // 曲线平滑增强：Base + Strength * (1 - (1-t)^2)
                lowBoost = ExoConfig.SPECTRUM_LOW_BASE
                        + ExoConfig.SPECTRUM_LOW_STRENGTH * (1f - (1f - t) * (1f - t));
            }

            // 高频增强
            float highBoost = 1f;
            if (x > ExoConfig.SPECTRUM_HIGH_FREQ_START) {
                float t = (x - ExoConfig.SPECTRUM_HIGH_FREQ_START) / (1f - ExoConfig.SPECTRUM_HIGH_FREQ_START);
                highBoost = 1f + ExoConfig.SPECTRUM_HIGH_STRENGTH * t * t; // 二次曲线增强
            }

            // 中频增强
            float midBoost = 1f;
            if (x > ExoConfig.SPECTRUM_MID_START && x < ExoConfig.SPECTRUM_MID_END) {
                float t = (x - ExoConfig.SPECTRUM_MID_START)
                        / (ExoConfig.SPECTRUM_MID_END - ExoConfig.SPECTRUM_MID_START);
                float u = 2f * t - 1f; // 归一化到 [-1,1]
                midBoost = ExoConfig.SPECTRUM_MID_BASE + ExoConfig.SPECTRUM_MID_STRENGTH * (1f - u * u);
            }

            // 频率扩散
            float spread = (float) Math.pow(x, ExoConfig.SPECTRUM_SPREAD_EXP);

            // 组合所有增益 + 全局放大
            v *= lowBoost * highBoost * midBoost * spread * ampBoost;

            // 限制最大幅度为 1
            mag[i] = Math.min(v, 1f);
        }

        // 平滑
        float[] smooth = new float[half];
        for (int i = 0; i < half; i++) {
            float sum = mag[i] * ExoConfig.SPECTRUM_SMOOTH_CENTER; // 中心点权重
            float w = ExoConfig.SPECTRUM_SMOOTH_CENTER;

            if (i > 0) {
                sum += mag[i - 1] * ExoConfig.SPECTRUM_SMOOTH_SIDE; // 左邻点权重
                w += ExoConfig.SPECTRUM_SMOOTH_SIDE;
            }
            if (i < half - 1) {
                sum += mag[i + 1] * ExoConfig.SPECTRUM_SMOOTH_SIDE; // 右邻点权重
                w += ExoConfig.SPECTRUM_SMOOTH_SIDE;
            }

            smooth[i] = sum / w;
        }

        updateMagnitudeTimeStat(System.nanoTime() - startNs);
        iExoFFTCallBack.onMagnitudeReady(sampleRateHz, smooth);
    }

    /**
     * 更新process方法耗时统计
     */
    private void updateProcessTimeStat(long costNs) {
        processTotalTimeNs += costNs;
        processCallCount++;
        processMaxTimeNs = Math.max(processMaxTimeNs, costNs);
        processMinTimeNs = Math.min(processMinTimeNs, costNs);
    }

    /**
     * 更新FFT分析方法耗时统计
     */
    private void updateFftTimeStat(long costNs) {
        fftTotalTimeNs += costNs;
        fftCallCount++;
        fftMaxTimeNs = Math.max(fftMaxTimeNs, costNs);
        fftMinTimeNs = Math.min(fftMinTimeNs, costNs);
    }

    /**
     * 更新幅度计算方法耗时统计
     */
    private void updateMagnitudeTimeStat(long costNs) {
        magnitudeTotalTimeNs += costNs;
        magnitudeCallCount++;
        magnitudeMaxTimeNs = Math.max(magnitudeMaxTimeNs, costNs);
        magnitudeMinTimeNs = Math.min(magnitudeMinTimeNs, costNs);
    }

    /**
     * 打印CPU耗时统计结果（转换为毫秒，便于阅读）
     */
    @SuppressLint("DefaultLocale")
    private void printCpuTimeStat() {
        if (!ExoConfig.LOG_ENABLE) {
            return;
        }
        // 计算平均耗时
        double processAvgMs = processCallCount > 0 ? (processTotalTimeNs / 1_000_000.0) / processCallCount : 0;
        double fftAvgMs = fftCallCount > 0 ? (fftTotalTimeNs / 1_000_000.0) / fftCallCount : 0;
        double magnitudeAvgMs = magnitudeCallCount > 0 ? (magnitudeTotalTimeNs / 1_000_000.0) / magnitudeCallCount : 0;

        // 转换最大/最小耗时为毫秒
        double processMaxMs = processMaxTimeNs / 1_000_000.0;
        double processMinMs = processMinTimeNs == Long.MAX_VALUE ? 0 : processMinTimeNs / 1_000_000.0;
        double fftMaxMs = fftMaxTimeNs / 1_000_000.0;
        double fftMinMs = fftMinTimeNs == Long.MAX_VALUE ? 0 : fftMinTimeNs / 1_000_000.0;
        double magnitudeMaxMs = magnitudeMaxTimeNs / 1_000_000.0;
        double magnitudeMinMs = magnitudeMinTimeNs == Long.MAX_VALUE ? 0 : magnitudeMinTimeNs / 1_000_000.0;

        // 统计日志
        StringBuilder statLog = new StringBuilder();
        statLog.append("===== 频谱分析 CPU耗时统计（最近" + (ExoConfig.FFT_STAT_PRINT_INTERVAL_MS / 1000) + "秒）=====\n");
        statLog.append(String.format("process方法：调用%d次 | 平均%.3fms | 最大%.3fms | 最小%.3fms\n",
                processCallCount, processAvgMs, processMaxMs, processMinMs));
        statLog.append(String.format("FFT分析：调用%d次 | 平均%.3fms | 最大%.3fms | 最小%.3fms\n",
                fftCallCount, fftAvgMs, fftMaxMs, fftMinMs));
        statLog.append(String.format("幅度计算：调用%d次 | 平均%.3fms | 最大%.3fms | 最小%.3fms\n",
                magnitudeCallCount, magnitudeAvgMs, magnitudeMaxMs, magnitudeMinMs));
        statLog.append("======================================================");

        ExoLog.log(statLog.toString());

        // 每次输出后重置
        resetTimeStat();
    }

    /**
     * 重置耗时统计
     */
    public void resetTimeStat() {
        processTotalTimeNs = 0;
        processCallCount = 0;
        processMaxTimeNs = 0;
        processMinTimeNs = Long.MAX_VALUE;

        fftTotalTimeNs = 0;
        fftCallCount = 0;
        fftMaxTimeNs = 0;
        fftMinTimeNs = Long.MAX_VALUE;

        magnitudeTotalTimeNs = 0;
        magnitudeCallCount = 0;
        magnitudeMaxTimeNs = 0;
        magnitudeMinTimeNs = Long.MAX_VALUE;

        lastStatPrintTimeMs = 0;
    }

    /**
     * 内部静态类：环形缓冲区（循环缓冲区）
     * 用于高效缓存音频样本，避免数据溢出，支持先进先出（FIFO）存取
     * 容量为2的幂，通过位运算提高存取效率
     */
    private static class CircularShortBuffer {
        // 缓冲区存储数组
        private final short[] buffer;
        // 掩码：用于快速计算索引（等价于取模运算，效率更高）
        private final int mask;
        // 头指针：指向待写入数据的位置
        private int head;
        // 尾指针：指向待读取数据的位置
        private int tail;
        // 缓冲区当前存储的样本数
        private int size;

        /**
         * 初始化环形缓冲区
         *
         * @param capacity 期望容量，会自动调整为最近的2的幂
         */
        public CircularShortBuffer(int capacity) {
            int actualCapacity = 1;
            // 调整容量为2的幂
            while (actualCapacity < capacity) {
                actualCapacity <<= 1;
            }
            buffer = new short[actualCapacity];
            mask = actualCapacity - 1;
            head = 0;
            tail = 0;
            size = 0;
        }

        /**
         * 写入单个样本到缓冲区
         *
         * @param sample 待写入的16位音频样本
         */
        public void put(short sample) {
            buffer[head] = sample;
            // 头指针移动：位运算（等价于(head + 1) % buffer.length）
            head = (head + 1) & mask;
            // 若缓冲区已满，尾指针同步移动（丢弃最旧数据）
            if (size == buffer.length) {
                tail = (tail + 1) & mask;
            } else {
                // 缓冲区未满，样本数加1
                size++;
            }
        }

        /**
         * 从缓冲区读取指定偏移量的样本
         *
         * @param offset 字节偏移量
         * @return 读取的16位音频样本，偏移量非法时返回0
         */
        public short get(int offset) {
            // 偏移量合法性校验
            if (offset < 0 || offset >= size * 2) {
                ExoLog.log("CircularShortBuffer 偏移量超出可用数据范围：" + offset + "，返回0");
                return 0;
            }
            int index = (tail + offset / 2) & mask;
            return buffer[index];
        }

        /**
         * 获取缓冲区中可用数据的字节数
         *
         * @return 可用字节数
         */
        public int getAvailableDataSize() {
            return size * 2;
        }

        /**
         * 释放缓冲区资源：清空数据
         */
        public void release() {
            Arrays.fill(buffer, (short) 0);
        }
    }


    /**
     * 重置处理器状态
     * 清空FFT时间戳和监听器，恢复初始状态
     */
    @Override
    protected void onReset() {
        super.onReset();
        lastFftTime = 0;
        resetTimeStat();
    }

    @Override
    protected void onFlush() {
        if (sampleBuffer != null) {
            sampleBuffer.release();
        }
        lastFftTime = 0;
    }

    /**
     * 释放处理器资源
     * 清空并销毁所有数组、缓冲区、FFT实例，避免内存泄漏
     */
    @Override
    protected void releaseResources() {
        if (audioFloats != null) {
            Arrays.fill(audioFloats, 0);
            audioFloats = null;
        }
        if (hanningWindow != null) {
            Arrays.fill(hanningWindow, 0);
            hanningWindow = null;
        }
        if (sampleBuffer != null) {
            sampleBuffer.release();
            sampleBuffer = null;
        }
        fft = null;
        iExoFFTCallBack = null;

        resetTimeStat();
    }
}