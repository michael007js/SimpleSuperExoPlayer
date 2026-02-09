package com.sss.michael.exo.helper;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by 61642
 * @date 2026/2/4 13:35
 * @Description 播放器整体缩放助手类（容器+视图）
 */
public abstract class ExoScaleHelper {
    // 默认缩放比例
    public static final float DEFAULT_SCALE = 1.0f;
    // 最小缩放比例
    public static final float MIN_SCALE = 0.5f;
    // 最大缩放比例
    public static final float MAX_SCALE = 2.0f;
    // 每次缩放步长（放大/缩小的增量）
    public static final float SCALE_STEP = 0.1f;

    // 播放器容器
    protected abstract ViewGroup getPlayerContainer();

    // 播放器视图
    protected abstract View getPlayerView();

    // 当前缩放比例
    private float mCurrentScale = DEFAULT_SCALE;
    // 主线程
    private android.os.Handler mMainHandler;
    // 是否已初始化缩放中心点
    private boolean isPivotInitialized = false;

    /**
     * 构造方法
     */
    public ExoScaleHelper() {
        this.mMainHandler = new android.os.Handler(Looper.getMainLooper());

        if (getPlayerContainer() == null || getPlayerView() == null) {
            ExoLog.log("ExoScaleHelper：容器/视图未初始化，延迟初始化缩放参数");
            return;
        }

        // 监听布局完成，设置缩放中心点（避免缩放偏移）
        getPlayerContainer().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (getPlayerContainer() == null || getPlayerView() == null) {
                    return;
                }

                // 移除监听，避免重复执行
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    getPlayerContainer().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    getPlayerContainer().getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }

                // 设置中心点为控件中心（视觉缩放的核心，避免缩放偏移）
                setPivotToCenter();
                isPivotInitialized = true;

                ExoLog.log("ExoScaleHelper：缩放中心点初始化完成");
            }
        });
    }

    /**
     * 设置缩放中心点为View中心
     */
    private void setPivotToCenter() {
        ViewGroup container = getPlayerContainer();
        View playerView = getPlayerView();

        if (container != null && container.getWidth() > 0 && container.getHeight() > 0) {
            container.setPivotX(container.getWidth() / 2f);
            container.setPivotY(container.getHeight() / 2f);
        }

        if (playerView != null && playerView.getWidth() > 0 && playerView.getHeight() > 0) {
            playerView.setPivotX(playerView.getWidth() / 2f);
            playerView.setPivotY(playerView.getHeight() / 2f);
        }
    }

    /**
     * 设置自定义缩放比例
     *
     * @param targetScale 目标缩放比例（自动限制在 MIN_SCALE ~ MAX_SCALE 范围内）
     */
    public void setScale(float targetScale) {
        // 确保UI操作在主线程执行
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> setScale(targetScale));
            return;
        }

        if (!checkInitState()) {
            return;
        }

        // 限制缩放范围
        float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, targetScale));
        if (scale == mCurrentScale) {
            ExoLog.log("ExoScaleHelper：当前缩放比例已为" + scale + "，无需调整");
            return;
        }

        // 仅修改视觉缩放（scaleX/scaleY），移除布局参数修改
        ViewGroup container = getPlayerContainer();
        View playerView = getPlayerView();

        if (container != null) {
            container.setScaleX(scale);
            container.setScaleY(scale);
        }

        if (playerView != null) {
            playerView.setScaleX(scale);
            playerView.setScaleY(scale);
        }

        // 更新当前缩放比例
        mCurrentScale = scale;
        ExoLog.log("ExoScaleHelper：缩放完成，当前比例：" + scale);
    }

    /**
     * 放大播放器（每次增加 SCALE_STEP）
     */
    public void zoomIn() {
        setScale(mCurrentScale + SCALE_STEP);
    }

    /**
     * 缩小播放器（每次减少 SCALE_STEP）
     */
    public void zoomOut() {
        setScale(mCurrentScale - SCALE_STEP);
    }

    /**
     * 重置缩放比例为默认值（DEFAULT_SCALE）
     */
    public void resetScale() {
        if (!checkInitState()) {
            return;
        }

        // 恢复视觉缩放系数
        ViewGroup container = getPlayerContainer();
        View playerView = getPlayerView();

        if (container != null) {
            container.setScaleX(DEFAULT_SCALE);
            container.setScaleY(DEFAULT_SCALE);
        }

        if (playerView != null) {
            playerView.setScaleX(DEFAULT_SCALE);
            playerView.setScaleY(DEFAULT_SCALE);
        }

        // 重置变量
        mCurrentScale = DEFAULT_SCALE;
        ExoLog.log("ExoScaleHelper：缩放已重置为默认比例：" + DEFAULT_SCALE);
    }

    /**
     * 检查初始化状态（容器/视图是否有效）
     *
     * @return true=初始化完成，false=未初始化
     */
    private boolean checkInitState() {
        ViewGroup container = getPlayerContainer();
        View playerView = getPlayerView();

        if (container == null || playerView == null) {
            ExoLog.log("ExoScaleHelper：容器/视图未初始化，跳过缩放操作");
            return false;
        }

        // 如果缩放中心点未初始化，强制重新设置
        if (!isPivotInitialized) {
            setPivotToCenter();
            isPivotInitialized = true;
        }

        return true;
    }

    /**
     * 获取当前缩放比例
     *
     * @return 当前缩放值
     */
    public float getCurrentScale() {
        return mCurrentScale;
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
            mMainHandler = null;
        }
        mCurrentScale = DEFAULT_SCALE;
        isPivotInitialized = false;
    }
}