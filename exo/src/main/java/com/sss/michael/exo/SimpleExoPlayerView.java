package com.sss.michael.exo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sss.michael.exo.callback.ExoControllerWrapper;
import com.sss.michael.exo.callback.ExoGestureEnable;
import com.sss.michael.exo.callback.IExoControlComponent;
import com.sss.michael.exo.callback.IExoController;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.callback.IExoGestureCallBack;
import com.sss.michael.exo.callback.IExoLifecycle;
import com.sss.michael.exo.callback.IExoNotifyCallBack;
import com.sss.michael.exo.callback.IExoScaleCallBack;
import com.sss.michael.exo.component.ExoComponentCompleteView;
import com.sss.michael.exo.component.ExoComponentDebugControlView;
import com.sss.michael.exo.component.ExoComponentEqView;
import com.sss.michael.exo.component.ExoComponentErrorView;
import com.sss.michael.exo.component.ExoComponentGestureVodControlView;
import com.sss.michael.exo.component.ExoComponentLoadingView;
import com.sss.michael.exo.component.ExoComponentPausedView;
import com.sss.michael.exo.component.ExoComponentSpectrumView;
import com.sss.michael.exo.component.ExoComponentSpeedView;
import com.sss.michael.exo.component.ExoComponentTitleBarView;
import com.sss.michael.exo.component.ExoLiveControlBarView;
import com.sss.michael.exo.component.ExoShortVideoControlBarView;
import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import com.sss.michael.exo.component.ExoVodControlBarView;
import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.core.ExoVideoView;
import com.sss.michael.exo.helper.ExoGestureHelper;
import com.sss.michael.exo.helper.ExoScaleHelper;
import com.sss.michael.exo.util.ExoDensityUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Michael by SSS
 * @date 2025/12/24 0024 19:23
 * @Description 播放器视图
 */
public class SimpleExoPlayerView extends FrameLayout
        implements TextureView.SurfaceTextureListener,
        IExoController,
        IExoGestureCallBack,
        IExoLifecycle,
        IExoFFTCallBack,
        IExoNotifyCallBack,
        IExoScaleCallBack {

    private final ExoControllerWrapper exoControllerWrapper;
    private ExoVideoView exoCore;
    private final TextureView textureView;
    private Surface currentSurface;
    // 手势处理类
    private ExoGestureHelper exoGestureHelper;
    // 缩放助手
    private ExoScaleHelper exoScaleHelper;
    // 保存了所有的控制组件
    protected List<IExoControlComponent> mControlComponents = new ArrayList<>();

    private IExoGestureCallBack iExoGestureCallBack;
    private IExoNotifyCallBack iExoNotifyCallBack;
    private IExoFFTCallBack iExoFFTCallBack;

    public SimpleExoPlayerView(@NonNull Context context) {
        this(context, null);
    }

    public SimpleExoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SimpleExoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(0xff000000);
        textureView = new TextureView(context, null, defStyleAttr);
        addView(textureView);
        textureView.setSurfaceTextureListener(this);
        exoCore = new ExoVideoView(context, this, this);
        exoControllerWrapper = new ExoControllerWrapper(exoCore, this);
        textureView.setOpaque(false);
        exoGestureHelper = new ExoGestureHelper(textureView, context,
                new ExoGestureEnable()
                , exoCore, this);
        exoScaleHelper = new ExoScaleHelper() {
            @Override
            protected ViewGroup getPlayerContainer() {
                return exoCore.getPlayerContainer();
            }

            @Override
            protected View getPlayerView() {
                return exoCore.getPlayerView();
            }
        };
        setScaleMode(ExoCoreScale.SCALE_AUTO);

    }

    // <editor-fold defaultstate="collapsed" desc="原生函数">
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewGroup viewParent = (ViewGroup) getParent();
        setPlayerContainerWhileFirstTime(viewParent, this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        windowFocusChanged(hasWindowFocus);
    }

    @Override
    public void computeScroll() {
        if (exoGestureHelper != null) {
            exoGestureHelper.computeScroll();
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="非回调类对外API函数">

    public void setExoGestureCallBack(IExoGestureCallBack iExoGestureCallBack) {
        this.iExoGestureCallBack = iExoGestureCallBack;
    }

    public void setExoNotifyCallBack(IExoNotifyCallBack iExoNotifyCallBack) {
        this.iExoNotifyCallBack = iExoNotifyCallBack;
    }

    public void setExoFFTCallBack(IExoFFTCallBack iExoFFTCallBack) {
        this.iExoFFTCallBack = iExoFFTCallBack;
    }

    public ExoControllerWrapper getExoControllerWrapper() {
        return exoControllerWrapper;
    }

    public TextureView getTextureView() {
        return textureView;
    }

    /**
     * 设定边距
     *
     * @param left   左边距
     * @param top    上边距
     * @param right  右边距
     * @param bottom 下边距
     */
    public void setMargins(int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams textureParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        textureParams.bottomMargin = ExoDensityUtil.dp2px(getContext(), left);
        textureParams.bottomMargin = ExoDensityUtil.dp2px(getContext(), top);
        textureParams.bottomMargin = ExoDensityUtil.dp2px(getContext(), right);
        textureParams.bottomMargin = ExoDensityUtil.dp2px(getContext(), bottom);
        textureView.setLayoutParams(textureParams);
    }

    /**
     * 使用默认组件
     */
    public void useDefaultComponents() {
        // 缓冲loading
        ExoComponentLoadingView exoComponentLoadingView = new ExoComponentLoadingView(getContext());
        addControlComponent(exoComponentLoadingView);
        if (ExoConfig.COMPONENT_DEBUG_ENABLE) {
            // debug
            ExoComponentDebugControlView exoComponentDebugControlView = new ExoComponentDebugControlView(getContext());
            addControlComponent(exoComponentDebugControlView);
        }
        if (ExoConfig.COMPONENT_SPECTRUM_ENABLE) {
            // 频谱
            ExoComponentSpectrumView exoComponentSpectrumView = new ExoComponentSpectrumView(getContext());
            addControlComponent(exoComponentSpectrumView);
        }
        if (ExoConfig.COMPONENT_EQ_ENABLE) {
            // debug
            ExoComponentEqView exoComponentEqView = new ExoComponentEqView(getContext());
            addControlComponent(exoComponentEqView);
        }
        // 已暂停视图
        ExoComponentPausedView exoComponentPausedView = new ExoComponentPausedView(getContext());
        addControlComponent(exoComponentPausedView);
        // 播放完成
        ExoComponentCompleteView exoComponentCompleteView = new ExoComponentCompleteView(getContext());
        addControlComponent(exoComponentCompleteView);
        // 视频+直播手势控制视图
        ExoComponentGestureVodControlView exoGestureControlView = new ExoComponentGestureVodControlView(getContext());
        addControlComponent(exoGestureControlView);
        // 播放器标题栏(横屏模式下有效)
        ExoComponentTitleBarView exoComponentTitleBarView = new ExoComponentTitleBarView(getContext());
        addControlComponent(exoComponentTitleBarView);
        // 播放错误
        ExoComponentErrorView exoComponentErrorView = new ExoComponentErrorView(getContext());
        addControlComponent(exoComponentErrorView);
        ExoPlayMode mode = exoCore.getExoPlayerInfo().getExoPlayMode();
        if (ExoPlayMode.LIVE == mode || ExoPlayMode.VOD == mode) {
            if (ExoPlayMode.LIVE == mode) {
                // 直播控制栏
                ExoLiveControlBarView exoLiveControlBarView = new ExoLiveControlBarView(getContext());
                addControlComponent(exoLiveControlBarView);
            } else {
                // 视频控制栏
                ExoVodControlBarView exoVodControlBarView = new ExoVodControlBarView(getContext());
                addControlComponent(exoVodControlBarView);
                // 倍速播放
                ExoComponentSpeedView exoComponentSpeedView = new ExoComponentSpeedView(getContext());
                addControlComponent(exoComponentSpeedView);
            }
        } else if (ExoPlayMode.SHORT_VIDEO == mode) {
            // 短视频控制栏
            ExoShortVideoControlBarView exoShortVideoControlBarView = new ExoShortVideoControlBarView(getContext());
            addControlComponent(exoShortVideoControlBarView);
            // 短视频下拉控制栏
            ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView = new ExoShortVideoSimpleControlBarView(getContext());
            addControlComponent(exoShortVideoSimpleControlBarView);
        }

    }

    /**
     * 添加控制组件，最后面添加的在最下面，合理组织添加顺序，可让ControlComponent位于不同的层级
     */
    public void addControlComponent(IExoControlComponent... components) {
        if (components == null) {
            return;
        }
        for (IExoControlComponent component : components) {
            if (component == null || isControlComponentExist(component.getClass())) {
                continue;
            }
            mControlComponents.add(component);
            if (exoControllerWrapper != null) {
                component.attach(exoControllerWrapper);
            }
            View view = component.getView();
            if (view != null) {
                addView(view, 0);
                view.bringToFront();
            }
        }
    }

    /**
     * 判断是否已存在指定类的控制组件
     *
     * @param targetClass 目标组件的Class
     * @return true=已存在，false=不存在
     */
    private boolean isControlComponentExist(Class<? extends IExoControlComponent> targetClass) {
        for (IExoControlComponent existComponent : mControlComponents) {
            if (existComponent.getClass().equals(targetClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 手势功能禁用控制
     *
     * @param exoGestureEnable 手势功能禁用
     */
    public void setExoGestureEnable(ExoGestureEnable exoGestureEnable) {
        if (exoGestureHelper != null) {
            exoGestureHelper.setExoGestureEnable(exoGestureEnable);
        }
    }

    /**
     * 继续播放
     *
     * @param tip 继续播放提示文本
     */
    public void setPlayLocationLastTime(String tip) {
        for (IExoControlComponent component : mControlComponents) {
            component.setPlayLocationLastTime(tip);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="播放器核心控制">


    /**
     * 设置均衡器
     *
     * @param exoEqualizerPreset 均衡器预设值
     */
    @Override
    public void setEqualizer(@NonNull ExoEqualizerPreset exoEqualizerPreset) {
        if (exoCore != null) {
            exoCore.setEqualizer(exoEqualizerPreset);
        }
    }

    /**
     * 重新播放
     */
    @Override
    public void rePlay() {
        if (exoCore != null) {
            exoCore.rePlay();
        }
    }

    /**
     * 准备完成后开始自动播放
     *
     * @param playWhenReady true准备好后开始播放
     */
    public void setPlayWhenReady(boolean playWhenReady) {
        if (exoCore != null) {
            exoCore.setPlayWhenReady(playWhenReady);
        }
    }

    /**
     * 开始播放指定的媒体资源
     *
     * @param mode         模式
     * @param lastPlayTime 断点续播时间(仅非直播模式下有效)
     * @param url          视频流地址（支持 HLS, Dash, MP4 等）
     */
    @Override
    public void play(ExoPlayMode mode, long lastPlayTime, String url) {
        if (exoCore != null) {
            exoCore.play(mode, lastPlayTime, url);
        }
    }

    /**
     * 重置播放器
     * 立即停止所有播放、缓冲、重试等动作，清空媒体资源，重置所有播放状态
     */
    @Override
    public void reset() {
        if (exoCore != null) {
            exoCore.reset();
        }
    }

    /**
     * 刷新播放（不释放资源）
     * 逻辑：记录当前位置 -> 重新构建数据源 -> 准备播放器 -> 跳转回记录的位置
     */
    @Override
    public void refresh() {
        if (exoCore != null) {
            exoCore.refresh();
        }
    }

    /**
     * 暂停播放
     *
     * @param callFromActive 主动操作，如果缓冲结束，将不播放，需要手动继续
     */
    @Override
    public void pause(boolean callFromActive) {
        if (exoCore != null) {
            exoCore.pause(callFromActive);
        }
    }

    /**
     * 恢复播放（在暂停状态下调用）
     */
    @Override
    public void resume() {
        if (exoCore != null) {
            exoCore.resume();
        }
    }

    /**
     * 停止播放
     * 通常用于切换视频或退出当前页面，会重置播放状态
     */
    @Override
    public void stop() {
        if (exoCore != null) {
            exoCore.stop();
        }
    }

    /**
     * 当前是否正在播放
     *
     * @return true 表示正在播放，false 表示暂停或缓冲中
     */
    @Override
    public boolean isPlaying() {
        return exoCore != null && exoCore.isPlaying();
    }

    /**
     * 获取当前视频的总时长
     *
     * @return 单位：毫秒（ms）。如果视频尚未加载完成，可能返回 0 或负值。
     */
    @Override
    public long getDuration() {
        return exoCore == null ? 0 : exoCore.getDuration();
    }

    /**
     * 跳转到指定播放位置
     *
     * @param positionMs 目标位置的时间戳，单位：毫秒（ms）
     */
    @Override
    public void seekTo(long positionMs) {
        if (exoCore != null) {
            exoCore.seekTo(positionMs);
        }
    }

    /**
     * 获取当前已经播放到的位置
     *
     * @return 单位：毫秒（ms）
     */
    @Override
    public long getCurrentPosition() {
        return exoCore == null ? 0 : exoCore.getCurrentPosition();
    }

    /**
     * 设置视频的缩放/拉伸模式
     *
     * @param mode 取值 {@link ExoCoreScale}中
     */
    @Override
    public void setScaleMode(int mode) {
        if (exoCore != null) {
            exoCore.setScaleMode(mode);
        }
        if (exoGestureHelper != null) {
            exoGestureHelper.reset(true);
        }
    }

    /**
     * 获取当前视频应用的缩放模式
     *
     * @return 对应的模式枚举值
     */
    @Override
    public int getScaleMode() {
        return exoCore == null ? 0 : exoCore.getScaleMode();
    }

    /**
     * 设置播放速度
     *
     * @param speed 速度
     */
    @Override
    public void setSpeed(float speed) {
        if (exoCore != null) {
            exoCore.setSpeed(speed);
        }
    }

    /**
     * 获取播放速度
     *
     * @return 速度
     */
    @Override
    public float getSpeed() {
        return exoCore == null ? 0 : exoCore.getSpeed();
    }

    /**
     * 启动全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到90度
     */
    @Override
    public void startFullScreen(boolean callFromActive) {
        if (exoCore != null) {
            exoCore.startFullScreen(callFromActive);
        }
    }

    /**
     * 停止全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到0度
     */
    @Override
    public void stopFullScreen(boolean callFromActive) {
        if (exoCore != null) {
            exoCore.stopFullScreen(callFromActive);
        }
    }

    /**
     * 是否全屏
     *
     * @return 全屏模式
     */
    @Override
    public boolean isFullScreen() {
        return exoCore != null && exoCore.isFullScreen();
    }

    /**
     * 获取播放器实时信息
     *
     * @return 播放器实时信息
     */
    @Override
    public ExoPlayerInfo getExoPlayerInfo() {
        return exoCore == null ? new ExoPlayerInfo() : exoCore.getExoPlayerInfo();
    }

    /**
     * 设置试看时间
     * 仅针对于本次播放链接有效
     *
     * @param experienceTimeMs 试看时间 大于0有效
     */
    @Override
    public void setExperienceTime(long experienceTimeMs) {
        if (exoCore != null) {
            exoCore.setExperienceTime(experienceTimeMs);
        }
    }


    /**
     * 窗口焦点已更改
     * 由{@link View#onWindowFocusChanged(boolean)}触发回调后调用
     *
     * @param hasWindowFocus 是否具有窗口焦点
     */
    @Override
    public void windowFocusChanged(boolean hasWindowFocus) {
        if (exoCore != null) {
            exoCore.windowFocusChanged(hasWindowFocus);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Texture回调">
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {
        currentSurface = new Surface(s);
        bindSurfaceWhileTextureAvailable(currentSurface);
        if (exoGestureHelper != null) {
            exoGestureHelper.applyScaleSafe(false);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {
        if (exoGestureHelper != null) {
            exoGestureHelper.applyScaleSafe(false);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) {
        unbindSurfaceTextureWhileDestroyed(currentSurface);
        if (currentSurface != null) {
            currentSurface.release();
            currentSurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="手势蒙层 UI 回调">

    /**
     * 进度调节回调
     *
     * @param current 当前播放位置
     * @param total   总时长
     * @param seekTo  手指滑动目标 seek 位置
     */
    @Override
    public void onProgressChange(long current, long total, long seekTo) {
        if (exoCore != null) {
            exoCore.seekTo(seekTo);
        }
        for (IExoControlComponent component : mControlComponents) {
            component.onProgressChange(current, total, seekTo);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onProgressChange(current, total, seekTo);
        }
    }

    /**
     * 音量调节回调
     *
     * @param current 当前音量
     * @param max     最大音量
     */
    @Override
    public void onVolumeChange(int current, int max) {
        for (IExoControlComponent component : mControlComponents) {
            component.onVolumeChange(current, max);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onVolumeChange(current, max);
        }
    }

    /**
     * 亮度调节回调
     *
     * @param percent 亮度百分比 0.0 - 1.0
     */
    @Override
    public void onBrightnessChange(float percent) {
        for (IExoControlComponent component : mControlComponents) {
            component.onBrightnessChange(percent);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onBrightnessChange(percent);
        }
    }

    /**
     * 手势触摸开始：第一根手指触摸时触发（ACTION_DOWN）
     * 用于 UI 层显示提示蒙层
     */
    @Override
    public void onGestureStart() {
        for (IExoControlComponent component : mControlComponents) {
            component.onGestureStart();
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onGestureStart();
        }
    }

    /**
     * 手势触摸结束：最后一根手指离开时触发（ACTION_UP / ACTION_CANCEL）
     * 用于 UI 层隐藏提示蒙层
     */
    @Override
    public void onGestureEnd() {
        for (IExoControlComponent component : mControlComponents) {
            component.onGestureEnd();
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onGestureEnd();
        }
    }

    /**
     * 手指触摸点击
     *
     * @param fingerCount 手指数量
     * @param singleClick true 单击 false 双击
     */
    @Override
    public void onFingerTouchClick(int fingerCount, boolean singleClick) {
        if (ExoPlayMode.SHORT_VIDEO == getExoPlayerInfo().getExoPlayMode()) {
            if (getExoPlayerInfo().isFullScreen()) {
                if (fingerCount == 1 && !singleClick) {
                    if (exoCore != null && exoControllerWrapper != null) {
                        exoControllerWrapper.togglePlayPause();
                    }
                }
            } else {
                if (fingerCount == 1 && singleClick) {
                    if (exoCore != null && exoControllerWrapper != null) {
                        exoControllerWrapper.togglePlayPause();
                    }
                }
            }
        } else {
            if (fingerCount == 1 && !singleClick) {
                if (exoCore != null && exoControllerWrapper != null) {
                    exoControllerWrapper.togglePlayPause();
                }
            }
        }
        if (fingerCount == 3 && !singleClick) {
            if (exoGestureHelper != null) {
                exoGestureHelper.reset(true);
            }
        }
        for (IExoControlComponent component : mControlComponents) {
            component.onFingerTouchClick(fingerCount, singleClick);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onFingerTouchClick(fingerCount, singleClick);
        }
    }

    /**
     * 单点触摸回调
     *
     * @param action MotionEvent意图
     * @param rawX   相当于屏幕左上角X值
     * @param rawY   相对于屏幕左上角Y值
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onSingleFingerPointTouchEvent(int action, float rawX, float rawY, boolean isEdge) {
        for (IExoControlComponent component : mControlComponents) {
            component.onSingleFingerPointTouchEvent(action, rawX, rawY, isEdge);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onSingleFingerPointTouchEvent(action, rawX, rawY, isEdge);
        }
    }

    /**
     * 缩放回调
     *
     * @param totalScale 所放量 范围受
     *                   {@link ExoConfig#GESTURE_MIN_SCALE}
     *                   {@link ExoConfig#GESTURE_MAX_SCALE}
     *                   限制
     */
    @Override
    public void onScale(float totalScale) {
        for (IExoControlComponent component : mControlComponents) {
            component.onScale(totalScale);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onScale(totalScale);
        }
    }

    /**
     * 长按开始（仅考虑单指）
     *
     * @param fingerCount 手指数量
     * @param isEdge      边缘触发
     *                    取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onLongPressStart(int fingerCount, boolean isEdge) {
        for (IExoControlComponent component : mControlComponents) {
            component.onLongPressStart(fingerCount, isEdge);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onLongPressStart(fingerCount, isEdge);
        }
    }

    /**
     * 长按结束（手指抬起）
     *
     * @param isEdge 边缘触发
     *               取决于{@link ExoGestureEnable#disableEdgePullDown()}为 false 时才开始判断
     */
    @Override
    public void onLongPressEnd(boolean isEdge) {
        for (IExoControlComponent component : mControlComponents) {
            component.onLongPressEnd(isEdge);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onLongPressEnd(isEdge);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="生命周期">


    /**
     * 绑定Surface
     * 由{@link TextureView} 的 onSurfaceTextureAvailable 函数回调后调用
     *
     * @param surface Surface
     */
    @Override
    public void bindSurfaceWhileTextureAvailable(@NonNull Surface surface) {
        if (exoCore != null) {
            exoCore.bindSurfaceWhileTextureAvailable(surface);
        }
    }

    /**
     * 解绑Surface
     * 由{@link TextureView} 的 onSurfaceTextureDestroyed 函数回调后调用
     *
     * @param surface Surface
     */
    @Override
    public void unbindSurfaceTextureWhileDestroyed(@NonNull Surface surface) {
        if (exoCore != null) {
            exoCore.unbindSurfaceTextureWhileDestroyed(surface);
        }
    }

    /**
     * 首次设置播放器容器
     * 由{@link View#onAttachedToWindow()}触发回调后调用
     *
     * @param playerContainer 播放器父容器
     * @param playerView      播放器视图
     */
    @Override
    public void setPlayerContainerWhileFirstTime(ViewGroup playerContainer, View playerView) {
        if (exoCore != null) {
            exoCore.setPlayerContainerWhileFirstTime(playerContainer, playerView);
        }
    }

    /**
     * 页面暂停交互时调用
     */
    @Override
    public void onPause() {
        if (exoCore != null) {
            exoCore.onPause();
        }
    }

    /**
     * 页面恢复交互时调用
     */
    @Override
    public void onResume() {
        if (exoCore != null) {
            exoCore.onResume();
        }
    }

    /**
     * 彻底释放播放器资源
     * 在 Activity 或 Fragment 的 onDestroy 中必须调用，防止内存泄漏和解码器占用
     */
    @Override
    public void release() {
        if (exoCore != null) {
            exoCore.release();
            exoCore = null;
        }
        if (currentSurface != null) {
            currentSurface.release();
            currentSurface = null;
        }
        if (exoGestureHelper != null) {
            exoGestureHelper.release();
            exoGestureHelper = null;
        }
        if (exoScaleHelper != null) {
            exoScaleHelper.release();
            exoScaleHelper = null;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="播放器核心回调">

    /**
     * 获取所有控制组件
     *
     * @return 组件
     */
    @Override
    public List<IExoControlComponent> getExoComponents() {
        return mControlComponents;
    }

    /**
     * 按类获取控制组件
     *
     * @param cls 组件类
     * @param <T> 组件类型
     * @return 组件
     */
    @Override
    public <T extends IExoControlComponent> T getExoControlComponentByClass(Class<T> cls) {
        for (IExoControlComponent component : mControlComponents) {
            if (cls.isInstance(component)) {
                return cls.cast(component);
            }
        }
        return null;
    }

    /**
     * 当视频的第一帧像素真正渲染到 Surface 上时回调
     */
    @Override
    public void onExoRenderedFirstFrame() {
        for (IExoControlComponent component : mControlComponents) {
            component.onExoRenderedFirstFrame();
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onExoRenderedFirstFrame();
        }
    }

    /**
     * 播放器信息被改变时回调
     *
     * @param exoPlayerInfo 播放器实时信息
     */
    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {
        for (IExoControlComponent component : mControlComponents) {
            component.onPlayerInfoChanged(exoPlayerInfo);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(exoPlayerInfo);
        }
    }

    /**
     * 流量被改变时回调
     *
     * @param bytesInLastSecond 最后一秒的字节数
     * @param totalBytes        全部字节数
     */
    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {
        for (IExoControlComponent component : mControlComponents) {
            component.onNetworkBytesChanged(bytesInLastSecond, totalBytes);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onNetworkBytesChanged(bytesInLastSecond, totalBytes);
        }
    }

    /**
     * 播放进度被改变
     *
     * @param currentMs          当前进度（毫秒）
     * @param durationMs         总时间（毫秒）
     * @param bufferedPositionMs 缓冲位置（毫秒）
     * @param bufferedPercentage 缓冲百分比
     */
    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPositionMs, int bufferedPercentage) {
        for (IExoControlComponent component : mControlComponents) {
            component.onPlayingProgressPositionChanged(currentMs, durationMs, bufferedPositionMs, bufferedPercentage);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayingProgressPositionChanged(currentMs, durationMs, bufferedPositionMs, bufferedPercentage);
        }
    }

    /**
     * 播放状态被改变时回调
     *
     * @param playbackState     状态码
     * @param playbackStateName 状态名
     */
    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        for (IExoControlComponent component : mControlComponents) {
            component.onPlaybackStateChanged(playbackState, playbackStateName);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlaybackStateChanged(playbackState, playbackStateName);
        }
    }

    /**
     * 播放器形态被改变时回调
     *
     * @param playerState     播放器形态码
     * @param playerStateName 播放器形态名
     * @param playerView      播放器视图
     */
    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        for (IExoControlComponent component : mControlComponents) {
            component.onPlayerStateChanged(playerState, playerStateName, playerView);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerStateChanged(playerState, playerStateName, playerView);
        }
    }

    /**
     * 当播放发生错误时回调
     *
     * @param errorMsg  错误描述
     * @param throwable 抛出异常
     */
    @Override
    public void onPlayerError(String errorMsg, Throwable throwable) {
        for (IExoControlComponent component : mControlComponents) {
            component.onPlayerError(errorMsg, throwable);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerError(errorMsg, throwable);
        }
    }

    /**
     * 视频大小改变时回调
     *
     * @param view                  播放器视图 TextureView
     * @param pixelWidthHeightRatio 像素宽高比
     * @param videoWidth            视频宽度
     * @param videoHeight           视频高度
     * @param scaleMode             缩放模式 见{@link ExoCoreScale}
     */
    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {
        if (exoGestureHelper != null) {
            exoGestureHelper.setVideoSizeChanged(videoWidth, videoHeight, scaleMode);
            exoGestureHelper.applyScaleSafe(true);
        }

        for (IExoControlComponent component : mControlComponents) {
            component.onVideoSizeChanged(view, pixelWidthHeightRatio, videoWidth, videoHeight, scaleMode);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onVideoSizeChanged(view, pixelWidthHeightRatio, videoWidth, videoHeight, scaleMode);
        }
    }

    /**
     * 试看时间结束
     */
    @Override
    public void onExperienceTimeout() {
        for (IExoControlComponent component : mControlComponents) {
            component.onExperienceTimeout();
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onExperienceTimeout();
        }
    }

    /**
     * 短视频组件有更改意图时回调（涉及外部列表适配器交互，外部组件的更改动作由外部执行）
     *
     * @param clearScreenMode                   清屏模式
     * @param exoShortVideoSimpleControlBarView 清屏控制组件
     */
    @Override
    public void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView) {
        for (IExoControlComponent component : getExoComponents()) {
            component.onShortVideoComponentChangedAction(clearScreenMode, exoShortVideoSimpleControlBarView);
        }
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onShortVideoComponentChangedAction(clearScreenMode, exoShortVideoSimpleControlBarView);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="FFT数据回调监听器">

    /**
     * FFT原始数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param channelCount 音频声道数
     * @param fft          FFT原始数据数组
     */
    @Override
    public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
        for (IExoControlComponent component : mControlComponents) {
            component.onFFTReady(sampleRateHz, channelCount, fft);
        }
        if (iExoFFTCallBack != null) {
            iExoFFTCallBack.onFFTReady(sampleRateHz, channelCount, fft);
        }
    }

    /**
     * 频谱幅度数据回调
     *
     * @param sampleRateHz 音频采样率
     * @param magnitude    频谱幅度数组
     */
    @Override
    public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
        for (IExoControlComponent component : mControlComponents) {
            component.onMagnitudeReady(sampleRateHz, magnitude);
        }
        if (iExoFFTCallBack != null) {
            iExoFFTCallBack.onMagnitudeReady(sampleRateHz, magnitude);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="缩放控制">

    /**
     * 设置播放器（playerView）和容器（playerContainer）的整体缩放比例
     *
     * @param scale 目标缩放比例
     */
    public void setPlayerScale(@IntRange(from = 0, to = 100) int scale) {
        if (exoScaleHelper != null) {
            float minScale = ExoScaleHelper.MIN_SCALE;
            float maxScale = ExoScaleHelper.MAX_SCALE;
            float scaleValue = minScale + (maxScale - minScale) * (scale * 1.0f / 100);
            scaleValue = Math.max(minScale, Math.min(maxScale, scaleValue));
            exoScaleHelper.setScale(scaleValue);
        }
    }

    /**
     * 放大播放器
     */
    public void zoomIn() {
        if (exoScaleHelper != null) {
            exoScaleHelper.zoomIn();
        }
    }

    /**
     * 缩小播放器
     */
    public void zoomOut() {
        if (exoScaleHelper != null) {
            exoScaleHelper.zoomOut();
        }
    }

    /**
     * 重置缩放比例
     */
    public void resetScale() {
        if (exoScaleHelper != null) {
            exoScaleHelper.resetScale();
        }
    }

    /**
     * 获取当前缩放比例
     *
     * @return 当前缩放值
     */
    public float getCurrentScale() {
        return exoScaleHelper != null ? exoScaleHelper.getCurrentScale() : ExoScaleHelper.DEFAULT_SCALE;
    }
    // </editor-fold>
}