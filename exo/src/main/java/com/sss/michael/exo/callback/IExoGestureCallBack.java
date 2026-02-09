package com.sss.michael.exo.callback;

import com.sss.michael.exo.ExoConfig;

/**
 * @author Michael by SSS
 * @date 2025/12/24 0024 20:33
 * @Description 手势回调接口
 */
public interface IExoGestureCallBack {
    /**
     * 进度调节回调
     *
     * @param current 当前播放位置
     * @param total   总时长
     * @param seekTo  手指滑动目标 seek 位置
     */
    void onProgressChange(long current, long total, long seekTo);

    /**
     * 音量调节回调
     *
     * @param current 当前音量
     * @param max     最大音量
     */
    void onVolumeChange(int current, int max);

    /**
     * 亮度调节回调
     *
     * @param percent 亮度百分比 0.0 - 1.0
     */
    void onBrightnessChange(float percent);

    /**
     * 手势触摸开始：第一根手指触摸时触发（ACTION_DOWN）
     * 用于 UI 层显示提示蒙层
     */
    void onGestureStart();

    /**
     * 手势触摸结束：最后一根手指离开时触发（ACTION_UP / ACTION_CANCEL）
     * 用于 UI 层隐藏提示蒙层
     */
    void onGestureEnd();

    /**
     * 手指触摸点击
     *
     * @param fingerCount 手指数量
     * @param singleClick true 单击 false 双击
     */
    void onFingerTouchClick(int fingerCount, boolean singleClick);

    /**
     * 单点触摸回调
     *
     * @param action MotionEvent意图
     * @param rawX   相当于屏幕左上角X值
     * @param rawY   相对于屏幕左上角Y值
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    void onSingleFingerPointTouchEvent(int action, float rawX, float rawY, boolean isEdge);

    /**
     * 缩放回调
     *
     * @param totalScale 所放量 范围受
     *                   {@link ExoConfig#GESTURE_MIN_SCALE}
     *                   {@link ExoConfig#GESTURE_MAX_SCALE}
     *                   限制
     */
    void onScale(float totalScale);

    /**
     * 长按开始（仅考虑单指）
     *
     * @param fingerCount 手指数量
     * @param isEdge      边缘触发
     *                    取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    void onLongPressStart(int fingerCount, boolean isEdge);

    /**
     * 长按结束（手指抬起）
     *
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    void onLongPressEnd(boolean isEdge);
}