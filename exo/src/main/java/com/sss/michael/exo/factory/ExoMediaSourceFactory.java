package com.sss.michael.exo.factory;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.MainThread;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

import com.sss.michael.exo.cache.ExoCacheManager;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.core.ExoPlayerInfo;
import com.sss.michael.exo.util.ExoLog;
import com.sss.michael.exo.util.ExoPlayerUtils;

/**
 * @author Michael by 61642
 * @date 2025/12/29 16:21
 * @Description 根据 ExoPlayMode 动态创建对应的 MediaSource 和 MediaItem
 */
@UnstableApi
public class ExoMediaSourceFactory {

    // ====================== 静态常量 ======================
    // HTTP 超时配置（不同模式）
    private static final int SHORT_VIDEO_HTTP_TIMEOUT_MS = 4000;
    private static final int LIVE_HTTP_TIMEOUT_MS = 8000;
    private static final int VOD_HTTP_TIMEOUT_MS = 6000;
    // HLS 重试配置（不同模式）
    private static final int SHORT_VIDEO_RETRY_COUNT = 1;
    private static final int LIVE_RETRY_COUNT = 3;
    private static final int VOD_RETRY_COUNT = 2;
    // Live 配置参数
    private static final int LIVE_TARGET_OFFSET_MS = 8000;
    private static final int LIVE_MIN_OFFSET_MS = 3000;
    private static final int LIVE_MAX_OFFSET_MS = 15000;
    // 媒体类型后缀
    private static final String SUFFIX_RTSP = "rtsp://";
    private static final String SUFFIX_M3U8 = ".m3u8";
    private static final String SUFFIX_MPD = ".mpd";

    /**
     * 私有构造器：防止实例化（和 ExoLoadControlFactory 保持一致，静态工具类无需实例化）
     */
    private ExoMediaSourceFactory() {
    }

    /**
     * 根据 ExoPlayMode 动态创建 MediaItem
     */
    @MainThread
    public static MediaItem buildMediaItem(ExoPlayMode playMode, Uri uri) {
        // 空安全校验
        if (uri == null || playMode == null) {
            ExoLog.log("buildMediaItem：uri 或 playMode 为空");
            return null;
        }

        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);

        // 根据 playMode 动态配置：仅 LIVE 模式设置 LiveConfiguration
        switch (playMode) {
            case LIVE:
                builder.setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                                .setMinOffsetMs(LIVE_MIN_OFFSET_MS)
                                .setMaxOffsetMs(LIVE_MAX_OFFSET_MS)
                                .build()
                );
                break;
            case SHORT_VIDEO:
            case VOD:
            default:
                // 非直播模式：不设置 LiveConfiguration，无额外配置
                break;
        }

        return builder.build();
    }

    /**
     * 根据 ExoPlayMode 动态创建 MediaSource
     */
    @OptIn(markerClass = UnstableApi.class)
    @MainThread
    public static MediaSource buildMediaSource(Context context, ExoPlayMode playMode, Uri uri, ExoPlayerInfo playerInfo) {
        if (context == null || playMode == null || uri == null) {
            ExoLog.log("buildMediaSource：context/playMode/uri 为空");
            return null;
        }

        String path = uri.toString().toLowerCase();
        DataSource.Factory dataSourceFactory = getDataSourceFactory(context, playMode, playerInfo);

        // 根据媒体类型与 playMode 动态创建 MediaSource
        if (path.startsWith(SUFFIX_RTSP)) {
            return new RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(uri));
        } else if (path.contains(SUFFIX_M3U8)) {
            return new HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(getHlsLoadErrorPolicy(playMode))
                    .createMediaSource(buildMediaItem(playMode, uri));
        } else if (path.contains(SUFFIX_MPD)) {
            return new DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(buildMediaItem(playMode, uri));
        } else {
            DataSource.Factory finalFactory;
            if (ExoCacheManager.getConfig().getCacheDir() == null) {
                ExoLog.log("警告：缓存目录未设置，将使用纯网络数据源播放，请在application中初始化时设置");
                finalFactory = dataSourceFactory;
            } else {
                finalFactory = ExoCacheManager.getCacheDataSourceFactory(context);
            }

            return new ProgressiveMediaSource.Factory(finalFactory)
                    .createMediaSource(buildMediaItem(playMode, uri));
        }
    }

    /**
     * 根据 ExoPlayMode 获取 DataSource.Factory
     */
    @OptIn(markerClass = UnstableApi.class)
    private static DataSource.Factory getDataSourceFactory(Context context, ExoPlayMode playMode, ExoPlayerInfo playerInfo) {
        // 根据 playMode 动态配置 HTTP 超时时间
        int httpTimeoutMs;
        switch (playMode) {
            case SHORT_VIDEO:
                httpTimeoutMs = SHORT_VIDEO_HTTP_TIMEOUT_MS;
                break;
            case LIVE:
                httpTimeoutMs = LIVE_HTTP_TIMEOUT_MS;
                break;
            case VOD:
            default:
                httpTimeoutMs = VOD_HTTP_TIMEOUT_MS;
                break;
        }

        // 构建 HTTP 数据源工厂（带传输监听）
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(httpTimeoutMs)
                .setReadTimeoutMs(httpTimeoutMs)
                .setTransferListener(getTransferListener(playerInfo));

        // 返回默认数据源工厂
        return new DefaultDataSource.Factory(context, httpFactory);
    }

    /**
     * 根据 ExoPlayMode 获取 HLS 错误处理策略
     */
    @OptIn(markerClass = UnstableApi.class)
    private static DefaultLoadErrorHandlingPolicy getHlsLoadErrorPolicy(ExoPlayMode playMode) {
        // 根据 playMode 动态配置重试次数
        int retryCount;
        switch (playMode) {
            case LIVE:
                retryCount = LIVE_RETRY_COUNT;
                break;
            case VOD:
                retryCount = VOD_RETRY_COUNT;
                break;
            case SHORT_VIDEO:
            default:
                retryCount = SHORT_VIDEO_RETRY_COUNT;
                break;
        }

        return new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorInfo info) {
                if (ExoPlayerUtils.isBehindLiveWindow(info.exception)) {
                    return C.TIME_UNSET;
                }
                return super.getRetryDelayMsFor(info);
            }

            @Override
            public int getMinimumLoadableRetryCount(int dataType) {
                return retryCount;
            }
        };
    }

    /**
     * 获取传输监听器
     */
    @OptIn(markerClass = UnstableApi.class)
    private static TransferListener getTransferListener(ExoPlayerInfo playerInfo) {
        return new TransferListener() {
            @Override
            public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }

            @Override
            public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }

            @Override
            public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
                if (isNetwork && playerInfo != null) {
                    playerInfo.setTotalBytes(playerInfo.getTotalBytes() + bytesTransferred);
                    playerInfo.setBytesInLastSecond(playerInfo.getBytesInLastSecond() + bytesTransferred);
                }
            }

            @Override
            public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }
        };
    }
}