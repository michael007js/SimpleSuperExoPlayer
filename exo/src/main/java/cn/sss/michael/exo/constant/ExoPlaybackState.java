package cn.sss.michael.exo.constant;

import androidx.media3.common.Player;

/**
 * @author Michael by 61642
 * @date 2025/12/26 11:34
 * @Description 播放状态
 */
public class ExoPlaybackState {
    /**
     * 播放器停止，或未准备 (对齐 Player.STATE_IDLE = 1)
     */
    public static final int STATE_IDLE = Player.STATE_IDLE;

    /**
     * 正在加载数据/缓冲 (对齐 Player.STATE_BUFFERING = 2)
     */
    public static final int STATE_BUFFERING = Player.STATE_BUFFERING;

    /**
     * 数据就绪，可以播放 (对齐 Player.STATE_READY = 3)
     */
    public static final int STATE_READY = Player.STATE_READY;

    /**
     * 播放完成 (对齐 Player.STATE_ENDED = 4)
     */
    public static final int STATE_ENDED = Player.STATE_ENDED;

    /**
     * 正在播放 (基于 STATE_READY + playWhenReady=true)
     */
    public static final int STATE_PLAYING = 5;

    /**
     * 已暂停 (基于 STATE_READY + playWhenReady=false)
     */
    public static final int STATE_PLAY_PAUSE = 6;
}