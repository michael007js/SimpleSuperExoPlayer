package com.sss.michael.exo.callback;

import android.view.View;

import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.core.ExoPlayerInfo;

import java.util.List;

/**
 * @author Michael by 61642
 * @date 2025/12/25 16:29
 * @Description 播放器核心回调接口
 */
public interface IExoNotifyCallBack {

    /**
     * 获取所有控制组件
     *
     * @return 组件
     */
    List<IExoControlComponent> getExoComponents();

    /**
     * 按类获取控制组件
     *
     * @param cls 组件类
     * @param <T> 组件类型
     * @return 组件
     */
    <T extends IExoControlComponent> T getExoControlComponentByClass(Class<T> cls);


    /**
     * 当视频的第一帧像素真正渲染到 Surface 上时回调
     */
    void onExoRenderedFirstFrame();

    /**
     * 播放器信息被改变时回调
     *
     * @param exoPlayerInfo 播放器实时信息
     */
    void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo);


    /**
     * 流量被改变时回调
     *
     * @param bytesInLastSecond 最后一秒的字节数
     * @param totalBytes        全部字节数
     */
    void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes);

    /**
     * 播放进度被改变
     *
     * @param currentMs          当前进度（毫秒）
     * @param durationMs         总时间（毫秒）
     * @param bufferedPositionMs 缓冲位置（毫秒）
     * @param bufferedPercentage 缓冲百分比
     */
    void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage);

    /**
     * 播放状态被改变时回调
     *
     * @param playbackState     状态码
     * @param playbackStateName 状态名
     */
    void onPlaybackStateChanged(int playbackState, String playbackStateName);

    /**
     * 播放器形态被改变时回调
     *
     * @param playerState     播放器形态码
     * @param playerStateName 播放器形态名
     * @param playerView      播放器视图
     */
    void onPlayerStateChanged(int playerState, String playerStateName, View playerView);

    /**
     * 当播放发生错误时回调
     *
     * @param errorMsg  错误描述
     * @param throwable 抛出异常
     */
    void onPlayerError(String errorMsg, Throwable throwable);

    /**
     * 视频大小改变时回调
     *
     * @param view                  播放器视图 TextureView
     * @param pixelWidthHeightRatio 像素宽高比
     * @param videoWidth            视频宽度
     * @param videoHeight           视频高度
     * @param scaleMode             缩放模式 见{@link ExoCoreScale}
     */
    void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode);

    /**
     * 试看时间结束
     */
    void onExperienceTimeout();


    /**
     * 短视频组件有更改意图时回调（涉及外部列表适配器交互，外部组件的更改动作由外部执行）
     *
     * @param clearScreenMode                   清屏模式
     * @param exoShortVideoSimpleControlBarView 清屏控制组件
     */
    void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView);

}
