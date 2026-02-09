package com.sss.michael.exo.callback;


import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;

/**
 * @author Michael by 61642
 * @date 2025/12/30 11:33
 * @Description 回调
 */
public interface OnExoVideoPlayRecyclerViewCallBack<T> {
    /**
     * 列表数据被移除时触发，用于通知外部队列移除指定的item
     *
     * @param itemPosition 索引
     */
    void onRemoveItemByPosition(int itemPosition);

    /**
     * 短视频组件有更改意图时回调（涉及外部列表适配器交互，外部组件的更改动作由外部执行）
     *
     * @param clearScreenMode                   清屏模式
     * @param exoShortVideoSimpleControlBarView 清屏控制组件
     */
    void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView);

    /**
     * 页面正在滑动
     *
     * @param targetPosition  当前position
     * @param isScrollingNext 是否滑向下一个
     * @param progress        距离上一个或下一个的滑动进度
     */
    void onPageScrolling(int targetPosition, boolean isScrollingNext, float progress);

    /**
     * 控制组件
     *
     * @return 组件 如果null或数组长度为0，则使用默认组件
     */
    IExoControlComponent[] components();

    /**
     * 页面被选中时回调
     *
     * @param itemPosition 索引
     * @param url          视频地址
     */
    void onPageSelected(int itemPosition, String url);

    /**
     * 获取视频地址
     *
     * @param itemPosition 索引
     * @return 视频地址
     */
    String getVideoUrl(int itemPosition);

    /**
     * 获取封面图片Id
     *
     * @return adapter中的封面图片Id
     */
    int getCoverImgId();

    /**
     * 获取视频容器Id
     *
     * @return adapter中的视频容器Id
     */
    int getPlayerContainerId();

    /**
     * 刷新
     */
    void onRefresh();

    /**
     * 加载更多
     */
    void onLoadMore();

}