package com.sss.michael.demo;

import android.view.View;
import android.widget.SeekBar;

import com.sss.michael.demo.base.BaseActivity;
import com.sss.michael.demo.databinding.ActivityExoDemoBinding;
import com.sss.michael.exo.constant.ExoCoreScale;
import com.sss.michael.exo.constant.ExoPlayMode;

public class ExoPlayerDemoActivity extends BaseActivity<ActivityExoDemoBinding> {
    @Override
    protected int setLayout() {
        return R.layout.activity_exo_demo;
    }

    @Override
    protected void init() {
        binding.seekbarPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.simpleVlcPlayer.setPlayerScale(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        binding.seekbarRoot.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                binding.simpleVlcPlayer.setPlayerScale(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        binding.simpleVlcPlayer.play(ExoPlayMode.VOD, 0, playUrl);
        binding.simpleVlcPlayer.useDefaultComponents();
        binding.simpleVlcPlayer.setPlayLocationLastTime("上次播放");
        binding.btn169.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.simpleVlcPlayer.setScaleMode(ExoCoreScale.SCALE_16_9);
            }
        });
        binding.btn219.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.simpleVlcPlayer.setScaleMode(ExoCoreScale.SCALE_21_9);
            }
        });
        binding.btnBestSize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.simpleVlcPlayer.setScaleMode(ExoCoreScale.SCALE_AUTO);
            }
        });

        binding.btnFitScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.simpleVlcPlayer.setScaleMode(ExoCoreScale.SCALE_STRETCH);
            }
        });
        binding.btnFitCut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.simpleVlcPlayer.setScaleMode(ExoCoreScale.SCALE_FILL_CUT);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (!binding.simpleVlcPlayer.isFullScreen()) {
            super.onBackPressed();
        } else {
            binding.simpleVlcPlayer.stopFullScreen(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.simpleVlcPlayer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.simpleVlcPlayer.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.simpleVlcPlayer.release();
    }
}
