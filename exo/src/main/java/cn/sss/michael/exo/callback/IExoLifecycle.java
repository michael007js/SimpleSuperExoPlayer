package cn.sss.michael.exo.callback;

import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * @author Michael by 61642
 * @date 2025/12/25 16:03
 * @Description 生命周期相关。与父控制器、父容器的主动调用相关
 */
public interface IExoLifecycle {
    /**
     * 窗口焦点已更改
     * 由{@link View#onWindowFocusChanged(boolean)}触发回调后调用
     *
     * @param hasWindowFocus 是否具有窗口焦点
     */
    void windowFocusChanged(boolean hasWindowFocus);

    /**
     * 绑定Surface
     * 由{@link android.view.TextureView} 的 onSurfaceTextureAvailable 函数回调后调用
     *
     * @param surface Surface
     */
    void bindSurfaceWhileTextureAvailable(@NonNull Surface surface);

    /**
     * 解绑Surface
     * 由{@link android.view.TextureView} 的 onSurfaceTextureDestroyed 函数回调后调用
     *
     * @param surface Surface
     */
    void unbindSurfaceTextureWhileDestroyed(@NonNull Surface surface);

    /**
     * 首次设置播放器容器
     * 由 触发回调后调用
     *
     * @param playerContainer 播放器父容器
     * @param playerView      播放器视图
     */
    void setPlayerContainerWhileFirstTime(ViewGroup playerContainer, View playerView);

    /**
     * 页面暂停交互时调用
     */
    void onPause();

    /**
     * 页面恢复交互时调用
     */
    void onResume();

    /**
     * 彻底释放播放器资源
     * 在 Activity 或 Fragment 的 onDestroy 中必须调用，防止内存泄漏和解码器占用
     */
    void release();

}
