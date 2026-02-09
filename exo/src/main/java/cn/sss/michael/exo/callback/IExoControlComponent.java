package cn.sss.michael.exo.callback;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author Michael by SSS
 * @date 2025/12/25 0025 21:38
 * @Description 可继承此接口实现自己的控制ui，以及监听播放器的状态
 */
public interface IExoControlComponent extends IExoNotifyCallBack, IExoGestureCallBack, IExoFFTCallBack {
    /**
     * 将 ControlWrapper 传递到当前 ControlComponent 中
     */
    void attach(@NonNull ExoControllerWrapper exoControllerWrapper);

    /**
     * 如果 IExoControlComponent 是 View，返回当前控件（this）即可
     * 如果不是，返回null
     */
    @Nullable
    View getView();

    /**
     * 继续播放提示
     *
     * @param tip 继续播放提示文本
     */
    void setPlayLocationLastTime(String tip);
}
