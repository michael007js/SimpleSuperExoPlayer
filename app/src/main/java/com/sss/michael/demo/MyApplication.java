package com.sss.michael.demo;

import android.app.Application;

import com.sss.michael.exo.ExoConfig;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ExoConfig.init(this, "exo_config", true);
    }
}
