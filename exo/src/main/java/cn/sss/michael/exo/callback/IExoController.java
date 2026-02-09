package cn.sss.michael.exo.callback;

import androidx.annotation.NonNull;

import cn.sss.michael.exo.constant.ExoCoreScale;
import cn.sss.michael.exo.constant.ExoEqualizerPreset;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;

/**
 * @author Michael by SSS
 * @date 2025/12/24 0024 20:13
 * @Description 播放器核心控制接口，用于解耦 UI 层/手势层与底层的 ExoPlayer 实现。
 */
public interface IExoController {
    /**
     * 设置均衡器
     *
     * @param exoEqualizerPreset 均衡器预设值
     */
    void setEqualizer(@NonNull ExoEqualizerPreset exoEqualizerPreset);

    /**
     * 重新播放
     */
    void rePlay();

    /**
     * 准备完成后开始自动播放
     *
     * @param playWhenReady true准备好后开始播放
     */
    void setPlayWhenReady(boolean playWhenReady);

    /**
     * 开始播放指定的媒体资源
     *
     * @param mode         模式
     * @param lastPlayTime 上次播放和时间(仅非直播模式下有效)
     * @param url          视频流地址（支持 HLS, Dash, MP4 等）
     */
    void play(ExoPlayMode mode, long lastPlayTime, String url);

    /**
     * 重置播放器
     * 立即停止所有播放、缓冲、重试等动作，清空媒体资源，重置所有播放状态
     */
    void reset();

    /**
     * 刷新播放（不释放资源）
     * 逻辑：记录当前位置 -> 重新构建数据源 -> 准备播放器 -> 跳转回记录的位置
     */
    void refresh();

    /**
     * 暂停播放
     *
     * @param callFromActive 主动操作，如果缓冲结束，将不播放，需要手动继续
     */
    void pause(boolean callFromActive);

    /**
     * 恢复播放（在暂停状态下调用）
     */
    void resume();

    /**
     * 停止播放
     * 通常用于切换视频或退出当前页面，会重置播放状态
     */
    void stop();

    /**
     * 当前是否正在播放
     *
     * @return true 表示正在播放，false 表示暂停或缓冲中
     */
    boolean isPlaying();

    /**
     * 获取当前视频的总时长
     *
     * @return 单位：毫秒（ms）。如果视频尚未加载完成，可能返回 0 或负值。
     */
    long getDuration();

    /**
     * 跳转到指定播放位置
     *
     * @param positionMs 目标位置的时间戳，单位：毫秒（ms）
     */
    void seekTo(long positionMs);

    /**
     * 获取当前已经播放到的位置
     *
     * @return 单位：毫秒（ms）
     */
    long getCurrentPosition();

    /**
     * 设置视频的缩放/拉伸模式
     *
     * @param mode 取值 {@link ExoCoreScale}中
     */
    void setScaleMode(int mode);

    /**
     * 获取当前视频应用的缩放模式
     *
     * @return 对应的模式枚举值
     */
    int getScaleMode();

    /**
     * 设置播放速度
     *
     * @param speed 速度
     */
    void setSpeed(float speed);

    /**
     * 获取播放速度
     *
     * @return 速度
     */
    float getSpeed();

    /**
     * 开始全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到90度
     */
    void startFullScreen(boolean callFromActive);

    /**
     * 停止全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到0度
     */
    void stopFullScreen(boolean callFromActive);

    /**
     * 是否全屏
     *
     * @return 全屏模式
     */
    boolean isFullScreen();

    /**
     * 获取播放器实时信息
     *
     * @return 播放器实时信息
     */
    ExoPlayerInfo getExoPlayerInfo();

    /**
     * 设置试看时间
     * 仅针对于本次播放链接有效
     *
     * @param experienceTimeMs 试看时间 大于0有效
     */
    void setExperienceTime(long experienceTimeMs);

}