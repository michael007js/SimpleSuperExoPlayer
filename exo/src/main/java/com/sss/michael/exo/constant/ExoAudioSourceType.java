package com.sss.michael.exo.constant;

/**
 * 标识当前播放器状态由哪条音频输入链路接管。
 *
 * <p>库原本只有基于 URL / MediaSource 的播放路径。引入 PCM 流式能力后，播放器需要能够区分
 * 当前是 ExoPlayer 主链还是 AudioTrack 流式链在生效，以便调试信息、状态展示和后续扩展逻辑
 * 都能保持明确。
 */
public final class ExoAudioSourceType {

    /**
     * 传统 URL / MediaSource 播放链路。
     */
    public static final int URL = 0;

    /**
     * 由外部持续推送原始 PCM 数据的流式播放链路。
     */
    public static final int PCM_STREAM = 1;

    private ExoAudioSourceType() {
    }
}
