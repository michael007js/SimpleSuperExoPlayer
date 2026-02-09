package com.sss.michael.demo.base;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

/**
 * MVVM+UI初始化相关
 */
public abstract class BaseActivity<B extends ViewDataBinding> extends Activity {

    protected B binding;
            protected String playUrl = "https://vd2.bdstatic.com/mda-rm0gxhe39y6stei1/cae_h264/1764591246938788260/mda-rm0gxhe39y6stei1.mp4?abtest=peav_l52&appver=&auth_key=1765378132-0-0-73a947e2314f891c2b33d4687d813600&bcevod_channel=searchbox_feed&cd=0&cr=0&did=cfcd208495d565ef66e7dff9f98764da&logid=1132087574&model=&osver=&pd=1&pt=4&sl=843&sle=1&split=706856&vid=13663286492887044429&vt=1";

    @Override
    protected void onNewIntent(Intent intent) {
        if (isReLoadWithNewIntent(intent)) {
            setIntent(intent);
            init();
        }
        super.onNewIntent(intent);
    }


    public void startActivity(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        super.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (binding == null) {
            binding = DataBindingUtil.setContentView(this, setLayout());
        }
        init();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }

    /**
     * 设置布局
     */
    protected abstract int setLayout();


    protected abstract void init();


    protected boolean isReLoadWithNewIntent(Intent newIntent) {
        //TODO 此处用来控制onNewIntent中是否走应用逻辑，本方法只有在singleTask、singleInstance模式下才有效
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && clashOfClashImmersion()) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }


    /**
     * 仿部落冲突沉浸式
     *
     * @return
     */
    public boolean clashOfClashImmersion() {
        return false;
    }

}
