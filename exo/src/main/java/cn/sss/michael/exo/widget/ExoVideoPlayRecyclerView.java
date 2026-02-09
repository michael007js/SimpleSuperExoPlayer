package cn.sss.michael.exo.widget;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.SimpleExoPlayerView;
import cn.sss.michael.exo.cache.ExoPreloadHelper;
import cn.sss.michael.exo.callback.ExoControllerWrapper;
import cn.sss.michael.exo.callback.ExoGestureEnable;
import cn.sss.michael.exo.callback.IExoControlComponent;
import cn.sss.michael.exo.callback.IExoController;
import cn.sss.michael.exo.callback.IExoFFTCallBack;
import cn.sss.michael.exo.callback.IExoGestureCallBack;
import cn.sss.michael.exo.callback.IExoNotifyCallBack;
import cn.sss.michael.exo.callback.IExoOnPageChangeListener;
import cn.sss.michael.exo.callback.IExoScaleCallBack;
import cn.sss.michael.exo.callback.OnExoVideoPlayRecyclerViewCallBack;
import cn.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;
import cn.sss.michael.exo.constant.ExoCoreScale;
import cn.sss.michael.exo.constant.ExoEqualizerPreset;
import cn.sss.michael.exo.constant.ExoPlayMode;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.helper.ExoScaleHelper;
import cn.sss.michael.exo.util.ExoDensityUtil;
import cn.sss.michael.exo.util.ExoLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Michael by SSS
 * @date 2025/12/30 12:10
 * @Description 短视频竖向滑动播放控件（共享播放器 + 预加载 + 下拉刷新/上拉加载）
 */
public class ExoVideoPlayRecyclerView extends FrameLayout implements IExoOnPageChangeListener, IExoController,
        IExoNotifyCallBack,
        IExoGestureCallBack,
        IExoFFTCallBack,
        IExoScaleCallBack {


    // 布局与样式常量
    private static final int COLOR_BG = Color.BLACK;
    private static final float DRAG_RATE = 3.5f; // 阻尼系数
    private static final int TEXT_COLOR = 0xff999999;
    private static final float TEXT_SIZE_DP = 14f;
    private static final float TEXT_MARGIN_DP = 30f;
    private static final int THRESHOLD_DP = 60; // 刷新/加载触发阈值

    // 动画时长常量
    private static final long COVER_FADE_DURATION = 200;
    private static final long SUSPENSION_ANIM_DURATION = 250;
    private static final long RESET_ANIM_DURATION = 300;

    // 视图缓存与预加载常量
    private static final int DEFAULT_PRELOAD_COUNT = 2;
    private static final float VIEW_CACHE_SCALE = 2.0f;
    private IExoGestureCallBack iExoGestureCallBack;
    private IExoNotifyCallBack iExoNotifyCallBack;
    private IExoFFTCallBack iExoFFTCallBack;

    private OnExoVideoPlayRecyclerViewCallBack onExoVideoPlayRecyclerViewCallBack;
    private SimpleExoPlayerView simpleExoPlayerView;
    private ExoPreloadHelper preloadHelper;
    private int currentPosition = -1;
    private int preloadCount = DEFAULT_PRELOAD_COUNT;
    private RecyclerView.Adapter adapter;
    private int threshold;
    private float textMargin;
    private int touchSlop;

    private ExoNestRecyclerView recyclerView;
    private TextView tvTip;
    protected ExoPagerLayoutManager layoutManager;

    private float mDownX = -1, mDownY = -1;
    private boolean isPulling;

    private int preLoadNumber;

    public ExoVideoPlayRecyclerView(Context context) {
        this(context, null);
    }

    public ExoVideoPlayRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoVideoPlayRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        threshold = ExoDensityUtil.dp2px(context, THRESHOLD_DP);
        textMargin = ExoDensityUtil.dp2px(context, TEXT_MARGIN_DP);
        setBackgroundColor(COLOR_BG);

        tvTip = new TextView(context);
        tvTip.setTextSize(TEXT_SIZE_DP);
        tvTip.setTextColor(TEXT_COLOR);
        tvTip.setGravity(Gravity.CENTER_HORIZONTAL);
        tvTip.setAlpha(0f);
        LayoutParams tipParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        addView(tvTip, tipParams);

        recyclerView = new ExoNestRecyclerView(context);
        // 根据预加载数量计算缓存大小
        int viewCacheSize = (int) (preloadCount * VIEW_CACHE_SCALE + 1);
        recyclerView.setItemViewCacheSize(viewCacheSize);
        recyclerView.setHasFixedSize(true);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER); // 禁用默认边缘光晕
        LayoutParams rvParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
        );
        addView(recyclerView, rvParams);

        layoutManager = new ExoPagerLayoutManager(context, this);
        layoutManager.setPreloadPageCount(preloadCount, recyclerView);
        recyclerView.setLayoutManager(layoutManager);

        simpleExoPlayerView = new SimpleExoPlayerView(context);
        simpleExoPlayerView.setExoGestureEnable(new ExoGestureEnable() {
            @Override
            public boolean disableVolumeGesture() {
                // 禁用音量调节（涉及纵向滑动）
                return true;
            }

            @Override
            public boolean disableBrightnessGesture() {
                // 禁用亮度调节（涉及纵向滑动）
                return true;
            }

            @Override
            public boolean disableProgressChangeGesture() {
                // 禁用进度手势
                return true;
            }

            @Override
            public boolean disableEdgePullDown() {
                // 禁用边缘下拉手势（仿抖音）
                return false;
            }

            @Override
            public boolean disableDoubleSpeedPlayWhileLongTouch() {
                // 禁用长按倍速播放
                return false;
            }

            @Override
            public boolean disableMoveWhileScaling() {
                // 缩放过程中是否禁用位移
                return false;
            }
        });
        simpleExoPlayerView.setExoNotifyCallBack(this);
        preloadHelper = ExoPreloadHelper.getInstance(context.getApplicationContext());
    }


    // <editor-fold defaultstate="collapsed" desc="外部控制函数">

    public ExoControllerWrapper getExoControllerWrapper() {
        return simpleExoPlayerView.getExoControllerWrapper();
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
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setMargins(left, top, right, bottom);
        }
    }

    /**
     * 开启预加载
     *
     * @param preLoadNumber 预加载阈值（当前item的索引大于等于该值时触发预加载）
     */
    public void openPreLoad(int preLoadNumber) {
        if (preLoadNumber > 1) {
            this.preLoadNumber = preLoadNumber;
        }

    }

    /**
     * 关闭预加载
     */
    public void closePreLoad() {
        this.preLoadNumber = -1;
    }

    /**
     * 获取当前完整展示的item索引
     *
     * @return 当前完整展示的item索引
     */
    public int getCurrentPositionInList() {
        return currentPosition;
    }

    /**
     * 同索引查找对应的view
     *
     * @param position 索引
     * @return view
     */
    public View findViewByPosition(int position) {
        if (position < 0 || position >= adapter.getItemCount()) {
            return null;
        }
        return layoutManager.findViewByPosition(position);
    }

    /**
     * 设置播放器回调通知
     *
     * @param iExoNotifyCallBack 播放器核心回调接口
     */
    public void setExoNotifyCallBack(IExoNotifyCallBack iExoNotifyCallBack) {
        this.iExoNotifyCallBack = iExoNotifyCallBack;
    }

    /**
     * 设置FFT数据回调监听器
     *
     * @param iExoFFTCallBack FFT数据回调监听器
     */
    public void setExoFFTCallBack(IExoFFTCallBack iExoFFTCallBack) {
        this.iExoFFTCallBack = iExoFFTCallBack;
    }

    /**
     * 设置手势回调接口
     *
     * @param iExoGestureCallBack 手势回调接口
     */
    public void setExoGestureCallBack(IExoGestureCallBack iExoGestureCallBack) {
        this.iExoGestureCallBack = iExoGestureCallBack;
    }

    /**
     * 设置短视频组件回调
     *
     * @param cb 短视频组件回调
     */
    public void setOnVideoPlayRecyclerViewCallBack(OnExoVideoPlayRecyclerViewCallBack cb) {
        this.onExoVideoPlayRecyclerViewCallBack = cb;
    }

    /**
     * 设置适配器
     *
     * @param adapter 适配器
     * @param <T>     模型类型
     */
    public <T extends RecyclerView.ViewHolder> void setAdapter(RecyclerView.Adapter<T> adapter) {
        this.adapter = adapter;
        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }
    }

    /**
     * 播放视频
     *
     * @param url 视频地址
     */
    public void play(String url) {
        if (simpleExoPlayerView == null) {
            return;
        }
        reset();
        setPlayWhenReady(true);
        play(ExoPlayMode.SHORT_VIDEO, 0, url);
        IExoControlComponent[] components = onExoVideoPlayRecyclerViewCallBack == null ? new IExoControlComponent[]{} : onExoVideoPlayRecyclerViewCallBack.components();
        if (components.length == 0) {
            simpleExoPlayerView.useDefaultComponents();
        } else {
            simpleExoPlayerView.addControlComponent(components);
        }
    }

    /**
     * 设置预加载数量
     *
     * @param count 预加载个数（最小为1）
     */
    public void setPreloadCount(int count) {
        preloadCount = Math.max(1, count);
        if (layoutManager != null && recyclerView != null) {
            layoutManager.setPreloadPageCount(preloadCount, recyclerView);
            // 更新视图缓存大小
            int viewCacheSize = (int) (preloadCount * VIEW_CACHE_SCALE + 1);
            recyclerView.setItemViewCacheSize(viewCacheSize);
        }
    }

    /**
     * 重置视图状态（刷新/加载完成后调用）
     */
    public void resetViewState() {
        if (recyclerView == null || tvTip == null) {
            return;
        }

        // 取消未完成的动画
        recyclerView.animate().cancel();
        tvTip.animate().cancel();

        // 回弹动画
        recyclerView.animate()
                .translationY(0)
                .setDuration(RESET_ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tvTip.animate()
                .alpha(0f)
                .setDuration(RESET_ANIM_DURATION)
                .start();

        // 重置状态变量
        isPulling = false;
        mDownX = -1;
        mDownY = -1;
    }

    /**
     * 根据指定索引移除列表项
     *
     * @param targetPosition 要移除的item索引
     */
    public void removeItemByPosition(int targetPosition) {
        if (adapter == null) {
            ExoLog.log("删除失败：Adapter为空");
            return;
        }
        int itemCount = adapter.getItemCount();
        if (targetPosition < 0 || targetPosition >= itemCount) {
            ExoLog.log("删除失败：索引越界，targetPosition=" + targetPosition + "，总数量=" + itemCount);
            return;
        }

        recyclerView.stopScroll();
        boolean isRemoveCurrent = (targetPosition == currentPosition);
        int oldCurrentPos = currentPosition;
        int oldDataSize = adapter.getItemCount();

        // 停止旧视频播放+解绑播放器容器
        if (isRemoveCurrent && simpleExoPlayerView != null) {
            stop();
            reset();
            ViewGroup oldParent = (ViewGroup) simpleExoPlayerView.getParent();
            if (oldParent != null) {
                oldParent.removeView(simpleExoPlayerView);
            }
        }

        if (isRemoveCurrent) {
            currentPosition = -1;
        } else if (targetPosition < currentPosition) {
            currentPosition--;
        }

        if (layoutManager != null) {
            // 重置布局管理器内部的选中状态和位置
            layoutManager.currentPostion = currentPosition;
            layoutManager.haveSelect = (currentPosition != -1);
            // 清空布局管理器的防抖缓存
            layoutManager.mLastFirstVisiblePosition = RecyclerView.NO_POSITION;
            layoutManager.mLastLastVisiblePosition = RecyclerView.NO_POSITION;
        }


        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onRemoveItemByPosition(targetPosition);
        }
        recyclerView.post(() -> {
            int newItemCount = adapter.getItemCount();
            if (newItemCount == 0) {
                resetViewState();
                return;
            }

            // 计算删除后应停留的位置（边界防护）
            int nextScrollPos = isRemoveCurrent
                    ? Math.min(targetPosition, newItemCount - 1)
                    : currentPosition;
            nextScrollPos = Math.max(0, nextScrollPos);

            layoutManager.scrollToPositionWithOffset(nextScrollPos, 0);

            View targetView = layoutManager.findViewByPosition(nextScrollPos);
            if (targetView == null) {
                ExoLog.log("目标位置View为空，nextScrollPos=" + nextScrollPos);
                return;
            }
            onPageSelected(nextScrollPos, targetView);
            layoutManager.currentPostion = nextScrollPos;
            layoutManager.haveSelect = true;
            ExoLog.log("删除成功：原位置=" + oldCurrentPos + "，新位置=" + nextScrollPos + "，原队列数量=" + oldDataSize + "，新队列数量=" + adapter.getItemCount());
        });

        // 清理旧视频预加载
        if (preloadHelper != null && onExoVideoPlayRecyclerViewCallBack != null) {
            String removeUrl = onExoVideoPlayRecyclerViewCallBack.getVideoUrl(targetPosition);
            if (!TextUtils.isEmpty(removeUrl)) {
                preloadHelper.stopPreload(removeUrl);
            }
        }
    }

    /**
     * 滚动到目标索引
     *
     * @param targetPosition 索引
     */
    public void scrollToPosition(int targetPosition) {
        if (recyclerView == null || layoutManager == null || adapter == null) {
            return;
        }
        int count = adapter.getItemCount();
        if (targetPosition < 0 || targetPosition >= count) {
            return;
        }
        layoutManager.scrollToPositionWithOffset(targetPosition, 0);
        post(new Runnable() {
            @Override
            public void run() {
                View targetView = layoutManager.findViewByPosition(targetPosition);
                if (targetView == null) {
                    ExoLog.log("目标位置View为空，jumpPosition=" + targetPosition);
                    return;
                }

                onPageSelected(targetPosition, targetView);
            }
        });
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="功能实现">

    /**
     * 页面正在滑动
     *
     * @param targetPosition  当前position
     * @param isScrollingNext 是否滑向下一个
     * @param progress        距离上一个或下一个的滑动进度
     */
    @Override
    public void onPageScrolling(int targetPosition, boolean isScrollingNext, float progress) {
        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onPageScrolling(targetPosition, isScrollingNext, progress);
        }
    }

    /**
     * 绑定视图（由外部adapter中手动调用触发）
     */
    @Override
    public void onBindViewHolder(int itemPosition, RecyclerView.ViewHolder viewHolder, View itemView) {
        if (preLoadNumber != 0) {
            if (itemPosition >= adapter.getItemCount() - preLoadNumber) {
                if (!isPulling) {
                    isPulling = true;
                    if (recyclerView != null) {
                        this.recyclerView.post(new Runnable() {
                            public void run() {
                                if (onExoVideoPlayRecyclerViewCallBack != null) {
                                    onExoVideoPlayRecyclerViewCallBack.onLoadMore();
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * 切换播放页面
     */
    @Override
    public void onPageSelected(int itemPosition, View itemView) {
        if (currentPosition == itemPosition || onExoVideoPlayRecyclerViewCallBack == null || adapter == null) {
            return;
        }

        // 恢复上一个与下一个页面的封面
        if (currentPosition != -1) {
            showCoverByPosition(currentPosition);
            showCoverByPosition(currentPosition + 1);
        }

        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.stop();
        }

        currentPosition = itemPosition;
        if (itemView == null) {
            return;
        }
        FrameLayout container = itemView.findViewById(onExoVideoPlayRecyclerViewCallBack.getPlayerContainerId());
        if (container == null) {
            return;
        }
        simpleExoPlayerView.setPlayerContainerWhileFirstTime(container, simpleExoPlayerView);

        ViewGroup parent = (ViewGroup) simpleExoPlayerView.getParent();
        if (parent != null) {
            parent.removeView(simpleExoPlayerView);
        }

        container.addView(simpleExoPlayerView, 0, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));

        String url = onExoVideoPlayRecyclerViewCallBack.getVideoUrl(itemPosition);
        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onPageSelected(itemPosition, url);
        }
        if (!TextUtils.isEmpty(url) && simpleExoPlayerView != null) {
            play(url);
        }
        // 执行预加载
//        executePreload(itemPosition);
    }

    /**
     * 加载上一个或下一个
     */
    @Override
    public void onLoadLastAndNext(int currentPosition, int currentPageSelectedPosition, View currentView) {
        if (onExoVideoPlayRecyclerViewCallBack == null) {
            return;
        }
        // 非当前页面强制显示封面
        if (currentPageSelectedPosition != currentPosition) {
            currentView.post(new Runnable() {
                @Override
                public void run() {
                    ImageView coverImg = currentView.findViewById(onExoVideoPlayRecyclerViewCallBack.getCoverImgId());
                    if (coverImg != null) {
                        if (coverImg.getWidth() <= 0) {
                            ViewGroup.LayoutParams lp = coverImg.getLayoutParams();
                            lp.width = LayoutParams.MATCH_PARENT;
                            lp.height = LayoutParams.MATCH_PARENT;
                            coverImg.setLayoutParams(lp);
                        }
                        showOrHideCover(coverImg, true);
                    }
                }
            });
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isPulling) {
            return true;
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - mDownY;
                float dx = ev.getRawX() - mDownX;

                // 仅拦截垂直滑动且达到最小滑动距离
                if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    boolean isTopCanNotScroll = dy > 0 && !recyclerView.canScrollVertically(-1);
                    boolean isBottomCanNotScroll = dy < 0 && !recyclerView.canScrollVertically(1);
                    if (isTopCanNotScroll || isBottomCanNotScroll) {
                        return true;
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isPulling) {
            return true;
        }

        if (mDownY == -1) {
            mDownY = ev.getRawY();
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - mDownY;
                handleScrollMove(dy);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float dyUp = ev.getRawY() - mDownY;
                handleScrollUp(dyUp);
                break;
        }
        return true;
    }

    /**
     * 显示或隐藏封面
     *
     * @param coverImg 封面图片框
     * @param show     显示或隐藏
     */
    private void showOrHideCover(ImageView coverImg, boolean show) {
        if (coverImg != null) {
            coverImg.animate().cancel();
            if (show) {
                coverImg.setAlpha(1f);
                coverImg.setVisibility(VISIBLE);
            } else {
                coverImg.setAlpha(0f);
                coverImg.setVisibility(INVISIBLE);
            }
        }
    }

    /**
     * 封面动画
     *
     * @param coverImg 封面图片框
     * @param show     显示或隐藏
     */
    private void coverAnimation(ImageView coverImg, boolean show) {

        if (show) {
            // 淡入动画
            coverImg.animate()
                    .alpha(1f)
                    .setDuration(COVER_FADE_DURATION)
                    .withEndAction(() -> {
                        showOrHideCover(coverImg, true);
                    })
                    .start();
        } else {
            // 淡出动画
            coverImg.animate()
                    .alpha(0f)
                    .setDuration(COVER_FADE_DURATION)
                    .withEndAction(() -> {
                        showOrHideCover(coverImg, false);
                    })
                    .start();
        }
    }

    /**
     * 处理滑动中逻辑（更新视图位移与提示文本）
     *
     * @param dy 垂直滑动距离
     */
    private void handleScrollMove(float dy) {
        float translationY = dy / DRAG_RATE;
        boolean isTopPull = dy > 0 && !recyclerView.canScrollVertically(-1);
        boolean isBottomPull = dy < 0 && !recyclerView.canScrollVertically(1);

        if (!isTopPull && !isBottomPull) {
            return;
        }

        if (Math.abs(translationY) > threshold) {
            tvTip.setText(isTopPull ? "释放立即刷新" : "释放立即加载");
        } else {
            tvTip.setText(isTopPull ? "下拉刷新" : "上拉加载更多");
        }

        updateScrollUI(translationY, isTopPull);
    }

    /**
     * 更新滑动时的视图状态
     *
     * @param translationY RecyclerView位移
     * @param isTop        是否是下拉刷新
     */
    private void updateScrollUI(float translationY, boolean isTop) {
        recyclerView.setTranslationY(translationY);

        // 提示文本透明度（随位移渐变）
        float alpha = Math.min(1.0f, Math.abs(translationY) / textMargin);
        tvTip.setAlpha(alpha);

        // 提示文本位置
        float tipTranslationY;
        if (isTop) {
            tipTranslationY = translationY - textMargin;
        } else {
            tipTranslationY = getHeight() + translationY + textMargin / 2;
        }
        tvTip.setTranslationY(tipTranslationY);
    }

    /**
     * 处理滑动抬起逻辑（触发刷新/加载或重置视图）
     *
     * @param dyUp 抬起时的垂直位移
     */
    private void handleScrollUp(float dyUp) {
        float actualTransY = dyUp / DRAG_RATE;

        if (actualTransY > threshold && !recyclerView.canScrollVertically(-1)) {
            triggerRefresh();
            return;
        }

        if (Math.abs(actualTransY) > threshold && !recyclerView.canScrollVertically(1)) {
            triggerLoadMore();
            return;
        }

        // 未达到阈值，重置视图
        resetViewState();
    }

    /**
     * 触发下拉刷新
     */
    private void triggerRefresh() {
        if (isPulling) {
            return;
        }
        isPulling = true;
        tvTip.setText("正在刷新...");

        // 回弹悬停动画
        float stopY = textMargin * 1.5f;
        startSuspensionAnimation(stopY, stopY - textMargin);

        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onRefresh();
        }
    }

    /**
     * 触发上拉加载
     */
    private void triggerLoadMore() {
        if (isPulling) {
            return;
        }
        isPulling = true;
        tvTip.setText("正在加载...");

        // 回弹悬停动画
        float stopY = -textMargin * 1.5f;
        float tipY = getHeight() + stopY + textMargin / 2;
        startSuspensionAnimation(stopY, tipY);

        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onLoadMore();
        }
    }

    /**
     * 执行悬停动画（刷新/加载时的回弹效果）
     *
     * @param targetRecyclerViewY RecyclerView目标位移
     * @param targetTipY          TextView目标位移
     */
    private void startSuspensionAnimation(float targetRecyclerViewY, float targetTipY) {
        if (!recyclerView.isAttachedToWindow() || !tvTip.isAttachedToWindow()) {
            return;
        }

        // RecyclerView悬停动画
        recyclerView.animate()
                .translationY(targetRecyclerViewY)
                .setDuration(SUSPENSION_ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 提示文本悬停动画
        tvTip.animate()
                .translationY(targetTipY)
                .alpha(1.0f)
                .setDuration(SUSPENSION_ANIM_DURATION)
                .start();
    }

    /**
     * 恢复指定位置封面显示
     *
     * @param position 目标位置
     */
    private void showCoverByPosition(int position) {
        if (onExoVideoPlayRecyclerViewCallBack == null || layoutManager == null) {
            return;
        }

        View itemView = findViewByPosition(position);
        if (itemView == null) {
            return;
        }

        ImageView coverImg = itemView.findViewById(onExoVideoPlayRecyclerViewCallBack.getCoverImgId());
        showOrHideCover(coverImg, true);

    }

    /**
     * 执行视频预加载
     * URL去重
     *
     * @param currentPosition 当前播放位置
     */
    private void executePreload(int currentPosition) {
        if (onExoVideoPlayRecyclerViewCallBack == null || preloadHelper == null || adapter == null) {
            return;
        }

        List<String> nextUrls = new ArrayList<>();
        HashSet<String> urlSet = new HashSet<>(); // 去重容器
        int maxPosition = adapter.getItemCount() - 1;

        for (int i = currentPosition + 1; i <= currentPosition + preloadCount; i++) {
            if (i > maxPosition) {
                break; // 避免越界
            }
            String nextUrl = onExoVideoPlayRecyclerViewCallBack.getVideoUrl(i);
            if (!TextUtils.isEmpty(nextUrl) && !urlSet.contains(nextUrl)) {
                urlSet.add(nextUrl);
                nextUrls.add(nextUrl);
            }
        }

        if (!nextUrls.isEmpty()) {
            preloadHelper.resumePreload(nextUrls);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="生命周期">

    public void onPause() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.onPause();
        }
    }

    public void onResume() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.onResume();
        }
    }

    /**
     * 释放所有资源
     */
    public void release() {
        resetViewState();
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setExoNotifyCallBack(null);
            simpleExoPlayerView.release();
            simpleExoPlayerView = null;
        }
        if (preloadHelper != null) {
            preloadHelper.stopAll();
            preloadHelper.shutdownExecutor();
            preloadHelper = null;
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
            recyclerView.clearOnChildAttachStateChangeListeners();
            recyclerView.removeAllViews();
            recyclerView = null;
        }
        if (tvTip != null) {
            tvTip.animate().cancel();
            tvTip = null;
        }
        layoutManager = null;
        iExoNotifyCallBack = null;
        onExoVideoPlayRecyclerViewCallBack = null;
        adapter = null;
        currentPosition = -1;
        isPulling = false;
    }

    /**
     * 控件脱离窗口时（如页面销毁、隐藏），自动重置状态，防止卡死
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        resetViewState(); // 强制重置所有状态
        // 取消所有动画，避免内存泄漏
        if (recyclerView != null) {
            recyclerView.animate().cancel();
        }
        if (tvTip != null) {
            tvTip.animate().cancel();
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
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setEqualizer(exoEqualizerPreset);
        }
    }

    /**
     * 重新播放
     */
    @Override
    public void rePlay() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.rePlay();
        }
    }

    /**
     * 准备完成后开始自动播放
     *
     * @param playWhenReady true准备好后开始播放
     */
    public void setPlayWhenReady(boolean playWhenReady) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setPlayWhenReady(playWhenReady);
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
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.play(mode, lastPlayTime, url);
        }
    }

    /**
     * 重置播放器
     * 立即停止所有播放、缓冲、重试等动作，清空媒体资源，重置所有播放状态
     */
    @Override
    public void reset() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.reset();
        }
    }

    /**
     * 刷新播放（不释放资源）
     * 逻辑：记录当前位置 -> 重新构建数据源 -> 准备播放器 -> 跳转回记录的位置
     */
    @Override
    public void refresh() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.refresh();
        }
    }

    /**
     * 暂停播放
     *
     * @param callFromActive 主动操作，如果缓冲结束，将不播放，需要手动继续
     */
    @Override
    public void pause(boolean callFromActive) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.pause(callFromActive);
        }
    }

    /**
     * 恢复播放（在暂停状态下调用）
     */
    @Override
    public void resume() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.resume();
        }
    }

    /**
     * 停止播放
     * 通常用于切换视频或退出当前页面，会重置播放状态
     */
    @Override
    public void stop() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.stop();
        }
    }

    /**
     * 当前是否正在播放
     *
     * @return true 表示正在播放，false 表示暂停或缓冲中
     */
    @Override
    public boolean isPlaying() {
        return simpleExoPlayerView != null && simpleExoPlayerView.isPlaying();
    }

    /**
     * 获取当前视频的总时长
     *
     * @return 单位：毫秒（ms）。如果视频尚未加载完成，可能返回 0 或负值。
     */
    @Override
    public long getDuration() {
        return simpleExoPlayerView == null ? 0 : simpleExoPlayerView.getDuration();
    }

    /**
     * 跳转到指定播放位置
     *
     * @param positionMs 目标位置的时间戳，单位：毫秒（ms）
     */
    @Override
    public void seekTo(long positionMs) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.seekTo(positionMs);
        }
    }

    /**
     * 获取当前已经播放到的位置
     *
     * @return 单位：毫秒（ms）
     */
    @Override
    public long getCurrentPosition() {
        return simpleExoPlayerView == null ? 0 : simpleExoPlayerView.getCurrentPosition();
    }

    /**
     * 设置视频的缩放/拉伸模式
     *
     * @param mode 取值 {@link ExoCoreScale}中
     */
    @Override
    public void setScaleMode(int mode) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setScaleMode(mode);
        }
    }

    /**
     * 获取当前视频应用的缩放模式
     *
     * @return 对应的模式枚举值
     */
    @Override
    public int getScaleMode() {
        return simpleExoPlayerView == null ? 0 : simpleExoPlayerView.getScaleMode();
    }

    /**
     * 设置播放速度
     *
     * @param speed 速度
     */
    @Override
    public void setSpeed(float speed) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setSpeed(speed);
        }
    }

    /**
     * 获取播放速度
     *
     * @return 速度
     */
    @Override
    public float getSpeed() {
        return simpleExoPlayerView == null ? 0 : simpleExoPlayerView.getSpeed();
    }

    /**
     * 启动全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到90度
     */
    @Override
    public void startFullScreen(boolean callFromActive) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.startFullScreen(callFromActive);
        }
    }

    /**
     * 停止全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到0度
     */
    @Override
    public void stopFullScreen(boolean callFromActive) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.stopFullScreen(callFromActive);
        }
    }

    /**
     * 是否全屏
     *
     * @return 全屏模式
     */
    @Override
    public boolean isFullScreen() {
        return simpleExoPlayerView != null && simpleExoPlayerView.isFullScreen();
    }

    /**
     * 获取播放器实时信息
     *
     * @return 播放器实时信息
     */
    @Override
    public ExoPlayerInfo getExoPlayerInfo() {
        return simpleExoPlayerView == null ? new ExoPlayerInfo() : simpleExoPlayerView.getExoPlayerInfo();
    }

    /**
     * 设置试看时间
     * 仅针对于本次播放链接有效
     *
     * @param experienceTimeMs 试看时间 大于0有效
     */
    @Override
    public void setExperienceTime(long experienceTimeMs) {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setExperienceTime(experienceTimeMs);
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
        return simpleExoPlayerView == null ? new ArrayList<>() : simpleExoPlayerView.getExoComponents();
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
        for (IExoControlComponent component : getExoComponents()) {
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
        if (onExoVideoPlayRecyclerViewCallBack == null || currentPosition == -1 || layoutManager == null) {
            return;
        }

        View currentItemView = findViewByPosition(currentPosition);
        if (currentItemView == null) {
            return;
        }

        ImageView coverImg = currentItemView.findViewById(onExoVideoPlayRecyclerViewCallBack.getCoverImgId());
        if (coverImg == null || coverImg.getVisibility() != VISIBLE) {
            return;
        }

        if (!coverImg.isAttachedToWindow()) {
            showOrHideCover(coverImg, false);
            return;
        }
        coverAnimation(coverImg, false);

        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
            component.onPlayerStateChanged(playerState, playerStateName, playerView);
        }
        if (ExoPlayerMode.PLAYER_NORMAL == playerState) {

            View itemView = findViewByPosition(currentPosition);
            if (itemView == null || playerView == null) return;

            FrameLayout container = itemView.findViewById(onExoVideoPlayRecyclerViewCallBack.getPlayerContainerId());
            if (container == null || playerView.getParent() == container) return;

            ((ViewGroup) playerView.getParent()).removeView(playerView);
            container.addView(playerView);

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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        if (onExoVideoPlayRecyclerViewCallBack != null) {
            onExoVideoPlayRecyclerViewCallBack.onShortVideoComponentChangedAction(clearScreenMode, exoShortVideoSimpleControlBarView);
        }
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
            component.onLongPressEnd(isEdge);
        }
        if (iExoGestureCallBack != null) {
            iExoGestureCallBack.onLongPressEnd(isEdge);
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
        for (IExoControlComponent component : getExoComponents()) {
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
        for (IExoControlComponent component : getExoComponents()) {
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
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.setPlayerScale(scale);
        }
    }

    /**
     * 放大播放器
     */
    public void zoomIn() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.zoomIn();
        }
    }

    /**
     * 缩小播放器
     */
    public void zoomOut() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.zoomOut();
        }
    }

    /**
     * 重置缩放比例
     */
    public void resetScale() {
        if (simpleExoPlayerView != null) {
            simpleExoPlayerView.resetScale();
        }
    }

    /**
     * 获取当前缩放比例
     *
     * @return 当前缩放值
     */
    public float getCurrentScale() {
        return simpleExoPlayerView != null ? simpleExoPlayerView.getCurrentScale() : ExoScaleHelper.DEFAULT_SCALE;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ExoNestRecyclerView">
    class ExoNestRecyclerView extends RecyclerView {
        private float startX, startY;

        public ExoNestRecyclerView(@NonNull Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // 正在刷新/加载时，不拦截触摸事件
            if (isPulling) {
                return false;
            }

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    // 横滑距离大于纵滑时，不拦截（方便视频进度条拖动等操作）
                    float dx = Math.abs(ev.getX() - startX);
                    float dy = Math.abs(ev.getY() - startY);
                    if (dx > dy && dx > touchSlop) {
                        return false;
                    }
                    break;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="分页布局管理器">
    public static class ExoPagerLayoutManager extends LinearLayoutManager implements RecyclerView.OnChildAttachStateChangeListener {
        private final PagerSnapHelper mPagerSnapHelper;
        private final IExoOnPageChangeListener mExoOnPageChangeListener;
        private int currentPostion = -1;
        private boolean haveSelect;
        private int mPreloadPageCount = DEFAULT_PRELOAD_COUNT;
        private final int mScreenHeight;

        // 缓存上一次的可见位置，用于防抖
        private int mLastFirstVisiblePosition = RecyclerView.NO_POSITION;
        private int mLastLastVisiblePosition = RecyclerView.NO_POSITION;

        ExoPagerLayoutManager(Context context, IExoOnPageChangeListener listener) {
            super(context, VERTICAL, false);
            this.mExoOnPageChangeListener = listener;
            mPagerSnapHelper = new PagerSnapHelper();
            mScreenHeight = context.getResources().getDisplayMetrics().heightPixels;
        }

        @Override
        protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
            // 预留额外布局空间，确保滑动顺滑
            int space = mScreenHeight * mPreloadPageCount;
            extraLayoutSpace[0] = space;
            extraLayoutSpace[1] = space;
            super.calculateExtraLayoutSpace(state, extraLayoutSpace);
        }

        public void setPreloadPageCount(int count, RecyclerView rv) {
            this.mPreloadPageCount = Math.max(1, count);
            if (rv != null) {
                rv.requestLayout();
            }
        }

        @Override
        public void onAttachedToWindow(RecyclerView view) {
            super.onAttachedToWindow(view);
            view.addOnChildAttachStateChangeListener(this);
            mPagerSnapHelper.attachToRecyclerView(view);
        }

        @Override
        public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
            super.onDetachedFromWindow(view, recycler);
            view.removeOnChildAttachStateChangeListener(this);
        }

        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            if (!haveSelect && mExoOnPageChangeListener != null) {
                haveSelect = true;
                currentPostion = getPosition(view);
                mExoOnPageChangeListener.onPageSelected(currentPostion, view);
            }
        }

        @Override
        public void onChildViewDetachedFromWindow(@NonNull View view) {
        }

        /**
         * 在每一像素的滚动中实时检测相邻View
         * 覆盖此方法可以捕获手指按住不松开时的滑动变化
         */
        @Override
        public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
            int scrolled = super.scrollVerticallyBy(dy, recycler, state);

            if (dy == 0) {
                return scrolled;
            }

            boolean isScrollingNext = dy > 0;
            int targetPosition = isScrollingNext ? currentPostion + 1 : currentPostion - 1;
            if (targetPosition >= 0 && targetPosition < getItemCount()) {


                // 获取当前视图，用于计算滑动的具体比例（可选）
                View currentView = findViewByPosition(currentPostion);
                if (currentView != null) {
                    int height = currentView.getHeight();
                    int top = getDecoratedTop(currentView);

                    // 计算滑出的比例 (0.0 ~ 1.0)
                    // 当 top 为 0 时，说明完整显示；
                    // 当 dy > 0，top 会变成负数，绝对值越大说明滑出越多
                    float progress = Math.abs((float) top / height);

                    ExoLog.log("正在滑向: " + targetPosition +
                            " | 方向: " + (isScrollingNext ? "下个" : "上个") +
                            " | 进度: " + progress);

                    if (mExoOnPageChangeListener != null) {
                        mExoOnPageChangeListener.onPageScrolling(targetPosition, isScrollingNext, progress);
                    }
                }

            }
            // 实时检测可见Items
            checkVisibleItemsInRealTime();
            return scrolled;
        }

        @Override
        public void onScrollStateChanged(int state) {
            super.onScrollStateChanged(state);
            // 滑动停止时，切换播放页面
            if (state == RecyclerView.SCROLL_STATE_IDLE) {
                if (mPagerSnapHelper == null || mExoOnPageChangeListener == null) {
                    return;
                }
                View snapView = mPagerSnapHelper.findSnapView(this);
                if (snapView == null) {
                    return;
                }
                int pos = getPosition(snapView);
                if (currentPostion != pos) {
                    currentPostion = pos;
                    mExoOnPageChangeListener.onPageSelected(currentPostion, snapView);
                }
            } else {
                // 状态变更时也检查一次，确保万无一失
                checkVisibleItemsInRealTime();
            }
        }

        /**
         * 实时检测并回调可见Item（带防抖）
         */
        private void checkVisibleItemsInRealTime() {
            if (mExoOnPageChangeListener == null) {
                return;
            }

            int first = findFirstVisibleItemPosition();
            int last = findLastVisibleItemPosition();

            // 如果位置无效，直接返回
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
                return;
            }

            // 如果可见范围没有变化，不重复触发循环和回调
            if (first == mLastFirstVisiblePosition && last == mLastLastVisiblePosition) {
                return;
            }

            mLastFirstVisiblePosition = first;
            mLastLastVisiblePosition = last;

            for (int i = first; i <= last; i++) {
                // 避免越界风险
                if (i < 0 || i >= getItemCount()) continue;

                View v = findViewByPosition(i);
                ExoLog.log(v + "");
                if (v != null) {
                    // 回调外部，处理预加载或封面显示
                    mExoOnPageChangeListener.onLoadLastAndNext(i, currentPostion, v);
                }
            }
        }
    }
    // </editor-fold>
}