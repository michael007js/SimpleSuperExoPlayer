package cn.sss.michael.exo.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoPlaybackState;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutExoComponentLoadingViewBinding;
import cn.sss.michael.exo.util.ExoFormatUtil;

/**
 * @author Michael by 61642
 * @date 2025/12/26 10:13
 * @Description 直播控制栏
 */
public class ExoComponentLoadingView extends BaseExoControlComponent<LayoutExoComponentLoadingViewBinding> {
    public ExoComponentLoadingView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentLoadingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentLoadingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_loading_view;
    }

    @Override
    protected void init(Context context) {


    }

    @Override
    public void onExoRenderedFirstFrame() {

    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {

    }


    @SuppressLint("SetTextI18n")
    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {
        binding.speed.setText("网速：" + (bytesInLastSecond == 0 ? "0 KB/s" : ExoFormatUtil.formatSpeed(bytesInLastSecond))
                + "\n" + "累计流量：" + (totalBytes == 0 ? "0 KB" : ExoFormatUtil.formatTotalSize(totalBytes)));
    }

    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPosition, int bufferedPercentage) {

    }

    @Override
    public void onPlaybackStateChanged(int playbackState, String playbackStateName) {
        super.onPlaybackStateChanged(playbackState, playbackStateName);
        if (playbackState == ExoPlaybackState.STATE_BUFFERING) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
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
