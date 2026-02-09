package cn.sss.michael.exo.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoEqualizerPreset;
import cn.sss.michael.exo.core.ExoPlayerInfo;
import cn.sss.michael.exo.databinding.LayoutExoComponentEqViewBinding;
import cn.sss.michael.exo.widget.ExoEqPanelView;

/**
 * @author Michael by SSS
 * @date 2026/1/5 0005 22:13
 * @Description 均衡器组件
 */
public class ExoComponentEqView extends BaseExoControlComponent<LayoutExoComponentEqViewBinding> {
    public ExoComponentEqView(@NonNull Context context) {
        super(context);
    }

    public ExoComponentEqView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ExoComponentEqView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    protected boolean showWhileFingerTouched() {
        return false;
    }

    @Override
    protected int setLayout() {
        return R.layout.layout_exo_component_eq_view;
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
        binding.eq.setOnEqChangeListener(new ExoEqPanelView.OnEqChangeListener() {
            @Override
            public void onBandGainChanged(int band, float gain) {

            }

            @Override
            public void onAllGainsBandGainChanged(float[] allGains) {
                System.arraycopy(allGains, 0, ExoEqualizerPreset.CUSTOM.getGains(), 0, allGains.length);
                ExoEqualizerPreset.CUSTOM.save(allGains);
                exoControllerWrapper.setEqualizer(ExoEqualizerPreset.CUSTOM);
            }

        });
        binding.btnSound1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.DEFAULT.getGains(), true);
            }
        });
        binding.btnSound2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.Garage.getGains(), true);
            }
        });
        binding.btnSound3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.ConcertHall.getGains(), true);
            }
        });
        binding.btnSound4.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.BassBoost.getGains(), true);
            }
        });
        binding.btnSound5.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.Classical.getGains(), true);
            }
        });
        binding.btnSound6.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.POP.getGains(), true);
            }
        });
        binding.btnSound7.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.Elegant.getGains(), true);
            }
        });
        binding.btnSound8.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.eq.setBandGains(ExoEqualizerPreset.Voice.getGains(), true);
            }
        });
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
