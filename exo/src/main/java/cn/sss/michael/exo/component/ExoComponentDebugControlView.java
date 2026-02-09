package cn.sss.michael.exo.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoCoreScale;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutComponentExoDebugControlViewBinding;
import cn.sss.michael.exo.util.ExoFormatUtil;

public class ExoComponentDebugControlView extends BaseExoControlComponent<LayoutComponentExoDebugControlViewBinding> {
    public ExoComponentDebugControlView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentDebugControlView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentDebugControlView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_component_exo_debug_control_view;
    }

    @Override
    protected void init(Context context) {
        setVisibility(GONE);
        binding.ivClose.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setVisibility(GONE);
            }
        });
    }


    @Override
    public void onExoRenderedFirstFrame() {

    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {
        binding.tvPlayUrl.setText(exoPlayerInfo.getUri() == null ? "地址：" : "地址：" + exoPlayerInfo.getUri().toString());

        String string =
                "缓冲进度：" + exoPlayerInfo.getBufferedPercentage() + "%" + "\n" +
                        "失败时重试次数（每次失败上限" + ExoConfig.MAX_RETRY_LIMIT_PLAY_REQUEST_WHILE_ERROR + "次）：" + exoPlayerInfo.getCurrentRetryCountWhileFail() + "\n" +
                        "分辨率：" + exoPlayerInfo.getVideoWidth() + " x " + exoPlayerInfo.getVideoHeight() + "\n" +
                        "播放状态：" + exoPlayerInfo.getPlaybackStateName() +
                        " (" + exoPlayerInfo.getPlaybackState() + ")\n" +
                        "形态：" + exoPlayerInfo.getPlayerStateName() +
                        " (" + exoPlayerInfo.getPlayerState() + ")\n" +
                        "缩放模式：" + ExoCoreScale.getScaleModeName(exoPlayerInfo.getScaleMode()) + "\n" +
                        "播放倍速：" + exoPlayerInfo.getSpeed() + "\n" +
                        "传感器角度：" + exoPlayerInfo.getAngleText() + "°\n" +
                        "视频相对父容器Rect：" + exoPlayerInfo.getVideoInParentRect() +
                        "视频相对屏幕Rect：" + exoPlayerInfo.getVideoInScreenRect();
        ;

        binding.tvStatus.setText(string);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {
        binding.tvSpeed.setText("网速：" + (bytesInLastSecond == 0 ? "0 KB/s" : ExoFormatUtil.formatSpeed(bytesInLastSecond))
                + "\n" + "累计流量：" + (totalBytes == 0 ? "0 KB" : ExoFormatUtil.formatTotalSize(totalBytes)));
    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPosition, int bufferedPercentage) {

    }

    @Override
    public void onVideoSizeChanged(View view, float pixelWidthHeightRatio, int videoWidth, int videoHeight, int scaleMode) {

    }

    @Override
    public void onExperienceTimeout() {

    }

    @Override
    public void onProgressChange(long current, long total, long seekTo) {

    }

    @Override
    public void onVolumeChange(int current, int max) {

    }

    @Override
    public void onBrightnessChange(float percent) {

    }

    @Override
    public void setPlayLocationLastTime(String tip) {

    }
}
