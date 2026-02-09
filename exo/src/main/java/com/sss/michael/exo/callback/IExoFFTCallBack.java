package com.sss.michael.exo.callback;

/**
 * FFT数据回调监听器
 * 包含FFT原始数据回调和幅度数据回调
 */
public interface IExoFFTCallBack {
    /**
     * FFT原始数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     * @param fft          FFT原始数据数组
     */
    void onFFTReady(int sampleRateHz, int channelCount, float[] fft);

    /**
     * 频谱幅度数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param magnitude    频谱幅度数组
     */
    void onMagnitudeReady(int sampleRateHz, float[] magnitude);
}