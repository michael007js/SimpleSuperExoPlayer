package com.sss.michael.exo.callback;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public interface IExoOnPageChangeListener {
    /**
     * 页面正在滑动
     *
     * @param targetPosition  当前position
     * @param isScrollingNext 是否滑向下一个
     * @param progress        距离上一个或下一个的滑动进度
     */
    void onPageScrolling(int targetPosition, boolean isScrollingNext, float progress);

    /**
     * 绑定视图（由外部adapter中手动调用触发）
     */
    void onBindViewHolder(int itemPosition, RecyclerView.ViewHolder viewHolder, View itemView);

    /**
     * 切换播放页面
     */
    void onPageSelected(int itemPosition, View itemView);

    /**
     * 加载上一个或下一个
     */
    void onLoadLastAndNext(int currentPosition, int currentPageSelectedPosition, View currentView);
}