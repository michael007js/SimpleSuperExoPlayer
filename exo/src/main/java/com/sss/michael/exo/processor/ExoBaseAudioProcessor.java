package com.sss.michael.exo.processor;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

/**
 * @author Michael by SSS
 * @date 2026/1/2 21:10
 * @Description 音频处理器基类
 */
@UnstableApi
public abstract class ExoBaseAudioProcessor extends BaseAudioProcessor {

    protected int sampleRateHz = C.RATE_UNSET_INT;
    protected int channelCount = C.LENGTH_UNSET;
    protected int encoding = C.ENCODING_INVALID;

    @NonNull
    @Override
    public AudioFormat onConfigure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        // 目前仅处理最通用的 16-bit PCM
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        this.sampleRateHz = inputAudioFormat.sampleRate;
        this.channelCount = inputAudioFormat.channelCount;
        this.encoding = inputAudioFormat.encoding;
        onConfigChanged();
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) {
            return;
        }
        ByteBuffer outputBuffer = replaceOutputBuffer(remaining);
        if (sampleRateHz == C.RATE_UNSET_INT || channelCount == C.LENGTH_UNSET) {
            outputBuffer.put(inputBuffer); // 配置未就绪时直接透传，不处理
            return;
        }
        process(inputBuffer, outputBuffer, sampleRateHz, channelCount);
        outputBuffer.flip();
    }

    /**
     * 核心处理方法
     *
     * @param input  输入数据 (请使用 get 读取，不要修改 position，或者读取后手动还原)
     * @param output 输出数据 (请使用 put 写入)
     */
    protected abstract void process(ByteBuffer input, ByteBuffer output, int sampleRateHz, int channelCount);

    /**
     * 只有在播放器彻底销毁（release）时才重置所有参数
     */
    @Override
    protected void onReset() {
        sampleRateHz = C.RATE_UNSET_INT;
        channelCount = C.LENGTH_UNSET;
        encoding = C.ENCODING_INVALID;
    }


    /**
     * 内存泄漏防护，显式释放资源
     */
    public void release() {
        onReset();
        releaseResources();
    }

    protected void onConfigChanged() {
    }

    /**
     * 释放自身持有的大资源（数组、缓冲区等）
     */
    protected abstract void releaseResources();

}