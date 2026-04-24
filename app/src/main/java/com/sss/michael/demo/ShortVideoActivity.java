package com.sss.michael.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sss.michael.demo.base.BaseActivity;
import com.sss.michael.demo.databinding.ActivityShortVideoBinding;
import com.sss.michael.exo.callback.IExoControlComponent;
import com.sss.michael.exo.callback.OnExoVideoPlayRecyclerViewCallBack;
import com.sss.michael.exo.component.ExoShortVideoSimpleControlBarView;

import java.util.ArrayList;
import java.util.List;

public class ShortVideoActivity extends BaseActivity<ActivityShortVideoBinding> {
    private static final int PAGE_SIZE = 6;
    private static final long MOCK_DELAY_MS = 900L;
    private static final int[] COVER_COLORS = {
            0xFF101820,
            0xFF183A37,
            0xFF6B2D5C,
            0xFF005F73,
            0xFF7B2D26,
            0xFF2B2D42
    };

    private final List<ShortVideoItem> videoItems = new ArrayList<>();
    private final ShortVideoAdapter adapter = new ShortVideoAdapter();

    private int currentPage = 0;
    private int dataVersion = 1;
    private int nextId = 1;
    private Runnable pendingRefreshAction;
    private Runnable pendingLoadMoreAction;

    @Override
    protected int setLayout() {
        return R.layout.activity_short_video;
    }

    @Override
    protected void init() {
        setupRecyclerView();
        loadInitialData();
    }

    private void setupRecyclerView() {
        binding.shortVideoList.setPullDownRefreshEnabled(true);
        binding.shortVideoList.setOnVideoPlayRecyclerViewCallBack(new OnExoVideoPlayRecyclerViewCallBack<ShortVideoItem>() {
            @Override
            public void onRemoveItemByPosition(int itemPosition) {
            }

            @Override
            public void onShortVideoComponentChangedAction(boolean clearScreenMode, ExoShortVideoSimpleControlBarView exoShortVideoSimpleControlBarView) {
            }

            @Override
            public void onPageScrolling(int targetPosition, boolean isScrollingNext, float progress) {
            }

            @Override
            public IExoControlComponent[] components() {
                return new IExoControlComponent[0];
            }

            @Override
            public void onPageSelected(int itemPosition, String url) {
            }

            @Override
            public String getVideoUrl(int itemPosition) {
                if (itemPosition < 0 || itemPosition >= videoItems.size()) {
                    return null;
                }
                return videoItems.get(itemPosition).videoUrl;
            }

            @Override
            public int getCoverImgId() {
                return R.id.short_video_cover;
            }

            @Override
            public int getPlayerContainerId() {
                return R.id.short_video_player_container;
            }

            @Override
            public void onRefresh() {
                scheduleRefresh();
            }

            @Override
            public void onLoadMore() {
                scheduleLoadMore();
            }
        });
        binding.shortVideoList.setAdapter(adapter);
    }

    private void loadInitialData() {
        currentPage = 1;
        videoItems.clear();
        videoItems.addAll(createMockPage(dataVersion, currentPage));
        adapter.notifyDataSetChanged();
        playFirstItem();
    }

    private void scheduleRefresh() {
        if (pendingRefreshAction != null) {
            binding.shortVideoList.removeCallbacks(pendingRefreshAction);
        }
        pendingRefreshAction = new Runnable() {
            @Override
            public void run() {
                dataVersion++;
                currentPage = 1;
                nextId = 1;
                videoItems.clear();
                videoItems.addAll(createMockPage(dataVersion, currentPage));
                adapter.notifyDataSetChanged();
                binding.shortVideoList.resetViewState();
                playFirstItem();
                pendingRefreshAction = null;
            }
        };
        binding.shortVideoList.postDelayed(pendingRefreshAction, MOCK_DELAY_MS);
    }

    private void scheduleLoadMore() {
        if (pendingLoadMoreAction != null) {
            binding.shortVideoList.removeCallbacks(pendingLoadMoreAction);
        }
        pendingLoadMoreAction = new Runnable() {
            @Override
            public void run() {
                currentPage++;
                int insertStart = videoItems.size();
                List<ShortVideoItem> moreItems = createMockPage(dataVersion, currentPage);
                videoItems.addAll(moreItems);
                adapter.notifyItemRangeInserted(insertStart, moreItems.size());
                binding.shortVideoList.resetViewState();
                pendingLoadMoreAction = null;
            }
        };
        binding.shortVideoList.postDelayed(pendingLoadMoreAction, MOCK_DELAY_MS);
    }

    private void playFirstItem() {
        if (videoItems.isEmpty()) {
            return;
        }
        binding.shortVideoList.post(new Runnable() {
            @Override
            public void run() {
                if (binding.shortVideoList.getCurrentPositionInList() != 0) {
                    binding.shortVideoList.scrollToPosition(0);
                } else {
                    binding.shortVideoList.play(videoItems.get(0).videoUrl);
                }
            }
        });
    }

    private List<ShortVideoItem> createMockPage(int version, int page) {
        List<ShortVideoItem> pageItems = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = (page - 1) * PAGE_SIZE + i + 1;
            int color = COVER_COLORS[(index - 1) % COVER_COLORS.length];
            pageItems.add(new ShortVideoItem(
                    nextId++,
                    "Feed " + version + "  Video " + index,
                    playUrl,
                    color
            ));
        }
        return pageItems;
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.shortVideoList.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.shortVideoList.onResume();
    }

    @Override
    protected void onDestroy() {
        if (pendingRefreshAction != null) {
            binding.shortVideoList.removeCallbacks(pendingRefreshAction);
            pendingRefreshAction = null;
        }
        if (pendingLoadMoreAction != null) {
            binding.shortVideoList.removeCallbacks(pendingLoadMoreAction);
            pendingLoadMoreAction = null;
        }
        binding.shortVideoList.release();
        super.onDestroy();
    }

    private static final class ShortVideoItem {
        final int id;
        final String title;
        final String videoUrl;
        final int coverColor;

        ShortVideoItem(int id, String title, String videoUrl, int coverColor) {
            this.id = id;
            this.title = title;
            this.videoUrl = videoUrl;
            this.coverColor = coverColor;
        }
    }

    private final class ShortVideoAdapter extends RecyclerView.Adapter<ShortVideoAdapter.ShortVideoViewHolder> {
        @NonNull
        @Override
        public ShortVideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_short_video, parent, false);
            return new ShortVideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ShortVideoViewHolder holder, int position) {
            ShortVideoItem item = videoItems.get(position);
            holder.titleView.setText(item.title);
            holder.indexView.setText("#" + item.id);
            holder.coverView.setBackgroundColor(item.coverColor);
            binding.shortVideoList.onBindViewHolder(position, holder, holder.itemView);
        }

        @Override
        public int getItemCount() {
            return videoItems.size();
        }

        final class ShortVideoViewHolder extends RecyclerView.ViewHolder {
            final ImageView coverView;
            final FrameLayout playerContainer;
            final TextView titleView;
            final TextView indexView;

            ShortVideoViewHolder(@NonNull View itemView) {
                super(itemView);
                coverView = itemView.findViewById(R.id.short_video_cover);
                playerContainer = itemView.findViewById(R.id.short_video_player_container);
                titleView = itemView.findViewById(R.id.short_video_title);
                indexView = itemView.findViewById(R.id.short_video_index);
            }
        }
    }
}
