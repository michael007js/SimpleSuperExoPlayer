package com.sss.michael.exo.core;

import android.graphics.Rect;
import android.net.Uri;
import android.view.OrientationEventListener;

import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.constant.ExoPlayMode;

/**
 * @author Michael by 61642
 * @date 2025/12/25 16:53
 * @Description 播放器实时信息
 */
public class ExoPlayerInfo {
    private ExoPlayMode exoPlayMode = ExoPlayMode.VOD;
    /**
     * 首帧渲染过，如果为true,表示播放过，并非第一次播放
     */
    private boolean isExoRenderedFirstFramed;
    /**
     * 当前失败后重试次数
     */
    private int currentRetryCountWhileFail = 0;
    /**
     * 缓冲进度
     */
    private int bufferedPercentage;

    /**
     * 媒体日志
     */
    private Uri uri;
    /**
     * 最后一秒的字节数
     */
    private long bytesInLastSecond = 0;
    /**
     * 全部字节数
     */
    private long totalBytes = 0;
    /**
     * 播放器播放状态码
     */
    private int playbackState;
    /**
     * 播放器播放状态名
     */
    private String playbackStateName;
    /**
     * 播放器形态码
     */
    protected int playerState;

    /**
     * 播放器形态名称
     */
    protected String playerStateName;
    /**
     * 像素宽高比
     */
    private float pixelWidthHeightRatio;
    /**
     * 视频宽度
     */
    private int videoWidth;
    /**
     * 视频高度
     */
    private int videoHeight;
    /**
     * 缩放模式
     */
    private int scaleMode = ExoCoreScale.SCALE_AUTO;
    /**
     * 播放速度
     */
    private float speed;
    /**
     * 设备旋转方向 -1 默认、0，竖直、90右侧横屏、270左侧横屏、
     */
    private int orientationDirection = OrientationEventListener.ORIENTATION_UNKNOWN;
    /**
     * 设备旋转角度
     */
    private int orientationAngle = OrientationEventListener.ORIENTATION_UNKNOWN;

    /**
     * 视频相对于父布局尺寸
     */
    private Rect videoInParentRect = new Rect();
    /**
     * 视频相对于屏幕尺寸
     */
    private Rect videoInScreenRect = new Rect();
    private boolean isFullScreen;

    public int getCurrentRetryCountWhileFail() {
        return currentRetryCountWhileFail;
    }

    public void setCurrentRetryCountWhileFail(int currentRetryCountWhileFail) {
        this.currentRetryCountWhileFail = currentRetryCountWhileFail;
    }

    public int getBufferedPercentage() {
        return bufferedPercentage;
    }

    public void setBufferedPercentage(int bufferedPercentage) {
        this.bufferedPercentage = bufferedPercentage;
    }

    public ExoPlayMode getExoPlayMode() {
        return exoPlayMode;
    }

    public void setExoPlayMode(ExoPlayMode exoPlayMode) {
        this.exoPlayMode = exoPlayMode;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public long getBytesInLastSecond() {
        return bytesInLastSecond;
    }

    public void setBytesInLastSecond(long bytesInLastSecond) {
        this.bytesInLastSecond = bytesInLastSecond;
    }

    public int getPlaybackState() {
        return playbackState;
    }

    public void setPlaybackState(int playbackState) {
        this.playbackState = playbackState;
    }

    public String getPlaybackStateName() {
        return playbackStateName;
    }

    public void setPlaybackStateName(String playbackStateName) {
        this.playbackStateName = playbackStateName;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    public int getScaleMode() {
        return scaleMode;
    }

    public void setScaleMode(int scaleMode) {
        this.scaleMode = scaleMode;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public int getPlayerState() {
        return playerState;
    }

    public void setPlayerState(int playerState) {
        this.playerState = playerState;
    }

    public String getPlayerStateName() {
        return playerStateName;
    }

    public void setPlayerStateName(String playerStateName) {
        this.playerStateName = playerStateName;
    }

    public int getOrientationDirection() {
        return orientationDirection;
    }

    public void setOrientationDirection(int orientationDirection) {
        this.orientationDirection = orientationDirection;
    }

    public boolean isFullScreen() {
        return isFullScreen;
    }

    public void setFullScreen(boolean fullScreen) {
        isFullScreen = fullScreen;
    }

    public int getOrientationAngle() {
        return orientationAngle;
    }

    public void setOrientationAngle(int orientationAngle) {
        this.orientationAngle = orientationAngle;
    }

    public String getAngleText() {
        return orientationAngle == -1 ? "默认角度（-1）" : orientationAngle + "°";
    }

    public String getDirectionText() {
        switch (orientationDirection) {
            case 0:
                return "竖屏 (0)";
            case 90:
                return "右横屏 (90)";
            case 270:
                return "左横屏 (270)";
            case -1:
                return "默认 (-1)";
            default:
                return "其他 (" + orientationDirection + ")";
        }
    }

    public boolean isExoRenderedFirstFramed() {
        return isExoRenderedFirstFramed;
    }

    public void setExoRenderedFirstFramed(boolean exoRenderedFirstFramed) {
        isExoRenderedFirstFramed = exoRenderedFirstFramed;
    }

    public float getPixelWidthHeightRatio() {
        return pixelWidthHeightRatio;
    }

    public void setPixelWidthHeightRatio(float pixelWidthHeightRatio) {
        this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    }

    public void setVideoInParentRect(Rect videoInParentRect) {
        this.videoInParentRect.set(videoInParentRect);
    }

    public Rect getVideoInParentRect() {
        return videoInParentRect;
    }

    public Rect getVideoInScreenRect() {
        return videoInScreenRect;
    }

    public void setVideoInScreenRect(Rect videoInScreenRect) {
        this.videoInScreenRect = videoInScreenRect;
    }
}
