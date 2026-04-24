package com.sss.michael.exo.processor;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.constant.ExoEqualizerPreset;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 独立 PCM 流式链路使用的音频处理链。
 *
 * <p>库内已经具备面向 ExoPlayer 的均衡器和频谱处理器。本类的职责不是重新实现一套 DSP，
 * 而是把既有处理器适配到独立的 AudioTrack 流式链路中，使 URL 主链和 PCM 主链共享同一套
 * 音频处理能力。
 *
 * <p>该处理链按“单次会话”维度创建：初始化时绑定固定 PCM 格式，后续由 PCM 工作线程在写入
 * AudioTrack 之前逐块处理输入音频。
 */
@UnstableApi
public class ExoStandaloneAudioProcessorChain {

    private final List<ExoBaseAudioProcessor> audioProcessors = new ArrayList<>();
    private final ExoEqualizerProcessor equalizerProcessor;

    /**
     * 创建并初始化独立音频处理链。
     *
     * @param sampleRateHz PCM 采样率
     * @param channelCount PCM 声道数
     * @param iExoFFTCallBack 频谱处理器启用时使用的 FFT 回调
     * @throws AudioProcessor.UnhandledAudioFormatException 当现有处理器无法处理该 PCM 格式时抛出
     */
    public ExoStandaloneAudioProcessorChain(int sampleRateHz, int channelCount,
                                            IExoFFTCallBack iExoFFTCallBack)
            throws AudioProcessor.UnhandledAudioFormatException {
        AudioProcessor.AudioFormat inputFormat =
                new AudioProcessor.AudioFormat(sampleRateHz, channelCount, C.ENCODING_PCM_16BIT);

        if (ExoConfig.COMPONENT_EQ_ENABLE) {
            equalizerProcessor = new ExoEqualizerProcessor();
            equalizerProcessor.setBandGains(ExoEqualizerPreset.CUSTOM.getGains());
            equalizerProcessor.configure(inputFormat);
            equalizerProcessor.flush();
            audioProcessors.add(equalizerProcessor);
        } else {
            equalizerProcessor = null;
        }

        if (ExoConfig.COMPONENT_SPECTRUM_ENABLE) {
            ExoSpectrumProcessor spectrumProcessor = new ExoSpectrumProcessor();
            spectrumProcessor.setExoFFTCallBack(iExoFFTCallBack);
            spectrumProcessor.configure(inputFormat);
            spectrumProcessor.flush();
            audioProcessors.add(spectrumProcessor);
        }
    }

    /**
     * 将当前处理链应用到指定 PCM 数据区间。
     *
     * <p>返回值始终是新的字节数组，因为 AudioTrack 链路在方法返回后会继续持有处理结果。
     * 这样可以避免生产线程、DSP 层和 AudioTrack 工作线程共享同一块可变缓冲区。
     *
     * @param data 源 PCM 数据数组
     * @param offset 本次处理的起始偏移
     * @param length 本次需要处理的字节数
     * @return 处理完成后的新 PCM 字节数组
     */
    @NonNull
    public byte[] process(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return new byte[0];
        }
        if (audioProcessors.isEmpty()) {
            return Arrays.copyOfRange(data, offset, offset + length);
        }

        byte[] stageBytes = Arrays.copyOfRange(data, offset, offset + length);
        for (ExoBaseAudioProcessor audioProcessor : audioProcessors) {
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(stageBytes.length)
                    .order(ByteOrder.nativeOrder());
            inputBuffer.put(stageBytes);
            inputBuffer.flip();

            audioProcessor.queueInput(inputBuffer);

            ByteBuffer outputBuffer = audioProcessor.getOutput();
            byte[] outputBytes = new byte[outputBuffer.remaining()];
            outputBuffer.get(outputBytes);
            stageBytes = outputBytes;
        }
        return stageBytes;
    }

    /**
     * 更新共享均衡器处理器的预设。
     *
     * @param exoEqualizerPreset 后续 PCM 分片应采用的均衡器预设
     */
    public void setEqualizer(ExoEqualizerPreset exoEqualizerPreset) {
        if (equalizerProcessor == null || exoEqualizerPreset == null) {
            return;
        }
        equalizerProcessor.setBandGains(exoEqualizerPreset.getGains());
    }

    /**
     * 清空处理器内部状态，使新流会话从干净的 DSP 基线开始。
     */
    public void flush() {
        for (ExoBaseAudioProcessor audioProcessor : audioProcessors) {
            audioProcessor.flush();
        }
    }

    /**
     * 释放处理链持有的全部资源。
     *
     * <p>调用后该实例即视为不可再用。
     */
    public void release() {
        for (ExoBaseAudioProcessor audioProcessor : audioProcessors) {
            audioProcessor.release();
        }
        audioProcessors.clear();
    }
}
