package cn.sss.michael.exo.component;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoPlayerMode;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutComponentExoTitleViewBinding;
import cn.sss.michael.exo.util.ExoFormatUtil;
import cn.sss.michael.exo.util.ExoNetworkUtil;
import cn.sss.michael.exo.util.ExoPlayerUtils;

/**
 * @author Michael by SSS
 * @date 2025/12/27 0027 15:47
 * @Description 播放器标题栏(横屏模式下有效) 在顶部显示系统电量、时间、网络  横屏下模拟系统状态栏
 */
public class ExoComponentTitleBarView extends BaseExoControlComponent<LayoutComponentExoTitleViewBinding> {

    private BroadcastReceiver batteryReceiver;

    public ExoComponentTitleBarView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentTitleBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentTitleBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected boolean showWhileFingerTouched() {
        return true;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_component_exo_title_view;
    }

    @Override
    protected void init(Context context) {
        binding.back.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Activity activity = ExoPlayerUtils.scanForActivity(getContext());
                if (activity != null && exoControllerWrapper.isFullScreen()) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    exoControllerWrapper.stopFullScreen(true);
                }
            }
        });
    }

    /**
     * 上一次的context
     */
    private Context lastAttachedContext;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (batteryReceiver == null) {
            batteryReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle extras = intent.getExtras();
                    if (extras == null) return;
                    int current = extras.getInt("level");// 获得当前电量
                    int total = extras.getInt("scale");// 获得总电量
                    int percent = current * 100 / total;
                    binding.ivBattery.getDrawable().setLevel(percent);
                }
            };
            lastAttachedContext = getContext();
            lastAttachedContext.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        }
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (batteryReceiver != null && lastAttachedContext != null) {
            lastAttachedContext.unregisterReceiver(batteryReceiver);
            batteryReceiver = null;
        }
    }

    @Override
    public void onExoRenderedFirstFrame() {

    }

    @Override
    public void onPlayerInfoChanged(ExoPlayerInfo exoPlayerInfo) {
        binding.sysTime.setText(ExoFormatUtil.getCurrentSystemTime());
    }

    @Override
    public void onNetworkBytesChanged(long bytesInLastSecond, long totalBytes) {

    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onPlayingProgressPositionChanged(long currentMs, long durationMs, long bufferedPosition, int bufferedPercentage) {
        Context context = getContext();
        if (context != null) {
            int networkType = ExoNetworkUtil.getNetworkType(context);
            switch (networkType) {
                case ExoNetworkUtil.NETWORK_MOBILE_3G:
                    binding.network.setText("3G");
                    break;
                case ExoNetworkUtil.NETWORK_MOBILE_4G:
                    binding.network.setText("4G");
                    break;
                case ExoNetworkUtil.NETWORK_MOBILE_5G:
                    binding.network.setText("5G");
                    break;
                case ExoNetworkUtil.NETWORK_WIFI:
                    int networkSign = ExoNetworkUtil.getWifiDbm(context);
                    if (networkSign == Integer.MIN_VALUE) {
                        binding.network.setText("Wifi");
                    } else {
                        binding.network.setText("Wifi" + networkSign + "dBm");
                    }

                    break;
                default:
                    binding.network.setText("");
            }

        }
    }

    @Override
    protected void fadeIn(boolean callOut) {
        if (exoControllerWrapper.isFullScreen()) {
            super.fadeIn(callOut);
        }
    }

    @Override
    public void onPlayerStateChanged(int playerState, String playerStateName, View playerView) {
        super.onPlayerStateChanged(playerState, playerStateName, playerView);
        if (ExoPlayerMode.PLAYER_NORMAL == playerState) {
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
