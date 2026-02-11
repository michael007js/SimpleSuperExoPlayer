package com.sss.michael.demo;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import com.sss.michael.demo.base.BaseActivity;
import com.sss.michael.demo.databinding.ActivityMusicBinding;
import com.sss.michael.exo.SimpleExoPlayerView;
import com.sss.michael.exo.callback.IExoFFTCallBack;
import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.constant.ExoPlayMode;
import com.sss.michael.exo.widget.ExoEqPanelView;

public class MusicActivity extends BaseActivity<ActivityMusicBinding> {

    String url = "https://files.freemusicarchive.org/storage-freemusicarchive-org/music/ccCommunity/Mid-Air_Machine/Everywhere_Outside__World_Music/Mid-Air_Machine_-_At_Least_It_Is.mp3";
    //    String url="https://cdn.pixabay.com/download/audio/2022/12/08/audio_90e88f8fac.mp3";
    SimpleExoPlayerView player;

    @Override
    protected int setLayout() {
        return R.layout.activity_music;
    }

    @Override
    protected void init() {
        player = new SimpleExoPlayerView(this);
        player.setExoFFTCallBack(new IExoFFTCallBack() {
            @Override
            public void onFFTReady(int sampleRateHz, int channelCount, float[] fft) {
                binding.columnView.onFFTReady(sampleRateHz, channelCount, fft);
                binding.glow.onFFTReady(sampleRateHz, channelCount, fft);
                binding.column3DView.onFFTReady(sampleRateHz, channelCount, fft);
                binding.ringWare.onFFTReady(sampleRateHz, channelCount, fft);
                binding.ringColumnWare.onFFTReady(sampleRateHz, channelCount, fft);

            }

            @Override
            public void onMagnitudeReady(int sampleRateHz, float[] magnitude) {
                binding.columnView.setVisibility(binding.rbColumnView.isChecked() ? VISIBLE : GONE);
                binding.columnView.onMagnitudeReady(sampleRateHz, magnitude);

                binding.glow.setVisibility(binding.rbGlow.isChecked() ? VISIBLE : GONE);
                binding.glow.onMagnitudeReady(sampleRateHz, magnitude);

                binding.column3DView.setVisibility(binding.rbColumnView3d.isChecked() ? VISIBLE : GONE);
                binding.column3DView.onMagnitudeReady(sampleRateHz, magnitude);

                binding.ringWare.setVisibility(binding.rbRingWare.isChecked() ? VISIBLE : GONE);
                binding.ringWare.onMagnitudeReady(sampleRateHz, magnitude);

                binding.ringColumnWare.setVisibility(binding.rbRingColumnWare .isChecked() ? VISIBLE : GONE);
                binding.ringColumnWare.onMagnitudeReady(sampleRateHz, magnitude);
            }
        });
        player.play(ExoPlayMode.MUSIC, 0, url);
        binding.eq.setOnEqChangeListener(new ExoEqPanelView.OnEqChangeListener() {
            @Override
            public void onBandGainChanged(int band, float gain) {

            }

            @Override
            public void onAllGainsBandGainChanged(float[] allGains) {
                System.arraycopy(allGains, 0, ExoEqualizerPreset.CUSTOM.getGains(), 0, allGains.length);
                ExoEqualizerPreset.CUSTOM.save(allGains);
                player.setEqualizer(ExoEqualizerPreset.CUSTOM);
            }

        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        player.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        player.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        player.release();
    }
}
