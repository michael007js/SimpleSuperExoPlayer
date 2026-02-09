package cn.sss.michael.exo.constant;

import android.content.pm.ActivityInfo;

/**
 * @author Michael by 61642
 * @date 2025/12/29 16:38
 * @Description 屏幕方向
 */
public class ExoScreenOrientation {
    // 横屏 屏幕朝左
    public static final int ORIENTATION_LEFT = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    // 横屏 屏幕朝右
    public static final int ORIENTATION_RIGHT = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    //竖屏 屏幕面向用户
    public static final int ORIENTATION_PORTRAIT_USER = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
}
