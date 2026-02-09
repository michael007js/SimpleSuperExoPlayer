package cn.sss.michael.exo.factory;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;

import cn.sss.michael.exo.constant.ExoPlayMode;

/**
 * @author Michael by 61642
 * @date 2025/12/29 15:42
 * @Description 根据 ExoPlayMode 动态创建对应的 LoadControl
 */
public class ExoLoadControlFactory {

    /**
     * 获取差异化配置的 LoadControl
     *
     * @param playMode 播放模式（SHORT_VIDEO/LIVE/VOD）
     * @return 对应配置的 LoadControl
     */
    @OptIn(markerClass = UnstableApi.class)
    public static LoadControl getLoadControlByPlayMode(ExoPlayMode playMode) {
        int minBufferMs;       // 最小缓冲区：播放器至少要缓冲这么久，才会进入播放状态
        int maxBufferMs;       // 最大缓冲区：播放器最多缓冲这么久，避免占用过多内存
        int bufferForPlaybackMs; // 播放缓冲区：满足该时长即可触发播放（首屏启动速度关键）
        int bufferForPlaybackAfterRebufferMs; // 重新缓冲后播放缓冲区：缓冲耗尽后，需满足该时长才恢复播放

        switch (playMode) {
            case SHORT_VIDEO:
                // 短视频：优先快速启动，缓冲参数最小化
                minBufferMs = 1000;      // 最小缓冲区 1s
                maxBufferMs = 3000;      // 最大缓冲区 3s（避免占用过多内存，毕竟短视频时长短）
                bufferForPlaybackMs = 500; // 播放缓冲区 500ms（首屏极速启动）
                bufferForPlaybackAfterRebufferMs = 1000; // 重新缓冲后 1s 即可播放
                break;
            case LIVE:
                // 直播：兼顾实时性和抗波动，缓冲适中
                minBufferMs = 3000;      // 最小缓冲区 3s
                maxBufferMs = 10000;     // 最大缓冲区 10s（避免缓冲过多导致直播延迟过高）
                bufferForPlaybackMs = 1000; // 播放缓冲区 1s（快速启动，兼顾实时性）
                bufferForPlaybackAfterRebufferMs = 2000; // 重新缓冲后 2s 恢复播放，减少卡顿感知
                break;
            case VOD:
                // 点播：优先流畅无卡顿，缓冲参数最大化
                minBufferMs = 5000;      // 最小缓冲区 5s
                maxBufferMs = 15000;     // 最大缓冲区 15s
                bufferForPlaybackMs = 2000; // 播放缓冲区 2s
                bufferForPlaybackAfterRebufferMs = 3000; // 重新缓冲后 3s 恢复播放
                break;
            default:
                // 默认使用点播配置，保证兼容性
                minBufferMs = 5000;
                maxBufferMs = 15000;
                bufferForPlaybackMs = 2000;
                bufferForPlaybackAfterRebufferMs = 3000;
                break;
        }

        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBufferMs,
                        maxBufferMs,
                        bufferForPlaybackMs,
                        bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true) // 优先保证缓冲时长，而非缓冲大小
                .build();
    }
}