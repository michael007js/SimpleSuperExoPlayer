package cn.sss.michael.exo.callback;

import androidx.annotation.IntRange;

public interface IExoScaleCallBack {
    /**
     * 设置播放器（playerView）和容器（playerContainer）的整体缩放比例
     *
     * @param scale 目标缩放比例
     */
    void setPlayerScale(@IntRange(from = 0, to = 100) int scale);

    /**
     * 放大播放器
     */
    void zoomIn();

    /**
     * 缩小播放器
     */
    void zoomOut();

    /**
     * 重置缩放比例
     */
    void resetScale();

    /**
     * 获取当前缩放比例
     *
     * @return 当前缩放值
     */
    float getCurrentScale();

}
