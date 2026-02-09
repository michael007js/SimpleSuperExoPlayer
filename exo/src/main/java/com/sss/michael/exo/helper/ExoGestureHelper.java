package com.sss.michael.exo.helper;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.callback.ExoGestureEnable;
import com.sss.michael.exo.callback.IExoController;
import com.sss.michael.exo.callback.IExoGestureCallBack;
import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.util.ExoAudioUtil;
import com.sss.michael.exo.util.ExoLog;

/**
 * @author Michael by SSS
 * @date 2025/12/24 0024 17:10
 * @Description 手势处理类：集成缩放、平移、惯性滑动、点击判定及 UI 调节（进度/音量/亮度）
 */
public class ExoGestureHelper {

    private int videoWidth = 0;
    private int videoHeight = 0;
    private final TextureView textureView;
    private final Context context;
    private final IExoController iExoController;
    private final IExoGestureCallBack iExoGestureCallBack; // 回调接口

    private boolean isLongPressTriggered = false;     // 是否已经触发了长按
    private Runnable longPressRunnable;

    // --- 矩阵变换相关变量 ---
    private float totalScale = ExoConfig.GESTURE_DEFAULT_SCALE; // 当前缩放倍数
    private float offsetX = 0f;     // X轴平移偏移量
    private float offsetY = 0f;     // Y轴平移偏移量
    private float lastX, lastY;    // 辅助记录上一次触摸点
    private float lastFocusX, lastFocusY;

    // 基准记录器
    private float mBaseOffsetX = 0f;
    private float mBaseOffsetY = 0f;

    // --- 点击识别逻辑变量 ---
    private float startX, startY;  // ACTION_DOWN 的起点坐标
    private long downTime;          // 按下的时间戳
    private int maxPointerCount = 0; // 单次触摸序列中出现过的最大手指数量（用于判定是几指操作）
    private final int touchSlop;           // 系统滑动判定阈值
    private static final int CLICK_TIMEOUT = 200; // 单击/双击的最大判定时间间隔
    private boolean isHandledInThisSequence = false; // 标记该序列是否已处理，防止 UP 和 CANCEL 重复触发

    // --- 连击记录 ---
    private long lastOneFingerClickTime, lastTwoFingerClickTime, lastThreeFingerClickTime;
    private static final int DOUBLE_CLICK_INTERVAL = 180; // 双击判定间隔

    // --- 系统识别器 ---
    private final ScaleGestureDetector scaleGestureDetector; // 处理双指缩放
    private final GestureDetector gestureDetector;           // 处理 Fling 惯性滑动
    private final OverScroller scroller;                     // 物理滚算引擎
    private boolean isDisallowParentIntercept = false;       // 标记是否已阻止父容器拦截触摸事件

    // --- 拖拽调节模式 ---
    private static final int GESTURE_NONE = 0;       // 无模式
    private static final int GESTURE_PROGRESS = 1;   // 正在调节进度
    private static final int GESTURE_VOLUME = 2;     // 正在调节音量
    private static final int GESTURE_BRIGHTNESS = 3; // 正在调节亮度
    private int mGestureType = GESTURE_NONE;
    private float mScrollStartValue; // 滑动开始时的初始数值（初始进度/音量/亮度）
    private final float progressSensitive = 3.0f; // 进度调节灵敏度：数值越大，手指需要滑动的距离越长
    private final float vOBrightSensitive = 1.5f; // 音量/亮度灵敏度：数值越小，调节越快
    // --- 动画相关 ---
    private float mCurrentAnimScaleX = 1f;
    private float mCurrentAnimScaleY = 1f;
    private ValueAnimator scaleAnimator;


    // --- 边缘下拉相关 ---
    private boolean isEdgePulling = false;       // DOWN 时是否在边缘
    private final int edgePullSlop; // 边缘下拉的判定阈值（基于系统touchSlop）

    private ExoGestureEnable exoGestureEnable;
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(2.0f);// 值越大，结尾减速感越明显


    @SuppressLint("ClickableViewAccessibility")
    public ExoGestureHelper(TextureView textureView, Context context,
                            ExoGestureEnable exoGestureEnable,
                            IExoController iExoController,
                            IExoGestureCallBack iExoGestureCallBack) {
        this.textureView = textureView;
        this.exoGestureEnable = exoGestureEnable == null ? new ExoGestureEnable() : exoGestureEnable;
        this.context = context;
        this.iExoController = iExoController;
        this.iExoGestureCallBack = iExoGestureCallBack;

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        edgePullSlop = touchSlop * 2; // 边缘下拉判定阈值，比普通滑动稍大
        scroller = new OverScroller(context);

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                disallowParentIntercept();
                // 缩放开始时，初始化中心点坐标
                lastFocusX = detector.getFocusX();
                lastFocusY = detector.getFocusY();
                // 缩放开始时更新基准偏移值，避免基于旧值计算
                mBaseOffsetX = offsetX;
                mBaseOffsetY = offsetY;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float prevScale = totalScale;
                totalScale *= factor;
                totalScale = Math.max(ExoConfig.GESTURE_MIN_SCALE_WHILE_FINGER_TOUCHED, Math.min(totalScale, ExoConfig.GESTURE_MAX_SCALE_WHILE_FINGER_TOUCHED));
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();

                if (prevScale != totalScale && !exoGestureEnable.disableMoveWhileScaling()) {
                    float viewCenterX = textureView.getWidth() / 2f;
                    float viewCenterY = textureView.getHeight() / 2f;

                    // 精准计算缩放中心导致的偏移补偿
                    // 新偏移 = 旧偏移 + (缩放中心 - 视图中心) * (1 - 1/缩放因子)
                    float scaleRatio = totalScale / prevScale; // 当前缩放相对上一帧的比例
                    offsetX = (offsetX - (focusX - viewCenterX)) * scaleRatio + (focusX - viewCenterX);
                    offsetY = (offsetY - (focusY - viewCenterY)) * scaleRatio + (focusY - viewCenterY);

                    // 补偿手指移动产生的位移
                    offsetX += (focusX - lastFocusX);
                    offsetY += (focusY - lastFocusY);
                }

                lastFocusX = focusX;
                lastFocusY = focusY;
                // 缩放过程中，只有在放大状态才修正边界
                if (totalScale > 1.0f) {
                    fixTranslation();
                }
                applyScaleSafe(false);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                allowParentIntercept();
                // 缩放结束立即修正偏移边界，避免释放时偏移非法
                fixTranslation();
                // 更新基准偏移值为当前合法值，防止后续滑动基于旧值计算
                mBaseOffsetX = offsetX;
                mBaseOffsetY = offsetY;
                // 检查缩放边界并回弹，确保释放后缩放值合法
                checkScaleBounds();
                // 立即应用最终矩阵，避免视觉延迟
                applyScaleSafe(false);
                ExoLog.log("缩放结束：修正偏移 X=" + offsetX + ", Y=" + offsetY + ", 缩放倍数=" + totalScale);
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // 只有在视频放大后才开启惯性滑动
                if (!exoGestureEnable.disableScaleGesture() && totalScale > 1.0f) {
                    startFling(velocityX, velocityY);
                    return true;
                }
                return false;
            }
        });

        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (scaleGestureDetector != null && !exoGestureEnable.disableScaleGesture()) {
                    scaleGestureDetector.onTouchEvent(event);
                }
                if (gestureDetector != null) gestureDetector.onTouchEvent(event);

                int action = event.getActionMasked();
                int pointerCount = event.getPointerCount();
                long currentTime = System.currentTimeMillis();
                if (!isEdgePulling) {
                    // 多指时拦截滚动事件，为后续意图做准备
                    if (pointerCount > 1) {
                        disallowParentIntercept();
                    } else {
                        allowParentIntercept();
                    }
                }
                handleEdgePullIntercept(event, action, pointerCount);
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        // 拦截瞬间停止惯性，实现即停效果
                        if (!scroller.isFinished()) {
                            // 强制计算当前的偏移量
                            scroller.computeScrollOffset();
                            offsetX = scroller.getCurrX();
                            offsetY = scroller.getCurrY();
                            scroller.abortAnimation();

                            // 立即应用一次矩阵，防止视觉闪烁，确保视图与 offsetX 同步
                            applyScaleSafe(false);
                        }
                        maxPointerCount = 1;
                        startX = event.getX();
                        startY = event.getY();
                        lastX = startX;
                        lastY = startY;


                        // 此时捕获的 mBaseOffsetX 才是真正 Fling 停止的位置
                        mBaseOffsetX = offsetX;
                        mBaseOffsetY = offsetY;

                        downTime = currentTime;
                        isHandledInThisSequence = false;
                        mGestureType = GESTURE_NONE;
                        if (iExoGestureCallBack != null && event.getPointerCount() == 1) {
                            iExoGestureCallBack.onSingleFingerPointTouchEvent(action, event.getRawX(), event.getRawY(), isEdgeTouch());
                            iExoGestureCallBack.onGestureStart();
                        }
                        if (!exoGestureEnable.disableLongTap()) {
                            // 创建长按计时任务
                            longPressRunnable = () -> {
                                if (exoGestureEnable.disableLongTap()) {
                                    // 双重保障
                                    return;
                                }
                                // 只有当前屏幕上依然只有 1 根手指，且没有进入缩放/拖拽模式时才触发
                                if (mGestureType == GESTURE_NONE && !scaleGestureDetector.isInProgress()
                                        && event.getPointerCount() == 1) {
                                    isLongPressTriggered = true;
                                    if (iExoGestureCallBack != null) {
                                        iExoGestureCallBack.onLongPressStart(1, isEdgeTouch()); // 明确传 1
                                    }
                                    if (isEdgeTouch() && !exoGestureEnable.disableDoubleSpeedPlayWhileLongTouch()) {
                                        iExoController.setSpeed(2.0f);
                                    }
                                    ExoVibratorHelper.vibrator(context);
                                    ExoLog.log("识别：单指长按开始，手指是否位于边缘：" + isEdgeTouch());
                                }
                            };
                            textureView.postDelayed(longPressRunnable, ExoConfig.GESTURE_LONG_PRESS_TIMEOUT);
                        }
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        // 记录序列中手指峰值（用于区分单指/三指）
                        if (pointerCount > maxPointerCount) maxPointerCount = pointerCount;

                        // 一旦增加手指，立即取消单指长按计时
                        textureView.removeCallbacks(longPressRunnable);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (iExoGestureCallBack != null && event.getPointerCount() == 1) {
                            iExoGestureCallBack.onSingleFingerPointTouchEvent(action, event.getRawX(), event.getRawY(), isEdgeTouch());
                        }
                        // 缩放过程中不处理其他 Move 逻辑
                        if (!scaleGestureDetector.isInProgress()) {
                            float x = event.getX();
                            float y = event.getY();

                            if (!exoGestureEnable.disableScaleGesture() && totalScale > 1.0f) {
                                disallowParentIntercept();
                                offsetX = mBaseOffsetX + (x - startX);
                                offsetY = mBaseOffsetY + (y - startY);
                                // 只有放大状态下才限制移动边界
                                fixTranslation();
                                applyScaleSafe(false);
                            } else if (pointerCount == 1) {
                                handleDragGesture(x - startX, y - startY);
                            }
                            lastX = x;
                            lastY = y;
                        }
                        if (!isLongPressTriggered) {
                            float deltaX = event.getX() - startX;
                            float deltaY = event.getY() - startY;
                            // 如果移动距离超过了阈值，说明是在滑动而不是长按，取消计时
                            if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                                textureView.removeCallbacks(longPressRunnable);
                                if (maxPointerCount == 1) lastOneFingerClickTime = 0;
                                else if (maxPointerCount == 2) lastTwoFingerClickTime = 0;
                                else if (maxPointerCount == 3) lastThreeFingerClickTime = 0;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (iExoGestureCallBack != null && event.getPointerCount() == 1) {
                            iExoGestureCallBack.onSingleFingerPointTouchEvent(action, event.getRawX(), event.getRawY(), isEdgeTouch());
                        }
                        // 正常抬起时结算：点击判定、隐藏 UI 蒙层、回弹
                        handleLongPressEnd();
                        handleGestureEnd(currentTime, event.getX(), event.getY());
                        allowParentIntercept();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        if (iExoGestureCallBack != null && event.getPointerCount() == 1) {
                            iExoGestureCallBack.onSingleFingerPointTouchEvent(action, event.getRawX(), event.getRawY(), isEdgeTouch());
                        }
                        // 系统拦截（如三指截屏）时的补救逻辑
                        ExoLog.log("识别到系统拦截 (ACTION_CANCEL)，补救结算...");
                        handleGestureEnd(currentTime, event.getX(), event.getY());
                        allowParentIntercept();
                        // 移除长按（在抖音等布局中手指滑动切换后不会回调 ACTION_UP ，故在此出移除长按事件）
                        textureView.removeCallbacks(longPressRunnable);
                        break;
                }
                return true;
            }
        });
    }

    /**
     * 处理边缘下拉拦截逻辑
     *
     * @param event        触摸事件
     * @param action       触摸动作
     * @param pointerCount 触摸手指数量
     */
    private void handleEdgePullIntercept(MotionEvent event, int action, int pointerCount) {
        // 仅在单指操作、启用边缘拦截、非缩放状态下处理
        if (exoGestureEnable.disableEdgePullDown() || pointerCount > 1 || scaleGestureDetector.isInProgress()) {
            if (isEdgePulling) {
                isEdgePulling = false;
                allowParentIntercept(); // 恢复父容器拦截
                ExoLog.log("边缘下拉：多指/缩放状态，取消拦截");
            }
            return;
        }

        int vw = textureView.getWidth();
        if (vw <= 0) return;

        float touchX = event.getX();
        float touchY = event.getY();
        boolean isInEdgeArea = checkInEdgeRegion(touchX);
        if (isInEdgeArea) {
            disallowParentIntercept();
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // 按下时标记是否在边缘区域
                isEdgePulling = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaY = touchY - startY;
                // 判定条件：在边缘区域 + 下拉动作（deltaY > 0） + 移动距离超过阈值
                if (isInEdgeArea && deltaY > edgePullSlop && !isEdgePulling) {
                    isEdgePulling = true;
                    disallowParentIntercept(); // 拦截父容器滚动
                    ExoLog.log("边缘下拉：触发拦截，阻止滚动视图滚动");
                } else if (!isInEdgeArea && isEdgePulling) {
                    if (ExoConfig.GESTURE_RESUME_SCROLL_LAYOUT_SCROLLING_WHEN_LEAVING_THE_EDGE_AREA_DURING_EDGE_PULL_DOWN) {
                        isEdgePulling = false;
                        allowParentIntercept(); // 离开边缘区域，恢复父容器拦截
                        ExoLog.log("边缘下拉：离开边缘区域，恢复滚动视图滚动");
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 抬起/取消时重置状态
                if (isEdgePulling) {
                    isEdgePulling = false;
                    allowParentIntercept();
                    ExoLog.log("边缘下拉：触摸结束，恢复滚动视图滚动");
                }
                break;
        }
    }

    /**
     * 是否触摸在边缘（判断记录到的第一个X坐标值）
     */
    private boolean isEdgeTouch() {
        return !exoGestureEnable.disableEdgePullDown() && checkInEdgeRegion(startX);
    }

    /**
     * 判断是否在边缘区域
     */
    private boolean checkInEdgeRegion(float x) {
        int vh = textureView.getWidth();
        if (vh <= 0) return false;

        float edge;
        if (ExoConfig.GESTURE_EDGE_LOCK_PIXEL > 0) {
            edge = ExoConfig.GESTURE_EDGE_LOCK_PIXEL;
        } else {
            edge = vh * ExoConfig.GESTURE_EDGE_LOCK_PERCENT;
        }
        return x <= edge || x >= (vh - edge);
    }

    /**
     * 动态阻止父容器拦截触摸事件（仅在需要时调用）
     */
    private void disallowParentIntercept() {
        if (textureView.getParent() == null || isDisallowParentIntercept) {
            return; // 父容器为空或已阻止，直接返回
        }
        textureView.getParent().requestDisallowInterceptTouchEvent(true);
        isDisallowParentIntercept = true;
    }

    /**
     * 恢复父容器的触摸事件拦截权限（手势结束时调用）
     */
    private void allowParentIntercept() {
        if (textureView.getParent() == null || !isDisallowParentIntercept) {
            return; // 父容器为空或未阻止，直接返回
        }
        textureView.getParent().requestDisallowInterceptTouchEvent(false);
        isDisallowParentIntercept = false;
    }

    private void handleLongPressEnd() {
        textureView.removeCallbacks(longPressRunnable);
        removeAllDelayedClickRunnables();
        if (isLongPressTriggered) {
            isLongPressTriggered = false;
            if (iExoGestureCallBack != null) {
                iExoGestureCallBack.onLongPressEnd(isEdgeTouch());
            }
            ExoLog.log("识别：长按结束");
            // 标记为已处理，防止触发单击事件
            isHandledInThisSequence = true;
        }
    }

    /**
     * 公共的手势结束处理
     */
    private void handleGestureEnd(long currentTime, float totalFingerXDistance, float totalFingerYDistance) {
        // 如果是长按触发的结束，或是调节手势触发的结束，这里只做 UI 清理
        if (iExoGestureCallBack != null && (mGestureType != GESTURE_NONE || isHandledInThisSequence)) {
            iExoGestureCallBack.onGestureEnd();
        }
        // 立即执行一次边界修正，确保 offsetX/Y 是合法的
        fixTranslation();

        // 只有在没有被其他逻辑（长按/滑动）处理过的情况下，才去判定点击
        if (!isHandledInThisSequence) {
            performClickDetection(currentTime, totalFingerXDistance, totalFingerYDistance);
        }
        // 缩放结束已处理checkScaleBounds，此处增加判断避免重复处理
        if (!scaleGestureDetector.isInProgress()) {
            checkScaleBounds();
        }
        maxPointerCount = 0;
        mGestureType = GESTURE_NONE;
        isHandledInThisSequence = false; // 为下一次触摸序列做准备
    }

    /**
     * Fling 惯性滑动处理逻辑
     */
    private void startFling(float velocityX, float velocityY) {
        int vw = textureView.getWidth();
        int vh = textureView.getHeight();
        float currentWidth = vw * mCurrentAnimScaleX;
        float currentHeight = vh * mCurrentAnimScaleY;

        // 计算最大位移极限，保证惯性后画面不会出界
        float maxDx = Math.max(0, (currentWidth - vw) / 2f);
        float maxDy = Math.max(0, (currentHeight - vh) / 2f);

        scroller.forceFinished(true);
        scroller.fling(
                (int) offsetX, (int) offsetY,
                (int) velocityX, (int) velocityY,
                (int) -maxDx, (int) maxDx,
                (int) -maxDy, (int) maxDy
        );
        textureView.postInvalidateOnAnimation();
    }

    /**
     * 必须在外部 View 的 computeScroll 中手动调用
     */
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.getCurrX();
            offsetY = scroller.getCurrY();
            applyScaleSafe(false);
            textureView.postInvalidateOnAnimation();
        }
    }

    /**
     * 处理进度/音量/亮度调节逻辑
     */
    private void handleDragGesture(float deltaX, float deltaY) {
        int vw = textureView.getWidth();
        int vh = textureView.getHeight();
        if (vw <= 0 || vh <= 0) return;

        if (mGestureType == GESTURE_NONE) {
            // 边缘保护：边缘屏蔽像素值  高度的 10%
            float edgeThreshold = vh * 0.1f;

            // 如果起始点在顶部 10% 或底部 10% 区域，直接跳过，防止误触系统手势
            if (startY < edgeThreshold || startY > (vh - edgeThreshold)) {
                return;
            }

            // 模式判定
            if (Math.abs(deltaX) > touchSlop && Math.abs(deltaX) > Math.abs(deltaY)) {
                mGestureType = GESTURE_PROGRESS;
                mScrollStartValue = iExoController.getCurrentPosition();
                disallowParentIntercept();
            } else if (Math.abs(deltaY) > touchSlop) {
                // 垂直滑动：只有在屏幕左右两侧 25% 宽度内才触发音量/亮度，中间留白
                float horizontalPadding = vw * 0.2f;
                if (!exoGestureEnable.disableBrightnessGesture() && startX < horizontalPadding) {
                    mGestureType = GESTURE_BRIGHTNESS;
                    mScrollStartValue = getBrightness();
                    disallowParentIntercept();
                } else if (!exoGestureEnable.disableVolumeGesture() && startX > (vw - horizontalPadding)) {
                    mGestureType = GESTURE_VOLUME;
                    mScrollStartValue = ExoAudioUtil.getStreamVolume(context, AudioManager.STREAM_MUSIC);
                    disallowParentIntercept();
                }
            }
        }

        if (mGestureType == GESTURE_PROGRESS) {
            if (exoGestureEnable.disableProgressChangeGesture()) {
                return;
            }
            float percentage = deltaX / (vw * progressSensitive);
            long duration = iExoController.getDuration();
            long seekTo = (long) (mScrollStartValue + duration * percentage);
            long target = Math.max(0, Math.min(seekTo, duration));
            ExoLog.log("调节进度，目标：" + target + " 当前：" + iExoController.getCurrentPosition());
            if (iExoGestureCallBack != null) {
                iExoGestureCallBack.onProgressChange(iExoController.getCurrentPosition(), duration, target);
            }
        } else if (mGestureType == GESTURE_VOLUME) {
            if (exoGestureEnable.disableVolumeGesture()) {
                return;
            }
            float percentage = -deltaY / (vh * vOBrightSensitive);
            int maxVol = ExoAudioUtil.getStreamMaxVolume(context, AudioManager.STREAM_MUSIC);
            int change = (int) (maxVol * percentage);
            int finalVol = Math.max(0, Math.min((int) mScrollStartValue + change, maxVol));
            ExoAudioUtil.setStreamVolume(context, AudioManager.STREAM_MUSIC, finalVol);
            ExoLog.log("调节音量，当前：" + finalVol + ",最大：" + maxVol);
            if (iExoGestureCallBack != null) {
                iExoGestureCallBack.onVolumeChange(finalVol, maxVol);
            }
        } else if (mGestureType == GESTURE_BRIGHTNESS) {
            if (exoGestureEnable.disableBrightnessGesture()) {
                return;
            }
            float percentage = -deltaY / (vh * vOBrightSensitive);
            float finalBright = Math.max(0.01f, Math.min(mScrollStartValue + percentage, 1.0f));
            setBrightness(finalBright);
            ExoLog.log("调节亮度：" + finalBright);
            if (iExoGestureCallBack != null) {
                iExoGestureCallBack.onBrightnessChange(finalBright);
            }
        }
    }

    private float getBrightness() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();

            // 如果当前窗口已经有设置过的亮度，直接用
            if (lp.screenBrightness >= 0) {
                return lp.screenBrightness;
            }

            // 如果窗口亮度是默认值(-1)，则去获取系统的全局亮度
            try {
                int systemBright = android.provider.Settings.System.getInt(
                        activity.getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                );
                // 系统亮度是 0-255，转换为 0.0-1.0
                return systemBright / 255f;
            } catch (Exception e) {
                return 0.5f; // 保底值
            }
        }
        return 0.5f;
    }

    private void setBrightness(float brightness) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.screenBrightness = brightness;
            activity.getWindow().setAttributes(lp);
        }
    }

    /**
     * 点击判定逻辑（含单/双/三指识别）
     */
    private void performClickDetection(long currentTime, float totalFingerXDistance, float totalFingerYDistance) {
        if (exoGestureEnable.disableTapGesture() || isHandledInThisSequence) return;

        float endX = lastX;
        float endY = lastY;
        double distance = Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2));
        long duration = currentTime - downTime;

        // 三指判定由于手指多，滑动容错（Slop）放大 3 倍，判定时间（Timeout）也适当放宽
        int dynamicSlop = (maxPointerCount >= 3) ? touchSlop * 3 : touchSlop;
        int dynamicTimeout = (maxPointerCount >= 3) ? 350 : CLICK_TIMEOUT;

        // 已经触发了调节手势则不判定为点击
        if (mGestureType != GESTURE_NONE) return;

        if (distance < dynamicSlop && duration < dynamicTimeout) {
            double fingerDistance = Math.sqrt(Math.pow(totalFingerXDistance - startX, 2) + Math.pow(totalFingerYDistance - startY, 2));
            resolveClickType(maxPointerCount, currentTime, (float) fingerDistance);
            isHandledInThisSequence = true;
        } else {
            // 滑动超阈值，清空上次点击时间，防止误判双击
            if (maxPointerCount == 1) lastOneFingerClickTime = 0;
            else if (maxPointerCount == 2) lastTwoFingerClickTime = 0;
            else if (maxPointerCount == 3) lastThreeFingerClickTime = 0;
        }
    }


    // 用于延迟执行单击的Runnable
    private Runnable singleClickRunnable;
    private Runnable doubleFingerClickRunnable;
    private Runnable tripleFingerClickRunnable;

    /**
     * 移除所有延迟单击任务
     */
    private void removeAllDelayedClickRunnables() {
        if (singleClickRunnable != null) {
            textureView.removeCallbacks(singleClickRunnable);
            singleClickRunnable = null;
        }
        if (doubleFingerClickRunnable != null) {
            textureView.removeCallbacks(doubleFingerClickRunnable);
            doubleFingerClickRunnable = null;
        }
        if (tripleFingerClickRunnable != null) {
            textureView.removeCallbacks(tripleFingerClickRunnable);
            tripleFingerClickRunnable = null;
        }
    }

    /**
     * 最终解析点击类型
     */
    private void resolveClickType(int maxPointers, long now, float distance) {
        removeAllDelayedClickRunnables();
        boolean isSlideValid = distance < touchSlop;
        if (!isSlideValid) {
            ExoLog.log("手指滑动超过阈值，不触发点击事件");
            return;
        }
        if (maxPointers == 1) {
            if (now - lastOneFingerClickTime < DOUBLE_CLICK_INTERVAL) {
                ExoLog.log("识别：单指双击");
                if (iExoGestureCallBack != null) {
                    iExoGestureCallBack.onFingerTouchClick(1, false);
                }
                lastOneFingerClickTime = 0;
            } else {
                lastOneFingerClickTime = now;
                singleClickRunnable = () -> {
                    ExoLog.log("识别：单指单击");
                    if (iExoGestureCallBack != null) {
                        iExoGestureCallBack.onFingerTouchClick(1, true);
                    }
                    lastOneFingerClickTime = 0;
                };
                textureView.postDelayed(singleClickRunnable, DOUBLE_CLICK_INTERVAL);
            }
        } else if (maxPointers == 2) {
            if (now - lastTwoFingerClickTime < DOUBLE_CLICK_INTERVAL) {
                ExoLog.log("识别：双指双击");
                if (iExoGestureCallBack != null) {
                    iExoGestureCallBack.onFingerTouchClick(2, false);
                }
                lastTwoFingerClickTime = 0;
            } else {
                lastTwoFingerClickTime = now;
                doubleFingerClickRunnable = () -> {
                    ExoLog.log("识别：双指单击");
                    if (iExoGestureCallBack != null) {
                        iExoGestureCallBack.onFingerTouchClick(2, true);
                    }
                    lastTwoFingerClickTime = 0;
                };
                textureView.postDelayed(doubleFingerClickRunnable, DOUBLE_CLICK_INTERVAL);
            }
        } else if (maxPointers == 3) {
            if (now - lastThreeFingerClickTime < DOUBLE_CLICK_INTERVAL) {
                ExoLog.log("识别：三指双击");
                if (iExoGestureCallBack != null) {
                    iExoGestureCallBack.onFingerTouchClick(3, false);
                }
                lastThreeFingerClickTime = 0;
            } else {
                lastThreeFingerClickTime = now;
                tripleFingerClickRunnable = () -> {
                    ExoLog.log("识别：三指单击");
                    if (iExoGestureCallBack != null) {
                        iExoGestureCallBack.onFingerTouchClick(3, true);
                    }
                    lastThreeFingerClickTime = 0;
                };
                textureView.postDelayed(tripleFingerClickRunnable, DOUBLE_CLICK_INTERVAL);
            }
        }
    }

    /**
     * 锁定画面不让其滑出黑边
     */
    private void fixTranslation() {
        if (totalScale <= 1.0f) {
            offsetX = 0;
            offsetY = 0;
            return;
        }
        int vw = textureView.getWidth();
        int vh = textureView.getHeight();
        if (vw <= 0 || vh <= 0) return;

        // 计算当前缩放下的实际宽高
        float currentWidth = vw * mCurrentAnimScaleX;
        float currentHeight = vh * mCurrentAnimScaleY;

        // 计算当前比例下，画面超出 View 边界的最大允许偏移
        float maxDx = Math.max(0, (currentWidth - vw) / 2f);
        float maxDy = Math.max(0, (currentHeight - vh) / 2f);

        // 限制偏移，防止放手时画面瞬间跳变
        offsetX = Math.max(-maxDx, Math.min(offsetX, maxDx));
        offsetY = Math.max(-maxDy, Math.min(offsetY, maxDy));
    }

    /**
     * 检查缩放极限并触发动画回弹
     */
    private void checkScaleBounds() {
        if (exoGestureEnable.disableScaleGesture()) {
            return;
        }
        boolean needSpringBack = false;
        if (totalScale > ExoConfig.GESTURE_MAX_SCALE) {
            totalScale = ExoConfig.GESTURE_MAX_SCALE;
            needSpringBack = true;
        } else if (totalScale < ExoConfig.GESTURE_MIN_SCALE) {
            totalScale = ExoConfig.GESTURE_MIN_SCALE;
            offsetX = 0;
            offsetY = 0;
            needSpringBack = true;
        }
        if (needSpringBack) applyScaleSafe(true);
    }

    /**
     * 执行矩阵应用及动画平滑过渡
     */
    public void applyScaleSafe(boolean useAnim) {
        int vw = textureView.getWidth();
        int vh = textureView.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || vw <= 0 || vh <= 0) return;

        float videoRatio = (float) videoWidth / videoHeight;
        float viewRatio = (float) vw / vh;

        float targetScaleX = 1f;
        float targetScaleY = 1f;

        // 根据 ExoCoreScale 模式计算基础缩放
        switch (iExoController.getScaleMode()) {
            case ExoCoreScale.SCALE_FIT:
            case ExoCoreScale.SCALE_AUTO:
                if (videoRatio > viewRatio) targetScaleY = viewRatio / videoRatio;
                else targetScaleX = videoRatio / viewRatio;
                break;

            case ExoCoreScale.SCALE_FILL_CUT:
                if (videoRatio > viewRatio) targetScaleX = videoRatio / viewRatio;
                else targetScaleY = viewRatio / videoRatio;
                break;

            case ExoCoreScale.SCALE_STRETCH:
                // 拉伸模式，ScaleX/Y 保持 1.0f，TextureView 默认会铺满 Layout
                targetScaleX = 1f;
                targetScaleY = 1f;
                break;

            case ExoCoreScale.SCALE_16_9:
                float target16_9 = 16f / 9f;
                if (target16_9 > viewRatio) targetScaleY = viewRatio / target16_9;
                else targetScaleX = target16_9 / viewRatio;
                break;

            case ExoCoreScale.SCALE_21_9:
                float target21_9 = 21f / 9f;
                if (target21_9 > viewRatio) targetScaleY = viewRatio / target21_9;
                else targetScaleX = target21_9 / viewRatio;
                break;
        }

        // 叠加手势缩放值
        final float finalTargetX = targetScaleX * totalScale;
        final float finalTargetY = targetScaleY * totalScale;

        if (!useAnim) {
            if (scaleAnimator != null) scaleAnimator.cancel();
            mCurrentAnimScaleX = finalTargetX;
            mCurrentAnimScaleY = finalTargetY;
            updateMatrix(vw, vh);
        } else {
            if (scaleAnimator != null) scaleAnimator.cancel();
            float sX = mCurrentAnimScaleX, sY = mCurrentAnimScaleY;
            float ox = offsetX, oy = offsetY;

            scaleAnimator = ValueAnimator.ofFloat(0f, 1f);
            scaleAnimator.setDuration(200);
            scaleAnimator.setInterpolator(decelerateInterpolator);
            scaleAnimator.addUpdateListener(animation -> {
                float f = animation.getAnimatedFraction();
                mCurrentAnimScaleX = sX + (finalTargetX - sX) * f;
                mCurrentAnimScaleY = sY + (finalTargetY - sY) * f;
                // 回弹到 1:1 时位移必须归零
                if (totalScale <= 1.0f) {
                    offsetX = ox * (1 - f);
                    offsetY = oy * (1 - f);
                }
                updateMatrix(vw, vh);
            });
            scaleAnimator.start();
        }
    }

    private void updateMatrix(int vw, int vh) {
        Matrix matrix = new Matrix();
        matrix.setScale(mCurrentAnimScaleX, mCurrentAnimScaleY, vw / 2f, vh / 2f);
        matrix.postTranslate(offsetX, offsetY);
        textureView.setTransform(matrix);
//        textureView.invalidate();
        // 比 invalidate() 更丝滑，因为它会严格对齐系统的屏幕刷新率（Vsync），特别是在执行 ValueAnimator 平滑动画时，它能保证每一帧动画都精准触发重绘
        textureView.postInvalidateOnAnimation();
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onScale(totalScale);
        }
    }

    /**
     * 重置所有状态（如切视频或三指双击时）
     */
    public void reset(boolean withAnim) {
        if (scaleAnimator != null) scaleAnimator.cancel();
        if (!scroller.isFinished()) scroller.abortAnimation();
        totalScale = ExoConfig.GESTURE_DEFAULT_SCALE;
        offsetX = 0;
        offsetY = 0;
        lastFocusX = 0f;
        lastFocusY = 0f;
        mBaseOffsetX = 0f;
        mBaseOffsetY = 0f;
        isLongPressTriggered = false;
        isEdgePulling = false;
        allowParentIntercept();
        applyScaleSafe(withAnim);
    }

    public void setVideoSizeChanged(int videoWidth, int videoHeight, int scaleMode) {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        textureView.post(() -> applyScaleSafe(true));
    }

    /**
     * 手势功能禁用控制
     *
     * @param exoGestureEnable 手势功能禁用
     */
    public void setExoGestureEnable(ExoGestureEnable exoGestureEnable) {
        this.exoGestureEnable = exoGestureEnable;
        reset(false);
    }

    /**
     * 释放资源
     */
    public void release() {
        removeAllDelayedClickRunnables();
        if (scaleAnimator != null) {
            scaleAnimator.removeAllUpdateListeners();
            scaleAnimator.cancel();
            scaleAnimator = null;
        }

        if (scroller != null) {
            scroller.abortAnimation();
            scroller.forceFinished(true);
        }

        if (textureView != null) {
            textureView.setOnTouchListener(null);
        }
    }


}