package cn.sss.michael.exo.processor;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by SSS
 * @date 2026/1/4 0004 20:27
 * @Description 滤波器
 */
final class BiquadFilter {
    // 当前工作系数（实际参与滤波计算，渐进逼近目标系数）
    private double b0, b1, b2, a1, a2;
    // 目标系数（由EQ参数计算得出，瞬时更新，不直接参与滤波）
    private double tb0, tb1, tb2, ta1, ta2;
    // 滤波器内部状态（保留延迟状态，无reset，避免爆音）
    private double x1, x2, y1, y2;
    // 初始化标记（解决首次系数未对齐问题，仅执行一次）
    private boolean isInitialized = false;

    /**
     * 配置Peaking EQ（首次调用时直接对齐工作系数与目标系数，无迟滞感）
     *
     * @param freq       中心频率(Hz)
     * @param sampleRate 采样率(Hz)
     * @param Q          品质因子
     * @param dbGain     增益(dB)
     */
    void setPeakingEQ(double freq, double sampleRate, double Q, double dbGain) {
        // 参数合法性校验
        if (freq <= 0 || sampleRate <= 0 || Q <= 0) {
            ExoLog.log("BiquadFilter 无效参数：freq>0、sampleRate>0、Q>0，跳过配置");
            return;
        }

        // 计算归一化角频率
        double w0 = 2 * Math.PI * freq / sampleRate;
        // 计算正弦/余弦值
        double sinW0 = Math.sin(w0);
        double cosW0 = Math.cos(w0);
        // 计算alpha（带宽系数）
        double alpha = sinW0 / (2 * Q);
        // 计算增益幅度
        double A = Math.pow(10, dbGain / 20);
        // 计算目标系数（瞬时更新，无平滑，仅作为收敛方向）
        double numeratorB0 = 1 + alpha * A;
        double numeratorB1 = -2 * cosW0;
        double numeratorB2 = 1 - alpha * A;
        double denominatorA0 = 1 + alpha / A;
        double denominatorA1 = -2 * cosW0;
        double denominatorA2 = 1 - alpha / A;

        // 归一化目标系数（a0作为分母，简化滤波公式）
        this.tb0 = numeratorB0 / denominatorA0;
        this.tb1 = numeratorB1 / denominatorA0;
        this.tb2 = numeratorB2 / denominatorA0;
        this.ta1 = denominatorA1 / denominatorA0;
        this.ta2 = denominatorA2 / denominatorA0;

        // 首次调用时，直接对齐工作系数与目标系数，无迟滞感
        if (!isInitialized) {
            this.b0 = this.tb0;
            this.b1 = this.tb1;
            this.b2 = this.tb2;
            this.a1 = this.ta1;
            this.a2 = this.ta2;
            this.isInitialized = true; // 标记为已初始化，后续仅走平滑
            ExoLog.log("BiquadFilter 首次初始化，系数直接对齐目标值，无迟滞");
        }
    }

    /**
     * Sample级处理（先平滑系数，再滤波，唯一平滑点）
     *
     * @param in 输入音频样本
     * @return 输出音频样本
     */
    float process(float in) {
        // 系数平滑插值（指数插值，sample级更新，唯一平滑点）
        smoothCoefficients();
        // Biquad滤波核心公式（直接型II，数值稳定性更高）
        double out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        // 更新延迟状态（保留历史状态，无reset，避免相位突变）
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;

        return (float) out;
    }

    /**
     * 系数平滑（指数插值，渐进逼近目标系数，无突变）
     * 公式：current = current + (target - current) * smoothFactor
     * 特点：sample级更新，相位连续，无高频呼吸感
     */
    private void smoothCoefficients() {
        // 对每个系数独立平滑，达到阈值后直接收敛
        if (Math.abs(b0 - tb0) > ExoConfig.FILTER_COEFF_CONVERGE_THRESHOLD) {
            b0 += (tb0 - b0) * ExoConfig.FILTER_COEFF_SMOOTH_FACTOR;
        } else {
            b0 = tb0;
        }

        if (Math.abs(b1 - tb1) > ExoConfig.FILTER_COEFF_CONVERGE_THRESHOLD) {
            b1 += (tb1 - b1) * ExoConfig.FILTER_COEFF_SMOOTH_FACTOR;
        } else {
            b1 = tb1;
        }

        if (Math.abs(b2 - tb2) > ExoConfig.FILTER_COEFF_CONVERGE_THRESHOLD) {
            b2 += (tb2 - b2) * ExoConfig.FILTER_COEFF_SMOOTH_FACTOR;
        } else {
            b2 = tb2;
        }

        if (Math.abs(a1 - ta1) > ExoConfig.FILTER_COEFF_CONVERGE_THRESHOLD) {
            a1 += (ta1 - a1) * ExoConfig.FILTER_COEFF_SMOOTH_FACTOR;
        } else {
            a1 = ta1;
        }

        if (Math.abs(a2 - ta2) > ExoConfig.FILTER_COEFF_CONVERGE_THRESHOLD) {
            a2 += (ta2 - a2) * ExoConfig.FILTER_COEFF_SMOOTH_FACTOR;
        } else {
            a2 = ta2;
        }
    }

    /**
     * 基于系数状态判断是否旁路（DAW级细节）
     * 替代基于UI gain的判断，避免系数收敛途中切断IIR，防止相位尾音不连续
     *
     * @return true=旁路（直通，无滤波效果），false=需要滤波
     */
    boolean isBypassed() {
        return Math.abs(b0 - 1.0) < ExoConfig.FILTER_BYPASS_EPS
                && Math.abs(b1) < ExoConfig.FILTER_BYPASS_EPS
                && Math.abs(b2) < ExoConfig.FILTER_BYPASS_EPS
                && Math.abs(a1) < ExoConfig.FILTER_BYPASS_EPS
                && Math.abs(a2) < ExoConfig.FILTER_BYPASS_EPS;
    }

    /**
     * 仅重置滤波器内部延迟状态（专为 onFlush() 设计）
     * 不改动系数（b0/b1/b2/a1/a2）和初始化标记，仅清空x1/x2/y1/y2，解决残音问题
     */
    void resetInternalStates() {
        // 仅清零延迟状态变量，消除上一段音频的末尾余音
        this.x1 = 0.0;
        this.x2 = 0.0;
        this.y1 = 0.0;
        this.y2 = 0.0;
        ExoLog.log("BiquadFilter 内部延迟状态已重置，消除Seek残音");
    }

    /**
     * 重置滤波器状态（仅用于管线重建，不影响正常使用）
     * 保留接口，重置初始化标记，支持重新初始化
     */
    void reset() {
        // 重置系数
        b0 = 1;
        b1 = 0;
        b2 = 0;
        a1 = 0;
        a2 = 0;
        tb0 = 1;
        tb1 = 0;
        tb2 = 0;
        ta1 = 0;
        ta2 = 0;
        // 重置延迟状态
        x1 = 0;
        x2 = 0;
        y1 = 0;
        y2 = 0;
        // 重置初始化标记，支持重新对齐系数
        isInitialized = false;
    }
}