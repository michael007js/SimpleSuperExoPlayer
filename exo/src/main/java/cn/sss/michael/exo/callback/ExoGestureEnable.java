package cn.sss.michael.exo.callback;

/**
 * @author Michael by SSS
 * @date 2025/12/31 15:17
 * @Description 手势功能禁用控制
 */
public class ExoGestureEnable {
    /**
     * 禁用长按
     *
     * @return 是否禁用
     */
    public boolean disableLongTap() {
        return false;
    }

    /**
     * 禁用点击手势
     *
     * @return 是否禁用
     */
    public boolean disableTapGesture() {
        return false;
    }

    /**
     * 禁用缩放手势
     *
     * @return 是否禁用
     */
    public boolean disableScaleGesture() {
        return false;
    }

    /**
     * 禁用亮度手势
     *
     * @return 是否禁用
     */
    public boolean disableBrightnessGesture() {
        return false;
    }

    /**
     * 禁用音量手势
     *
     * @return 是否禁用
     */
    public boolean disableVolumeGesture() {
        return false;
    }

    /**
     * 禁用进度手势
     *
     * @return 是否禁用
     */
    public boolean disableProgressChangeGesture() {
        return false;
    }

    /**
     * 禁用长按倍速播放
     *
     * @return 是否禁用
     */
    public boolean disableDoubleSpeedPlayWhileLongTouch() {
        return true;
    }

    /**
     * 禁用边缘下拉手势（仿抖音）
     *
     * @return 是否禁用
     */
    public boolean disableEdgePullDown() {
        return true;
    }

    /**
     * 缩放过程中是否禁用位移
     *
     * @return 是否禁用
     */
    public boolean disableMoveWhileScaling() {
        return false;
    }

}
