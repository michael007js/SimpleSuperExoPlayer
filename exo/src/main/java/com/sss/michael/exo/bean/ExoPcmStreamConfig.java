package com.sss.michael.exo.bean;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * PCM 流式播放会话配置。
 *
 * <p>播放器会在启动流式会话时复制该配置对象，因此调用方应在开始播放前一次性准备好格式与缓冲
 * 参数，而不是在会话中途持续修改同一实例。
 *
 * <p>当前版本仅支持 PCM 16-bit 输入。之所以仍暴露 {@code encoding} 字段，是为了让对外格式
 * 契约保持完整；若传入不支持的编码，底层会在校验阶段直接拒绝。
 */
public class ExoPcmStreamConfig {

    /**
     * 默认采样率，适用于腾讯云 TTS 及多数低延迟语音播报场景。
     */
    public static final int DEFAULT_SAMPLE_RATE_HZ = 16000;

    /**
     * 默认声道数，面向语音合成输出。
     */
    public static final int DEFAULT_CHANNEL_COUNT = 1;

    /**
     * 独立处理链与 AudioTrack 输出当前支持的默认 PCM 编码。
     */
    public static final int DEFAULT_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    /**
     * 哨兵值，表示缓冲区大小应由
     * {@link AudioTrack#getMinBufferSize(int, int, int)} 推导后再乘以 2。
     */
    public static final int DEFAULT_BUFFER_SIZE_IN_BYTES = -1;

    /**
     * 默认音频用途，按媒体播放场景输出。
     */
    public static final int DEFAULT_AUDIO_USAGE = AudioAttributes.USAGE_MEDIA;

    /**
     * 默认内容类型，针对语音输出优化。
     */
    public static final int DEFAULT_CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SPEECH;

    /**
     * 触发背压前允许保留在内存中的最大 PCM 排队时长。
     */
    public static final long DEFAULT_MAX_QUEUED_DURATION_MS = 5000L;

    private int sampleRateHz = DEFAULT_SAMPLE_RATE_HZ;
    private int channelCount = DEFAULT_CHANNEL_COUNT;
    private int encoding = DEFAULT_ENCODING;
    private int bufferSizeInBytes = DEFAULT_BUFFER_SIZE_IN_BYTES;
    private int audioUsage = DEFAULT_AUDIO_USAGE;
    private int contentType = DEFAULT_CONTENT_TYPE;
    private long maxQueuedDurationMs = DEFAULT_MAX_QUEUED_DURATION_MS;

    /**
     * 创建一个使用库默认值的配置对象。
     */
    public ExoPcmStreamConfig() {
    }

    /**
     * 复制构造函数，用于在播放层内部固定当前会话的配置快照。
     *
     * @param other 待复制的配置对象
     */
    public ExoPcmStreamConfig(ExoPcmStreamConfig other) {
        if (other == null) {
            return;
        }
        this.sampleRateHz = other.sampleRateHz;
        this.channelCount = other.channelCount;
        this.encoding = other.encoding;
        this.bufferSizeInBytes = other.bufferSizeInBytes;
        this.audioUsage = other.audioUsage;
        this.contentType = other.contentType;
        this.maxQueuedDurationMs = other.maxQueuedDurationMs;
    }

    /**
     * 返回流式会话的采样率。
     */
    public int getSampleRateHz() {
        return sampleRateHz;
    }

    /**
     * 设置流式会话的采样率。
     *
     * @param sampleRateHz 每秒采样点数
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setSampleRateHz(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
        return this;
    }

    /**
     * 返回 PCM 声道数。
     */
    public int getChannelCount() {
        return channelCount;
    }

    /**
     * 设置 PCM 声道数。
     *
     * <p>当前版本仅支持单声道和双声道，其他值会在播放层被拒绝。
     *
     * @param channelCount PCM 流中的声道数量
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setChannelCount(int channelCount) {
        this.channelCount = channelCount;
        return this;
    }

    /**
     * 返回 PCM 编码格式。
     */
    public int getEncoding() {
        return encoding;
    }

    /**
     * 设置 PCM 编码格式。
     *
     * <p>当前版本只接受 {@link AudioFormat#ENCODING_PCM_16BIT}。保留该字段是为了让接入方
     * 显式声明输入格式，而不是隐式依赖默认值。
     *
     * @param encoding PCM 编码常量
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setEncoding(int encoding) {
        this.encoding = encoding;
        return this;
    }

    /**
     * 返回期望的 AudioTrack 缓冲区大小。
     *
     * <p>当返回值 {@code <= 0} 时，表示由实现层基于
     * {@link AudioTrack#getMinBufferSize(int, int, int)} 自动推导。
     */
    public int getBufferSizeInBytes() {
        return bufferSizeInBytes;
    }

    /**
     * 设置期望的 AudioTrack 缓冲区大小。
     *
     * @param bufferSizeInBytes 目标缓冲区大小；当 {@code <= 0} 时使用库默认推导策略
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setBufferSizeInBytes(int bufferSizeInBytes) {
        this.bufferSizeInBytes = bufferSizeInBytes;
        return this;
    }

    /**
     * 返回 Android 音频用途属性。
     */
    public int getAudioUsage() {
        return audioUsage;
    }

    /**
     * 设置 Android 音频用途属性。
     *
     * @param audioUsage 例如 {@link AudioAttributes#USAGE_MEDIA} 这样的用途常量
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setAudioUsage(int audioUsage) {
        this.audioUsage = audioUsage;
        return this;
    }

    /**
     * 返回 Android 内容类型属性。
     */
    public int getContentType() {
        return contentType;
    }

    /**
     * 设置 Android 内容类型属性。
     *
     * @param contentType 例如 {@link AudioAttributes#CONTENT_TYPE_SPEECH} 这样的内容类型常量
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setContentType(int contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * 返回允许保留在内存中的最大 PCM 排队时长。
     */
    public long getMaxQueuedDurationMs() {
        return maxQueuedDurationMs;
    }

    /**
     * 设置触发背压前允许保留的最大 PCM 排队时长。
     *
     * @param maxQueuedDurationMs 最大待播时长，单位毫秒
     * @return 当前配置对象，便于链式调用
     */
    public ExoPcmStreamConfig setMaxQueuedDurationMs(long maxQueuedDurationMs) {
        this.maxQueuedDurationMs = maxQueuedDurationMs;
        return this;
    }

    /**
     * 返回单帧 PCM 数据的字节数。
     *
     * <p>当前版本仅支持 PCM 16-bit，因此帧大小等于 {@code channelCount * 2}。
     */
    public int getBytesPerFrame() {
        return Math.max(1, channelCount) * 2;
    }
}
