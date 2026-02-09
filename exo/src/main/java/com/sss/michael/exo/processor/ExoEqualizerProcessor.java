package com.sss.michael.exo.processor;

import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.util.ExoLog;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Michael by 61642
 * @date 2026/1/4 14:41
 * @Description 均衡处理器
 */
@UnstableApi
public class ExoEqualizerProcessor extends ExoBaseAudioProcessor {


    // 滤波器二维数组：[声道数][频段数]，每个声道对应一组10频段Biquad滤波器
    private BiquadFilter[][] filters;
    // 目标增益数组（单位：dB），用户直接设置，无额外增益平滑层，瞬时更新
    private float[] targetGains;

    // 标记位：是否有待更新的滤波器目标系数（增益变化后标记为true）
    private volatile boolean isPendingUpdate = true;
    // EQ激活状态标记（存在非零增益时为true，用于快速路径判断是否透传数据）
    private boolean isEqActive = false;
    // 可重入锁：保证增益设置、滤波器更新等操作的线程安全
    private final ReentrantLock eqLock = new ReentrantLock();

    // 全局目标增益（线性倍数）：用于预衰减（Pre-cut），防止多频段增益叠加导致破音
    private float targetGlobalGain = 1.0f;
    // 全局当前增益（线性倍数）：用于平滑过渡全局增益，避免“咔哒”声
    private float currentGlobalGain = 1.0f;


    public ExoEqualizerProcessor() {
        int bandCount = ExoConfig.EQ_CENTER_FREQUENCIES.length;
        // 初始化目标增益数组，默认所有频段增益为0dB（无EQ效果）
        this.targetGains = new float[bandCount];
        Arrays.fill(targetGains, 0);
    }

    /**
     * 设置单个频段的目标增益（线程安全）
     * 直接更新目标增益，瞬时生效，无增益平滑层，增益变化后标记滤波器需更新
     *
     * @param bandIndex 频段索引 (0-9，对应10个中心频率)
     * @param dbGain    目标增益（单位：dB），自动钳位在[-15,15]范围内
     */
    public void setBandGain(int bandIndex, float dbGain) {
        eqLock.lock();
        try {
            if (bandIndex < 0 || bandIndex >= targetGains.length) {
                ExoLog.log("ExoEqualizerProcessor 无效的频段索引：" + bandIndex + "，合法范围[0, " + (targetGains.length - 1) + "]，本次设置跳过");
                return;
            }
            // 增益范围钳位：限制在[-15,15]dB，避免无效增益导致失真
            float clampedGain = Math.max(ExoConfig.EQ_MIN_DB, Math.min(ExoConfig.EQ_MAX_DB, dbGain));
            this.targetGains[bandIndex] = clampedGain;
            this.isPendingUpdate = true;
            checkIfActive();
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 批量设置所有频段的目标增益（线程安全）
     * 核心特性：计算全局预衰减（Pre-cut），防止多频段增益叠加导致音频过载破音
     *
     * @param gains 增益数组（长度必须为10，对应10个EQ频段）
     */
    public void setBandGains(float[] gains) {
        eqLock.lock();
        try {
            if (gains == null || gains.length != targetGains.length) return;

            // 计算最大正增益，用于推导全局预衰减值
            float maxPositiveGain = 0;
            for (float g : gains) {
                if (g > maxPositiveGain) maxPositiveGain = g;
            }

            // 计算全局预衰减（Pre-cut）dB值
            // 核心公式：preCutDb = -maxPositiveGain * 0.8f
            // 作用：对所有频段进行整体衰减，避免增益提升后的音频峰值超出0dB，杜绝破音
            float preCutDb = maxPositiveGain > 0 ? -maxPositiveGain * 0.8f : 0f;

            // 将预衰减dB值转换为线性倍数（音频处理通用转换公式）
            this.targetGlobalGain = (float) Math.pow(ExoConfig.EQ_BAND_COUNT, preCutDb / 20.0f);

            // 批量更新目标增益，同时钳位增益范围
            for (int i = 0; i < targetGains.length; i++) {
                targetGains[i] = Math.max(ExoConfig.EQ_MIN_DB, Math.min(ExoConfig.EQ_MAX_DB, gains[i]));
            }

            this.isPendingUpdate = true;
            checkIfActive();
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 检查EQ是否激活
     * 判定规则：遍历目标增益数组，若存在绝对值大于增益跳过阈值的增益，标记为激活
     */
    private void checkIfActive() {
        isEqActive = false;
        if (targetGains == null) {
            return;
        }
        for (float gain : targetGains) {
            if (Math.abs(gain) > ExoConfig.EQ_GAIN_SKIP_THRESHOLD) {
                isEqActive = true;
                break; // 存在有效增益，直接标记为激活并退出循环
            }
        }
    }

    /**
     * 禁用EQ
     * 清零所有目标增益，依赖Biquad滤波器内部系数平滑过渡到无EQ状态
     * 与onReset()的区别：disableEQ=用户主动关闭（修改业务配置）；onReset=管线重建（保留业务配置）
     */
    public void disableEQ() {
        eqLock.lock();
        try {
            Arrays.fill(targetGains, 0);
            this.isPendingUpdate = true;
            this.isEqActive = false;
            ExoLog.log("ExoEqualizerProcessor EQ已禁用，目标增益重置为0，系数平滑过渡");
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 启用EQ（用户主动操作）
     * 恢复上次设置的目标增益，无需重新配置，依赖滤波器系数平滑过渡生效
     */
    public void enableEQ() {
        eqLock.lock();
        try {
            this.isPendingUpdate = true;
            checkIfActive();
            ExoLog.log("ExoEqualizerProcessor EQ已启用，恢复上次目标增益");
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 音频数据滤波 + 透传 + 削波保护
     * 执行流程：1. 加锁更新滤波器 2. 快速路径透传 3. 无锁音频滤波 4. 削波处理
     *
     * @param input        输入音频缓冲区
     * @param output       输出音频缓冲区
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     */
    @Override
    protected void process(ByteBuffer input, ByteBuffer output, int sampleRateHz, int channelCount) {
        BiquadFilter[][] currentFilters = null;
        boolean currentActive = false;

        eqLock.lock();
        try {
            // 滤波器未初始化/声道数变化/增益更新时，更新滤波器目标系数
            if (isPendingUpdate || filters == null || filters.length != channelCount) {
                updateFilterTargetCoefficients(sampleRateHz, channelCount);
                isPendingUpdate = false;
            }

            currentFilters = filters;
            currentActive = isEqActive;
        } finally {
            eqLock.unlock();
        }

        // 快速路径（FastPath）：EQ未激活或滤波器为空，直接透传数据，不做任何处理
        if (!currentActive || currentFilters == null) {
            output.put(input);
            return;
        }

        // 无锁核心处理：音频样本滤波 + 全局增益平滑 + 削波保护
        try {
            // 按声道遍历音频样本，每次处理一个声道组（所有声道的一个样本）
            while (input.hasRemaining()) {
                // 剩余数据不足一个声道组时，停止处理
                if (input.remaining() < channelCount * 2) {
                    break;
                }

                // 遍历每个声道，单独进行滤波处理
                for (int c = 0; c < channelCount; c++) {
                    // 读取16位音频样本（short类型，占2字节）
                    short rawSample = input.getShort();
                    // 全局增益平滑过渡，避免瞬时变化产生“咔哒”声
                    currentGlobalGain += (targetGlobalGain - currentGlobalGain) * ExoConfig.EQ_GLOBAL_GAIN_SMOOTH;
                    // 样本归一化（转换为[-1,1]浮点型） + 应用全局增益
                    float sample = (rawSample / 32768.0f) * currentGlobalGain;
                    // FastPath滤波处理（基于滤波器系数状态判断是否旁路）
                    sample = processWithFastPath(sample, currentFilters[c]);

                    // 削波处理，防止样本超出范围导致破音
                    sample = applyClipping(sample);

                    // 将处理后的样本转换为short类型，写入输出缓冲区
                    output.putShort((short) (sample * 32767));
                }
            }
        } catch (Exception e) {
            // 异常时透传数据，避免音频播放中断
            ExoLog.log("ExoEqualizerProcessor 处理异常，已透传数据", e);
            output.put(input);
        }
    }

    /**
     * FastPath：基于Biquad滤波器内部系数状态判断是否旁路
     * 优势：避免系数收敛途中切断IIR滤波器，防止相位尾音不连续，达到DAW级音频质量
     * 与“基于外部增益判断旁路”的区别：系数状态判断更精准，无过渡失真
     *
     * @param sample  输入浮点型音频样本（[-1,1]）
     * @param filters 单个声道对应的10频段Biquad滤波器数组
     * @return 处理后的浮点型音频样本
     */
    private float processWithFastPath(float sample, BiquadFilter[] filters) {
        // 遍历该声道的所有频段滤波器
        for (BiquadFilter filter : filters) {
            // 仅当滤波器未旁路时（系数状态非零增益），才进行滤波处理
            if (!filter.isBypassed()) {
                sample = filter.process(sample);
            }
        }
        return sample;
    }

    /**
     * 更新滤波器目标系数（仅更新目标值，不修改当前工作系数，无突变）
     * 执行流程：1. 计算奈奎斯特频率 2. 懒加载初始化滤波器 3. 批量更新每个滤波器的峰值EQ参数
     *
     * @param sampleRate   音频采样率
     * @param channelCount 音频声道数
     */
    private void updateFilterTargetCoefficients(int sampleRate, int channelCount) {
        // 奈奎斯特频率：采样率的一半，超过该频率的信号无法被正确采样，需跳过配置
        float nyquist = sampleRate / 2.0f;
        ExoLog.log("ExoEqualizerProcessor 更新EQ滤波器目标系数：采样率=" + sampleRate + "，声道数=" + channelCount + "，奈奎斯特频率=" + nyquist + "Hz");

        if (filters == null || filters.length != channelCount) {
            filters = new BiquadFilter[channelCount][ExoConfig.EQ_CENTER_FREQUENCIES.length];
            for (int c = 0; c < channelCount; c++) {
                for (int i = 0; i < ExoConfig.EQ_CENTER_FREQUENCIES.length; i++) {
                    filters[c][i] = new BiquadFilter();
                    // 初始化滤波器为0dB增益（无EQ效果），首次调用直接对齐工作系数
                    filters[c][i].setPeakingEQ(ExoConfig.EQ_CENTER_FREQUENCIES[i], sampleRate, ExoConfig.EQ_QUALITY_FACTOR, 0);
                }
            }
        }

        // 批量更新滤波器目标系数（峰值EQ）
        for (int c = 0; c < channelCount; c++) {
            for (int i = 0; i < ExoConfig.EQ_CENTER_FREQUENCIES.length; i++) {
                float freq = ExoConfig.EQ_CENTER_FREQUENCIES[i];
                // 跳过超出奈奎斯特频率的频段（无法被正确采样，配置无效）
                if (freq > nyquist) {
                    ExoLog.log("ExoEqualizerProcessor 频段 " + freq + "Hz 超出奈奎斯特频率，跳过配置");
                    continue;
                }
                // 设置峰值EQ参数：中心频率、采样率、Q值（1.414=√2，默认值）、目标增益
                // 仅更新目标系数，当前工作系数由Biquad内部平滑过渡
                filters[c][i].setPeakingEQ(freq, sampleRate, ExoConfig.EQ_QUALITY_FACTOR, targetGains[i]);
            }
        }

        ExoLog.log("ExoEqualizerProcessor EQ滤波器目标系数更新完成");
    }

    /**
     * 削波处理：防止音频样本超出范围导致破音，支持硬削波和软削波
     *
     * @param sample 输入浮点型音频样本（[-1,1]）
     * @return 削波后的浮点型音频样本
     */
    private float applyClipping(float sample) {
        float threshold = ExoConfig.EQ_CLIPPING_THRESHOLD;
        if (!ExoConfig.EQ_USE_SOFT_CLIPPING) {
            // 硬削波：直接截断超出阈值的样本，效率高，存在轻微失真
            return Math.max(-threshold, Math.min(threshold, sample));
        } else {
            // 软削波：S型曲线平滑压缩超出阈值的样本，失真小，效率略低
            if (sample > threshold) {
                return threshold - (float) Math.pow(sample - threshold, 2) / (4 * threshold);
            } else if (sample < -threshold) {
                return -threshold + (float) Math.pow(sample + threshold, 2) / (4 * threshold);
            } else {
                return sample; // 未超出阈值，直接返回原样本
            }
        }
    }

    /**
     * 重置处理器（管线重建时调用）
     * 仅重置状态变量（更新标记、激活状态），保留目标增益配置
     * 与disableEQ()的核心区别：不修改业务配置（targetGains），仅恢复初始状态
     */
    @Override
    protected void onReset() {
        eqLock.lock();
        try {
            super.onReset();
            // 仅重置状态变量，保留targetGains数组（管线重建后可恢复原有EQ配置）
            this.isPendingUpdate = true;
            this.isEqActive = false;
            if (filters != null) {
                for (BiquadFilter[] channelFilters : filters) {
                    if (channelFilters != null) {
                        for (BiquadFilter filter : channelFilters) {
                            if (filter != null) {
                                filter.reset();
                            }
                        }
                    }
                }
            }
            ExoLog.log("ExoEqualizerProcessor 管线重建，调用滤波器全量reset");
        } finally {
            eqLock.unlock();
        }
    }


    @Override
    protected void onConfigChanged() {
        // 当采样率重新配置（刷新播放）时，强制标记需要更新系数
        eqLock.lock();
        try {
            this.isPendingUpdate = true;
        } finally {
            eqLock.unlock();
        }
    }

    @Override
    protected void onFlush() {
        eqLock.lock();
        try {
            // 强制清空当前滤波器引用，触发下一次 process 重新初始化
            this.filters = null;
            this.isPendingUpdate = true;
            checkIfActive();
            // 若滤波器已初始化，先重置所有滤波器的内部延迟状态，消除残音
            if (filters != null) {
                for (BiquadFilter[] channelFilters : filters) {
                    if (channelFilters != null) {
                        for (BiquadFilter filter : channelFilters) {
                            if (filter != null) {
                                filter.resetInternalStates();
                            }
                        }
                    }
                }
            }

            ExoLog.log("ExoEqualizerProcessor 执行 Flush，强制重置滤波器引用以对齐管线");
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 释放处理器资源（销毁时调用）
     * 执行流程：销毁滤波器数组、清零目标增益，避免内存泄漏
     */
    @Override
    protected void releaseResources() {
        eqLock.lock();
        try {
            if (filters != null) {
                // 销毁每个声道的滤波器数组
                for (BiquadFilter[] channelFilters : filters) {
                    if (channelFilters != null) {
                        for (int i = 0; i < channelFilters.length; i++) {
                            BiquadFilter filter = channelFilters[i];
                            if (filter != null) {
                                // 重置滤波器内部状态
                                filter.reset();
                                channelFilters[i] = null;
                            }
                        }
                        // 清空一维数组引用
                        Arrays.fill(channelFilters, null);
                    }
                }
                filters = null;
            }
            // 清零目标增益
            if (targetGains != null) {
                Arrays.fill(targetGains, 0);
                targetGains = null;
            }
        } finally {
            eqLock.unlock();
        }
    }

    /**
     * 获取指定频段的目标增益（供外部查询，线程安全）
     *
     * @param bandIndex 频段索引（0-9）
     * @return 该频段的目标增益（单位：dB），索引无效时返回0
     */
    public float getTargetGain(int bandIndex) {
        // 频段索引合法性校验
        if (bandIndex < 0 || bandIndex >= targetGains.length) {
            return 0;
        }
        eqLock.lock();
        try {
            return targetGains[bandIndex];
        } finally {
            eqLock.unlock();
        }
    }
}