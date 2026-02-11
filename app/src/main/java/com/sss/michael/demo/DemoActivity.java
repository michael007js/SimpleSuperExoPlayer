package com.sss.michael.demo;

import android.view.View;

import com.sss.michael.demo.base.BaseActivity;
import com.sss.michael.demo.databinding.ActivityDemoBinding;

public class DemoActivity extends BaseActivity<ActivityDemoBinding> {


    @Override
    protected int setLayout() {
        return R.layout.activity_demo;
    }

    @Override
    protected void init() {
        binding.btnMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(MusicActivity.class);
            }
        });
        binding.btnVod.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(VideoActivity.class);
            }
        });

    }

}
