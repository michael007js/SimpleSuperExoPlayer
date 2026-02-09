package com.sss.michael.exo.core;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Looper;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;

import com.sss.michael.exo.ExoConfig;
import com.sss.michael.exo.SimpleExoPlayerView;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.callback.IExoNotifyCallBack;
import com.sss.michael.exo.callback.IExoPlayerListener;
import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.constant.ExoPlaybackState;
import com.sss.michael.exo.constant.ExoPlayerMode;
import com.sss.michael.exo.constant.ExoScreenOrientation;
import com.sss.michael.exo.factory.ExoMediaSourceFactory;
import com.sss.michael.exo.helper.ExoOrientationHelper;
import com.sss.michael.exo.processor.ExoBaseAudioProcessor;
import com.sss.michael.exo.util.ExoCutoutUtil;
import com.sss.michael.exo.util.ExoLog;
import com.sss.michael.exo.util.ExoPlayerUtils;

/**
 * @author Michael by 61642
 * @date 2025/12/24 17:05
 * @Description exo核心播放能力实现
 */
@SuppressWarnings("all")
public class ExoVideoView extends ExoVideoCore {
    // 是否开启重力旋转
    private boolean enableOrientation = false;
    // surface是否绑定
    private boolean isSurfaceReadyed = false;
    // 暂停前的播放器播放状态
    private boolean lastPlayWhenReadyBeforePaused = false;
    // 待播放地址
    private String pendingUrl;
    // 待播放类型
    private ExoPlayMode pendingMode;
    // 断点续播（直播类型下无效）
    private long pendingLastPlayTime;
    // 设备方向监听
    private ExoOrientationHelper exoOrientationHelper;
    // 承载播放器的真实容器
    private ViewGroup playerContainer;
    // 播放器视图
    private View playerView;
    private IExoPlayerListener playListener;

    // <editor-fold defaultstate="collapsed" desc="初始化构建">

    public ExoVideoView(Context context, IExoNotifyCallBack iExoNotifyCallBack, IExoFFTCallBack iExoFFTCallBack) {
        super(context, iExoNotifyCallBack, iExoFFTCallBack);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        player.setVolume(1.0f);
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        setScaleMode(playerInfo.getScaleMode());
        if (playListener == null) {
            playListener = new IExoPlayerListener() {
                @Override
                public void onExoPlayerError(PlaybackException error) {
                    if (playerInfo.getCurrentRetryCountWhileFail() >= ExoConfig.MAX_RETRY_LIMIT_PLAY_REQUEST_WHILE_ERROR) {
                        ExoLog.log("播放错误：已达最大重试次数 " + ExoConfig.MAX_RETRY_LIMIT_PLAY_REQUEST_WHILE_ERROR + "，停止重试。Error: " + error.getMessage());
                        if (iExoNotifyCallBack != null) {
                            onPlaybackStateChanged(ExoPlaybackState.STATE_ENDED);
                            iExoNotifyCallBack.onPlayerError("播放失败，请检查网络后重试", error);
                        }
                        return;
                    }
                    playerInfo.setCurrentRetryCountWhileFail(playerInfo.getCurrentRetryCountWhileFail() + 1);
                    // 计算延时时间：第一次2s, 第二次6s, 第三次14s...
                    // 算法：(2^(n) - 1) * 2000 毫秒
                    long delayMs = ((long) Math.pow(2, playerInfo.getCurrentRetryCountWhileFail()) - 1) * 2000;

                    ExoLog.log("播放错误: " + error.getMessage() + "，尝试进行第 " + playerInfo.getCurrentRetryCountWhileFail() + " 次重试，延时 " + delayMs + "ms");
                    // 取消之前的重试任务防止叠加
                    if (retryRunnable != null) mainHandler.removeCallbacks(retryRunnable);
                    retryRunnable = () -> {
                        ExoLog.log("开始执行第 " + playerInfo.getCurrentRetryCountWhileFail() + " 次错误重试...");
                        boolean isBehindLiveWindow = false;
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            isBehindLiveWindow = true;
                        } else {
                            isBehindLiveWindow = ExoPlayerUtils.isBehindLiveWindow(error.getCause());
                        }
                        if (isBehindLiveWindow) {
                            // 【场景 A】直播流落后（HLS/DASH 常见问题）
                            // 策略：不销毁播放器，而是直接“跳”到当前直播的最前端（Live Edge）
                            ExoLog.log("检测到直播落后窗口，执行自动复位...");

                            player.seekToDefaultPosition();
                            tryPlayInternal(true, true);
                        } else {
                            // 【场景 B】其他严重错误（网络断开、文件损坏、解码器崩溃等）
                            // 策略：执行“核弹级”重置，彻底销毁资源并重建
                            ExoLog.log("播放严重错误(非直播窗口问题): " + error.getMessage() + ", 执行重建...");
                            buildSource(false, true, "自动重建");
                        }
                    };

                    mainHandler.postDelayed(retryRunnable, delayMs);
                }

                // 只跳转一次
                boolean seeked;
                private int bufferingCount = 0;
                private long lastBufferingTime = 0;

                @Override
                public void onExoPlaybackStateChanged(int state) {
                    ExoVideoView.this.setPlaybackState(state);
                    if (state == Player.STATE_BUFFERING) {
                        long now = System.currentTimeMillis();
                        // 如果两次缓冲间隔小于 500ms，判定为异常循环
                        if (now - lastBufferingTime < 500) {
                            bufferingCount++;
                        } else {
                            bufferingCount = 0;
                        }
                        lastBufferingTime = now;

                        if (bufferingCount >= 3) {
                            ExoLog.log("检测到缓冲死循环，强制执行直播边缘对齐...");
                            bufferingCount = 0;
                            // 强制 Seek 到直播流的最前端，舍弃掉出错的旧缓存
                            player.seekToDefaultPosition();
                        }
                    }
                    if (state == ExoPlaybackState.STATE_PLAYING) {
                        playerInfo.setCurrentRetryCountWhileFail(0);
                        ExoLog.log("进入PLAYING状态，playWhenReady: " + player.getPlayWhenReady());
                    }
                    if (state == Player.STATE_READY) {
                        // 断点续播
                        if (!seeked && pendingLastPlayTime > 0) {
                            seeked = true;
                            player.seekTo(pendingLastPlayTime);
                            ExoLog.log("监听到 STATE_READY，执行断点续播时间跳转: " + pendingLastPlayTime);
                            pendingLastPlayTime = 0;
                            tryPlayInternal(false, true);
                        } else {
                            tryPlayInternal(false, true);
                        }
                        ExoLog.log("进入READY状态，playWhenReady: " + player.getPlayWhenReady() + ", isPlaying: " + player.isPlaying());
                    }
                }

                @Override
                protected void onExoRenderedFirstFrame() {
                    if (iExoNotifyCallBack != null) {
                        iExoNotifyCallBack.onExoRenderedFirstFrame();
                        setPlayerState(isFullScreen() ? ExoPlayerMode.PLAYER_FULL_SCREEN : ExoPlayerMode.PLAYER_NORMAL);
                        SimpleExoPlayerView view = (SimpleExoPlayerView) iExoNotifyCallBack;
                        playerInfo.setExoRenderedFirstFramed(true);
                        iExoNotifyCallBack.onVideoSizeChanged(view.getTextureView(), playerInfo.getPixelWidthHeightRatio(), playerInfo.getVideoWidth(), playerInfo.getVideoHeight(), playerInfo.getScaleMode());
                    }
                }

                @Override
                public void onExoIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        onPlaybackStateChanged(ExoPlaybackState.STATE_PLAYING);
                    } else {
                        int playbackState = player.getPlaybackState();
                        if (playbackState == Player.STATE_ENDED) {
                            ExoLog.log("onIsPlayingChanged: 播放完成");
                        } else if (playbackState == Player.STATE_BUFFERING) {
                            ExoLog.log("onIsPlayingChanged: 正在缓冲...");
                        } else {
                            ExoLog.log("onIsPlayingChanged: 手动暂停");
                            onPlaybackStateChanged(ExoPlaybackState.STATE_PLAY_PAUSE);
                        }
                    }
                }

                @Override
                public void onExoVideoSizeChanged(@NonNull VideoSize videoSize) {
                    playerInfo.setPixelWidthHeightRatio(videoSize.pixelWidthHeightRatio);
                    playerInfo.setVideoWidth(videoSize.width);
                    playerInfo.setVideoHeight(videoSize.height);
                    playerInfo.setScaleMode(playerInfo.getScaleMode());
                    if (iExoNotifyCallBack != null) {
                        try {
                            SimpleExoPlayerView exoPlayerView = (SimpleExoPlayerView) iExoNotifyCallBack;
                            TextureView textureView = exoPlayerView.getTextureView();

                            if (textureView != null && textureView.getWidth() > 0 && textureView.getHeight() > 0
                                    && videoSize.width > 0 && videoSize.height > 0) {

                                // 计算视频内容在 TextureView 内部的显示区域（相对 TextureView 坐标）
                                RectF videoInTextureRect = calculateVideoContentRect(
                                        textureView.getWidth(),    // TextureView 宽度
                                        textureView.getHeight(),   // TextureView 高度
                                        videoSize.width,           // 视频原始宽度
                                        videoSize.height,          // 视频原始高度
                                        playerInfo.getScaleMode()  // 自定义缩放模式
                                );

                                // 计算：视频相对于父容器的 Rect
                                if (textureView.getParent() instanceof ViewGroup) {
                                    ViewGroup textureParent = (ViewGroup) textureView.getParent();
                                    // 获取 TextureView 相对于父容器的偏移（左、上）
                                    int textureLeft = textureView.getLeft();
                                    int textureTop = textureView.getTop();
                                    // 转换为视频相对于父容器的 Rect
                                    Rect videoInParentRect = new Rect();
                                    videoInTextureRect.roundOut(videoInParentRect);
                                    videoInParentRect.offset(textureLeft, textureTop);

                                    ExoLog.log("【视频相对父容器Rect】：" + videoInParentRect.toShortString());
                                    playerInfo.setVideoInParentRect(videoInParentRect);
                                }

                                // 获取 TextureView 相对于屏幕的全局可见区域
                                Rect textureScreenRect = new Rect();
                                textureView.getGlobalVisibleRect(textureScreenRect);
                                // 转换为视频相对于屏幕的 Rect（视频在 TextureView 内的坐标 + TextureView 屏幕坐标偏移）
                                Rect videoInScreenRect = new Rect();
                                videoInTextureRect.roundOut(videoInScreenRect);
                                videoInScreenRect.offset(textureScreenRect.left, textureScreenRect.top);

                                ExoLog.log("【视频相对屏幕Rect】：" + videoInScreenRect.toShortString());
                                ExoLog.log("  - TextureView屏幕Rect：" + textureScreenRect.toShortString());
                                ExoLog.log("  - 视频在TextureView内Rect：" + videoInTextureRect.toShortString());

                                playerInfo.setVideoInScreenRect(videoInScreenRect);

                            } else {
                                ExoLog.log("【视频Rect计算】：TextureView未初始化/尺寸为0，暂不计算");
                            }
                        } catch (Exception e) {
                            ExoLog.log("【视频Rect计算】失败：" + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (iExoNotifyCallBack != null) {
                        SimpleExoPlayerView view = (SimpleExoPlayerView) iExoNotifyCallBack;
                        iExoNotifyCallBack.onVideoSizeChanged(view.getTextureView(), videoSize.pixelWidthHeightRatio, videoSize.width, videoSize.height, playerInfo.getScaleMode());
                        iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
                    }
                }

                /**
                 * 计算视频内容在 TextureView 内部的显示区域（相对 TextureView 坐标）
                 */
                private RectF calculateVideoContentRect(int viewWidth, int viewHeight, int videoWidth, int videoHeight, int customScaleMode) {
                    // 边界值保护：尺寸为0时返回控件全区域
                    if (videoWidth == 0 || videoHeight == 0 || viewWidth == 0 || viewHeight == 0) {
                        return new RectF(0, 0, viewWidth, viewHeight);
                    }

                    float videoRatio = (float) videoWidth / videoHeight; // 视频原始宽高比
                    float targetRatio = videoRatio; // 默认使用视频原始比例
                    float[] scaleX = new float[1];  // 用数组包装实现引用传递
                    float[] scaleY = new float[1];
                    float[] dx = new float[1];
                    float[] dy = new float[1];

                    // 针对自定义缩放模式的核心计算逻辑
                    switch (customScaleMode) {
                        case ExoCoreScale.SCALE_FIT: // 居中适配（黑边模式）
                            scaleX[0] = scaleY[0] = Math.min((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
                            dx[0] = (viewWidth - videoWidth * scaleX[0]) / 2; // 水平居中偏移
                            dy[0] = (viewHeight - videoHeight * scaleY[0]) / 2; // 垂直居中偏移
                            break;

                        case ExoCoreScale.SCALE_FILL_CUT: // 居中裁剪（无黑边）
                            scaleX[0] = scaleY[0] = Math.max((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
                            dx[0] = (viewWidth - videoWidth * scaleX[0]) / 2; // 水平居中偏移
                            dy[0] = (viewHeight - videoHeight * scaleY[0]) / 2; // 垂直居中偏移
                            break;

                        case ExoCoreScale.SCALE_STRETCH: // 拉伸填满
                            // 非等比缩放，直接拉伸视频填满整个控件
                            scaleX[0] = (float) viewWidth / videoWidth;
                            scaleY[0] = (float) viewHeight / videoHeight;
                            dx[0] = 0;
                            dy[0] = 0;
                            break;

                        case ExoCoreScale.SCALE_16_9: // 强制16:9显示
                            targetRatio = 16f / 9f;
                            calculateRatioBasedRect(viewWidth, viewHeight, videoWidth, videoHeight, targetRatio,
                                    false, scaleX, scaleY, dx, dy);
                            break;

                        case ExoCoreScale.SCALE_21_9: // 强制21:9显示
                            targetRatio = 21f / 9f;
                            calculateRatioBasedRect(viewWidth, viewHeight, videoWidth, videoHeight, targetRatio,
                                    false, scaleX, scaleY, dx, dy);
                            break;

                        case ExoCoreScale.SCALE_AUTO: // 自动适配
                            // 自动判断：视频比例接近16:9则用16:9，接近21:9则用21:9，否则用原始比例适配
                            if (Math.abs(videoRatio - 16f / 9f) < 0.1) { // 误差0.1以内视为16:9
                                targetRatio = 16f / 9f;
                            } else if (Math.abs(videoRatio - 21f / 9f) < 0.1) { // 误差0.1以内视为21:9
                                targetRatio = 21f / 9f;
                            }
                            calculateRatioBasedRect(viewWidth, viewHeight, videoWidth, videoHeight, targetRatio,
                                    false, scaleX, scaleY, dx, dy);
                            break;

                        default: // 默认使用SCALE_FIT模式
                            scaleX[0] = scaleY[0] = Math.min((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
                            dx[0] = (viewWidth - videoWidth * scaleX[0]) / 2;
                            dy[0] = (viewHeight - videoHeight * scaleY[0]) / 2;
                            break;
                    }

                    // 计算最终的视频内容显示区域（相对TextureView）
                    return new RectF(
                            dx[0],
                            dy[0],
                            videoWidth * scaleX[0] + dx[0],
                            videoHeight * scaleY[0] + dy[0]
                    );
                }

                /**
                 * 按指定目标比例计算视频显示区域
                 * @param isCrop 是否裁剪模式
                 * @param scaleX 输出：X轴缩放比例（数组包装）
                 * @param scaleY 输出：Y轴缩放比例（数组包装）
                 * @param dx 输出：水平偏移（数组包装）
                 * @param dy 输出：垂直偏移（数组包装）
                 */
                private void calculateRatioBasedRect(int viewWidth, int viewHeight, int videoWidth, int videoHeight,
                                                     float targetRatio, boolean isCrop,
                                                     float[] scaleX, float[] scaleY, float[] dx, float[] dy) {
                    // 先将视频缩放到目标比例
                    float scaledVideoWidth = videoHeight * targetRatio;
                    float scale = isCrop
                            ? Math.max((float) viewWidth / scaledVideoWidth, (float) viewHeight / videoHeight)
                            : Math.min((float) viewWidth / scaledVideoWidth, (float) viewHeight / videoHeight);

                    scaleX[0] = scale;
                    scaleY[0] = scale;
                    dx[0] = (viewWidth - scaledVideoWidth * scale) / 2;
                    dy[0] = (viewHeight - videoHeight * scale) / 2;
                }


            };
            player.addListener(playListener);
        }
        exoOrientationHelper = new ExoOrientationHelper(context.getApplicationContext());
        exoOrientationHelper.setOnOrientationChangeListener(new ExoOrientationHelper.OnOrientationChangeListener() {
            @Override
            public void onOrientationChanged(int orientation) {
                playerInfo.setOrientationAngle(orientation);
                if (iExoNotifyCallBack != null) {
                    iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
                }
                Activity activity = ExoPlayerUtils.scanForActivity(context);
                if (activity == null || activity.isFinishing()) {
                    return;
                }
                if (!enableOrientation) {
                    return;
                }
                //记录用户手机上一次放置的位置
                int lastOrientation = playerInfo.getOrientationDirection();

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    //手机平放时，检测不到有效的角度
                    //重置为原始位置 -1
                    playerInfo.setOrientationDirection(OrientationEventListener.ORIENTATION_UNKNOWN);
                    if (iExoNotifyCallBack != null) {
                        iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
                    }
                    return;
                }

                if (orientation > 350 || orientation < 10) {
                    int o = activity.getRequestedOrientation();
                    //手动切换横竖屏
                    if (o == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && lastOrientation == 0) {
                        return;
                    }
                    if (playerInfo.getOrientationDirection() == 0) {
                        return;
                    }
                    //0度，用户竖直拿着手机
                    playerInfo.setOrientationDirection(0);
                    if (iExoNotifyCallBack != null) {
                        iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
                    }

                    //没有开启设备方向监听的情况
                    if (!enableOrientation) {
                        return;
                    }
                    stopFullScreen(true);
                } else if (orientation > 80 && orientation < 100) {
                    int o = activity.getRequestedOrientation();
                    //手动切换横竖屏
                    if (o == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && lastOrientation == 90)
                        return;
                    if (playerInfo.getOrientationDirection() == 90) return;
                    //90度，用户右侧横屏拿着手机
                    playerInfo.setOrientationDirection(90);
                    ExoPlayerUtils.setScreenOrientation(activity, ExoScreenOrientation.ORIENTATION_RIGHT);
                    if (isFullScreen()) {
                        setPlayerState(ExoPlayerMode.PLAYER_FULL_SCREEN);
                    } else {
                        startFullScreen(false);
                    }
                } else if (orientation > 260 && orientation < 280) {
                    int o = activity.getRequestedOrientation();
                    //手动切换横竖屏
                    if (o == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && lastOrientation == 270)
                        return;
                    if (playerInfo.getOrientationDirection() == 270) return;
                    //270度，用户左侧横屏拿着手机
                    playerInfo.setOrientationDirection(270);
                    ExoPlayerUtils.setScreenOrientation(activity, ExoScreenOrientation.ORIENTATION_LEFT);
                    if (isFullScreen()) {
                        setPlayerState(ExoPlayerMode.PLAYER_FULL_SCREEN);
                    } else {
                        startFullScreen(false);
                    }
                }
            }
        });
        exoOrientationHelper.enable();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="非回调类对外API函数">

    public View getPlayerView() {
        return playerView;
    }

    public ViewGroup getPlayerContainer() {
        return playerContainer;
    }

    /**
     * 构建媒体资源
     *
     * @param refreshPlay   是否刷新播放
     * @param playWhenReady 播放器准备好后是否直接播放
     * @param taskName      任务名称
     */
    @OptIn(markerClass = UnstableApi.class)
    @androidx.annotation.MainThread
    protected void buildSource(boolean refreshPlay, boolean playWhenReady, String taskName) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> buildSource(refreshPlay, playWhenReady, taskName));
            return;
        }
        if (player == null || playerInfo == null || playerInfo.getUri() == null) {
            ExoLog.log(taskName + "：播放器/播放信息/Uri 为空，跳过重建");
            return;
        }
        try {
            long lastPositionBefore = player.getCurrentPosition();
            boolean lastPlayWhenReady = player.getPlayWhenReady();

            ExoLog.log("触发" + taskName + "重建 MediaSource");

            if (mainHandler != null && retryRunnable != null) {
                mainHandler.removeCallbacks(retryRunnable);
                retryRunnable = null;
            }

            player.stop();
            player.clearMediaItems();

            playerInfo.setBytesInLastSecond(0);
            MediaSource source = ExoMediaSourceFactory.buildMediaSource(
                    mContext,
                    playerInfo.getExoPlayMode(),
                    playerInfo.getUri(),
                    playerInfo
            );
            if (source == null) {
                RuntimeException runtimeException = new RuntimeException("media source创建失败");
                player.stop();
                ExoLog.log(taskName + "失败", runtimeException);
                if (iExoNotifyCallBack != null) {
                    iExoNotifyCallBack.onPlayerError(taskName + "失败", runtimeException);
                }
                return;
            }

            player.setMediaSource(source);

            if (refreshPlay) {
                // 先恢复播放位置
                if (lastPositionBefore > 0) {
                    player.seekTo(lastPositionBefore);
                }
                tryPlayInternal(true, lastPlayWhenReady);
            } else {
                tryPlayInternal(true, playWhenReady);
            }
        } catch (Exception e) {
            ExoLog.log(taskName + "失败", e);
            if (mainHandler != null && retryRunnable != null) {
                mainHandler.removeCallbacks(retryRunnable);
                retryRunnable = null;
            }
            if (player != null) {
                player.stop();
            }
            if (iExoNotifyCallBack != null) {
                iExoNotifyCallBack.onPlayerError(taskName + "失败", e);
            }
        }
    }

    /**
     * 播放器形态码解析
     */
    protected void setPlayerState(int playerState) {

        String playerStateName;
        switch (playerState) {
            case ExoPlayerMode.PLAYER_NORMAL:
                playerStateName = "普通播放";
                break;
            case ExoPlayerMode.PLAYER_FULL_SCREEN:
                playerStateName = "全屏播放";
                break;
            default:
                playerStateName = "未知";
                break;
        }
        playerInfo.setPlayerState(playerState);
        playerInfo.setPlayerStateName(playerStateName);
        ExoLog.log("播放形态=" + playerStateName);
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerStateChanged(playerState, playerStateName, playerView);
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        }
    }

    /**
     * 播放器播放状态码解析
     */
    protected void setPlaybackState(int playbackState) {

        boolean reallyPlaying = player.getPlaybackState() == Player.STATE_READY && player.isPlaying();
        if (reallyPlaying) {
            //额外解决已播放过  但由于线程问题卡在未播放状态
            playbackState = ExoPlaybackState.STATE_PLAYING;
        }
        String playbackStateName = getPlaybackStateDesc(playbackState);
        playerInfo.setPlaybackState(playbackState);
        playerInfo.setPlaybackStateName(playbackStateName);
        ExoLog.log("播放状态=" + playbackStateName);
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlaybackStateChanged(playbackState, playbackStateName);
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        }
    }

    /**
     * 尝试内部播放（所有播放需求均需调用此方法，禁止直接调用player.setPlayWhenReady/play）
     *
     * @param callFromRunnable 是否来自主线程任务
     * @param playWhenReady    是否需要自动播放
     */
    protected void tryPlayInternal(boolean callFromRunnable, boolean playWhenReady) {
        int state = playerInfo.getPlaybackState();
        if (state == ExoPlaybackState.STATE_BUFFERING && player.isPlaying()) {
            ExoLog.log("缓冲中且正在播放，跳过 tryPlayInternal 避免卡死");
            return;
        }

        int playerRealState = player.getPlaybackState();
        String stateDesc = getPlaybackStateDesc(playerRealState);

        if (playerRealState != Player.STATE_READY) {
            // 播放器未就绪，执行准备操作 + 设置自动播放标记
            player.prepare();
            setPlayWhenReady(playWhenReady);
            ExoLog.log(String.format("tryPlayInternal：播放器未就绪（当前状态：%s），执行prepare()，设置playWhenReady=%b，来源：callFromRunnable=%b",
                    stateDesc, playWhenReady, callFromRunnable));
        } else {
            // 播放器已就绪，执行播放/暂停操作
            setPlayWhenReady(playWhenReady);
            if (playWhenReady) {
                // 需要自动播放，调用play()
                player.play();
                ExoLog.log(String.format("tryPlayInternal：播放器已就绪，执行播放操作，playWhenReady=%b，来源：callFromRunnable=%b",
                        playWhenReady, callFromRunnable));
            } else {
                // 需要暂停，不播放
                ExoLog.log(String.format("tryPlayInternal：播放器已就绪，执行暂停操作，playWhenReady=%b，来源：callFromRunnable=%b",
                        playWhenReady, callFromRunnable));
            }
        }
    }

    /**
     * 获取播放状态说明
     *
     * @param playbackState 播放状态代码
     * @return 播放状态说明
     */
    private String getPlaybackStateDesc(int playbackState) {
        switch (playbackState) {
            case ExoPlaybackState.STATE_IDLE:
                return "空闲";
            case ExoPlaybackState.STATE_BUFFERING:
                playerInfo.setBufferedPercentage(player.getBufferedPercentage());
                return "缓冲中";
            case ExoPlaybackState.STATE_READY:
                return "就绪";
            case ExoPlaybackState.STATE_ENDED:
                return "结束";
            case ExoPlaybackState.STATE_PLAYING:
                return "播放中";
            case ExoPlaybackState.STATE_PLAY_PAUSE:
                return "暂停中";
            default:
                return "未知";
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="播放器核心控制">

    /**
     * 设置视频的缩放/拉伸模式
     *
     * @param mode 取值 {@link ExoCoreScale}中
     */
    @Override
    public void setScaleMode(int mode) {
        if (mode != ExoCoreScale.SCALE_FIT && mode != ExoCoreScale.SCALE_FILL_CUT && mode != ExoCoreScale.SCALE_STRETCH
                && mode != ExoCoreScale.SCALE_16_9 && mode != ExoCoreScale.SCALE_21_9 && mode != ExoCoreScale.SCALE_AUTO)
            return;
        ExoLog.log("缩放模式：" + ExoCoreScale.getScaleModeName(mode));
        playerInfo.setScaleMode(mode);
        if (player != null) {
            if (mode == ExoCoreScale.SCALE_FILL_CUT) {
                player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            } else {
                player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            }
        }

        if (iExoNotifyCallBack != null && player != null) {
            VideoSize videoSize = player.getVideoSize();
            SimpleExoPlayerView view = (SimpleExoPlayerView) iExoNotifyCallBack;
            iExoNotifyCallBack.onVideoSizeChanged(view.getTextureView(), videoSize.pixelWidthHeightRatio, videoSize.width, videoSize.height, playerInfo.getScaleMode());
        }
    }

    /**
     * 获取当前视频应用的缩放模式
     *
     * @return 对应的模式枚举值
     */
    @Override
    public int getScaleMode() {
        return playerInfo.getScaleMode();
    }

    /**
     * 设置播放速度
     *
     * @param speed 速度
     */
    @Override
    public void setSpeed(float speed) {
        if (playerInfo.getSpeed() == speed) {
            return;
        }
        if (speed <= 0) speed = 1.0f;
        if (player != null) {
            // PlaybackParameters 允许同时设置速度和音调
            PlaybackParameters params = new PlaybackParameters(speed);
            player.setPlaybackParameters(params);

            // 更新 playerInfo 方便 UI 逻辑使用
            playerInfo.setSpeed(speed);
            if (iExoNotifyCallBack != null) {
                iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
            }
        }
    }

    /**
     * 获取播放速度
     *
     * @return 速度
     */
    @Override
    public float getSpeed() {
        if (player != null) {
            return player.getPlaybackParameters().speed;
        }
        return 1.0f;
    }

    /**
     * 启动全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到90度
     */
    @Override
    public void startFullScreen(boolean callFromActive) {
        if (isFullScreen()) return;
        Activity activity = ExoPlayerUtils.scanForActivity(mContext);
        if (activity == null) return;
        boolean isCutoutDevice = ExoCutoutUtil.allowDisplayToCutout(activity);
        if (isCutoutDevice) {
            ExoCutoutUtil.adaptCutoutAboveAndroidP(mContext, ExoConfig.ALLOW_DISPLAY_TO_CUTOUT);
            ExoLog.log("全屏适配：检测到挖孔屏/刘海屏机型，已适配内容禁止延伸至挖孔区域");
        }
        if (callFromActive) {
            playerInfo.setOrientationDirection(90);
            ExoPlayerUtils.setScreenOrientation(activity, ExoScreenOrientation.ORIENTATION_RIGHT);
        }
        ViewGroup decorView = ExoPlayerUtils.getDecorView(mContext);
        if (decorView == null)
            return;
        //从当前FrameLayout中移除播放器视图
        if (playerView.getParent() != null) {
            ((ViewGroup) playerView.getParent()).removeView(playerView);
        }
        playerInfo.setFullScreen(true);
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        }
        ExoPlayerUtils.toggleImmersiveMode(activity, true);


        //将播放器视图添加到DecorView中即实现了全屏
        decorView.addView(playerView);

        setPlayerState(ExoPlayerMode.PLAYER_FULL_SCREEN);
    }

    /**
     * 停止全屏
     *
     * @param callFromActive 主动操作，将额外旋转屏幕到0度
     */
    @Override
    public void stopFullScreen(boolean callFromActive) {
        if (!isFullScreen()) return;
        Activity activity = ExoPlayerUtils.scanForActivity(mContext);
        if (activity == null) return;
        boolean isCutoutDevice = ExoCutoutUtil.allowDisplayToCutout(activity);
        if (isCutoutDevice) {
            ExoCutoutUtil.adaptCutoutAboveAndroidP(mContext, false);
            ExoLog.log("退出全屏：恢复挖孔屏/刘海屏默认适配逻辑");
        }
        if (callFromActive) {
            playerInfo.setOrientationDirection(180);
            ExoPlayerUtils.setScreenOrientation(activity, ExoScreenOrientation.ORIENTATION_PORTRAIT_USER);
        }
        ViewGroup decorView = ExoPlayerUtils.getDecorView(mContext);
        if (decorView == null)
            return;
        if (playerView.getParent() != null) {
            ((ViewGroup) playerView.getParent()).removeView(playerView);
        }
        playerInfo.setFullScreen(false);
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        }
        ExoPlayerUtils.toggleImmersiveMode(activity, false);

        //把播放器视图从DecorView中移除并添加到当前FrameLayout中即退出了全屏
        playerContainer.addView(playerView);

        setPlayerState(ExoPlayerMode.PLAYER_NORMAL);
    }

    /**
     * 是否全屏
     *
     * @return 全屏模式
     */
    @Override
    public boolean isFullScreen() {
        return playerInfo.isFullScreen();
    }

    /**
     * 获取播放器实时信息
     *
     * @return 播放器实时信息
     */
    @Override
    public ExoPlayerInfo getExoPlayerInfo() {
        return playerInfo;
    }

    /**
     * 设置试看时间
     * 仅针对于本次播放链接有效
     *
     * @param experienceTimeMs 试看时间 大于0有效
     */
    @Override
    public void setExperienceTime(long experienceTimeMs) {
        this.experienceTimeMs = experienceTimeMs;
        ExoLog.log("体验时间【" + this.experienceTimeMs + "】已设置");
    }

    /**
     * 设置均衡器
     *
     * @param exoEqualizerPreset 均衡器预设值
     */
    @Override
    public void setEqualizer(@NonNull ExoEqualizerPreset exoEqualizerPreset) {
        if (ExoConfig.COMPONENT_EQ_ENABLE && equalizerProcessor != null) {
            float[] gains = exoEqualizerPreset == null ? ExoEqualizerPreset.DEFAULT.getGains() : exoEqualizerPreset.getGains();
            equalizerProcessor.setBandGains(gains);
            ExoLog.log("已切换均衡器预设模式: " + exoEqualizerPreset.getDescription());
        }
    }

    /**
     * 重新播放
     */
    @Override
    public void rePlay() {
        play(playerInfo.getExoPlayMode(), this.pendingLastPlayTime, playerInfo.getUri().toString());
    }

    /**
     * 准备完成后开始自动播放
     *
     * @param playWhenReady true准备好后开始播放
     */
    public void setPlayWhenReady(boolean playWhenReady) {
        if (player != null) {
            player.setPlayWhenReady(playWhenReady);
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
        if (!isSurfaceReadyed) {
            ExoLog.log("Surface 尚未就绪，暂存模式：" + mode + "，断点续播时间：" + lastPlayTime + "，播放请求: " + url);
            this.pendingUrl = url;
            this.pendingMode = mode;
            if (iExoNotifyCallBack != null) {
                iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
            }
            playerInfo.setExoRenderedFirstFramed(false);
            playerInfo.setExoPlayMode(mode);
            if (mode == ExoPlayMode.LIVE) {
                this.pendingLastPlayTime = 0;
            } else {
                this.pendingLastPlayTime = lastPlayTime;
            }
            return;
        }
        // 清除暂存，执行真实播放逻辑
        this.pendingUrl = null;
        this.pendingMode = null;

        setPlayerState(isFullScreen() ? ExoPlayerMode.PLAYER_FULL_SCREEN : ExoPlayerMode.PLAYER_NORMAL);
        playerInfo.setUri(Uri.parse(url));
        playerInfo.setExoPlayMode(mode);
        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
        }
        buildSource(false, true, "播放");
    }

    /**
     * 重置播放器
     * 立即停止所有播放、缓冲、重试等动作，清空媒体资源，重置所有播放状态
     */
    @Override
    public void reset() {
        ExoLog.log("开始执行播放器重置操作");

        if (mainHandler != null && retryRunnable != null) {
            mainHandler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
        playerInfo.setExoRenderedFirstFramed(false);
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            setPlayWhenReady(false);
            player.seekTo(0);
        }

        pendingUrl = null;
        pendingMode = null;
        pendingLastPlayTime = 0;
        lastPlayWhenReadyBeforePaused = false;

        playerInfo.setCurrentRetryCountWhileFail(0);
        playerInfo.setPlaybackState(ExoPlaybackState.STATE_IDLE);
        playerInfo.setFullScreen(false);
        playerInfo.setBytesInLastSecond(0);
        playerInfo.setTotalBytes(0);

        setPlaybackState(ExoPlaybackState.STATE_IDLE);
        setPlayerState(ExoPlayerMode.PLAYER_NORMAL);

        if (iExoNotifyCallBack != null) {
            iExoNotifyCallBack.onPlayerInfoChanged(playerInfo);
            iExoNotifyCallBack.onPlaybackStateChanged(ExoPlaybackState.STATE_IDLE, "空闲");
        }

        ExoLog.log("播放器重置完成：已停止所有动作，清空媒体资源，状态重置为空闲");
    }

    /**
     * 刷新播放（不释放资源）
     * 逻辑：记录当前位置 -> 重新构建数据源 -> 准备播放器 -> 跳转回记录的位置
     */
    @Override
    public void refresh() {
        if (player == null || playerInfo.getUri() == null) {
            ExoLog.log("刷新失败：播放器未初始化或 URL 为空");
            if (iExoNotifyCallBack != null) {
                iExoNotifyCallBack.onPlayerError("刷新失败：播放器未初始化或 URL 为空", new RuntimeException("刷新失败：播放器未初始化或 URL 为空"));
            }
            return;
        }
        playerInfo.setCurrentRetryCountWhileFail(0);
        buildSource(false, true, "刷新");
    }


    /**
     * 暂停播放
     *
     * @param callFromActive 主动操作，如果缓冲结束，将不播放，需要手动继续
     */
    @Override
    public void pause(boolean callFromActive) {
        if (player != null) {
            lastPlayWhenReadyBeforePaused = callFromActive || isPlaying();
            player.pause();
        }
    }

    /**
     * 恢复播放（在暂停状态下调用）
     */
    @Override
    public void resume() {
        if (player != null) {
            tryPlayInternal(false, true);
        }
    }

    /**
     * 停止播放
     * 通常用于切换视频或退出当前页面，会重置播放状态
     */
    @Override
    public void stop() {
        if (player != null) {
            player.stop();
        }
    }


    /**
     * 当前是否正在播放
     *
     * @return true 表示正在播放，false 表示暂停或缓冲中
     */
    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    /**
     * 获取当前视频的总时长
     *
     * @return 单位：毫秒（ms）。如果视频尚未加载完成，可能返回 0 或负值。
     */
    @Override
    public long getDuration() {
        return player == null ? 0 : player.getDuration();
    }

    /**
     * 跳转到指定播放位置
     *
     * @param positionMs 目标位置的时间戳，单位：毫秒（ms）
     */
    @Override
    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
        ExoLog.log("播放位置【" + positionMs + "】已设置");
    }

    /**
     * 获取当前已经播放到的位置
     *
     * @return 单位：毫秒（ms）
     */
    @Override
    public long getCurrentPosition() {
        return player == null ? 0 : player.getCurrentPosition();
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="生命周期">

    /**
     * 窗口焦点已更改
     * 由{@link View#onWindowFocusChanged(boolean)}触发回调后调用
     *
     * @param hasWindowFocus 是否具有窗口焦点
     */
    @Override
    public void windowFocusChanged(boolean hasWindowFocus) {
        if (hasWindowFocus) {

        }
    }

    /**
     * 绑定Surface
     * 由{@link android.view.TextureView} 的 onSurfaceTextureAvailable 函数回调后调用
     *
     * @param surface Surface
     */
    @Override
    public void bindSurfaceWhileTextureAvailable(@NonNull Surface surface) {
        isSurfaceReadyed = true;
        if (player != null) {
            player.setVideoSurface(surface);
        }
        if (pendingUrl != null && pendingMode != null) {
            ExoLog.log("Surface 已就绪，触发暂存的播放任务");
            play(pendingMode, pendingLastPlayTime, pendingUrl);
        }
    }

    /**
     * 解绑Surface
     * 由{@link android.view.TextureView} 的 onSurfaceTextureDestroyed 函数回调后调用
     *
     * @param surface Surface
     */
    @Override
    public void unbindSurfaceTextureWhileDestroyed(@NonNull Surface surface) {
        isSurfaceReadyed = false;
        if (player != null) {
            player.clearVideoSurface(surface);
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
        if (this.playerContainer == null) {
            this.playerContainer = playerContainer;
        }
        this.playerView = playerView;
        playerView.setKeepScreenOn(true);

    }

    /**
     * 页面暂停交互时调用
     */
    @Override
    public void onPause() {
        if (exoMonitorManager != null) {
            exoMonitorManager.pauseMonitor();
        }
        if (exoOrientationHelper != null) {
            exoOrientationHelper.disable();
        }
        pause(false);
    }

    /**
     * 页面恢复交互时调用
     */
    @Override
    public void onResume() {
        if (exoMonitorManager != null) {
            exoMonitorManager.resumeMonitor();
        }
        if (exoOrientationHelper != null) {
            exoOrientationHelper.enable();
        }
        if (player != null) {
            tryPlayInternal(false, lastPlayWhenReadyBeforePaused);
        }
    }

    /**
     * 彻底释放播放器资源
     */
    @Override
    public void release() {
        if (audioProcessors != null) {
            for (ExoBaseAudioProcessor audioProcessor : audioProcessors) {
                audioProcessor.release();
            }
            audioProcessors.clear();
            audioProcessors = null;
        }
        equalizerProcessor = null;
        pendingUrl = null;
        pendingMode = null;
        pendingLastPlayTime = 0;
        if (playerView != null) {
            playerView.setKeepScreenOn(false);
        }
        if (exoOrientationHelper != null) {
            exoOrientationHelper.disable();
            exoOrientationHelper = null;
        }
        if (exoMonitorManager != null) {
            exoMonitorManager.stopMonitor();
            exoMonitorManager = null;
        }
        if (player != null) {
            player.removeListener(playListener);
            player.clearMediaItems();
            player.release();
            player = null;
        }
        playListener = null;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler = null;
        }
        mContext = null;
    }
    // </editor-fold>
}
