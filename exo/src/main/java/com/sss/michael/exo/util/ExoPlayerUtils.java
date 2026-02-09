package com.sss.michael.exo.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.sss.michael.exo.constant.ExoScreenOrientation;

import java.lang.reflect.Field;

/**
 * @author Michael by SSS
 * @date 2025/12/26 0026 0:38
 * @Description 播放器相关工具类
 */

public final class ExoPlayerUtils {

    /**
     * 获取状态栏高度
     */
    public static double getStatusBarHeight(Context context) {
        int statusBarHeight = 0;
        //获取status_bar_height资源的ID
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    /**
     * 获取竖屏下状态栏高度
     */
    public static double getStatusBarHeightPortrait(Context context) {
        int statusBarHeight = 0;
        //获取status_bar_height_portrait资源的ID
        int resourceId = context.getResources().getIdentifier("status_bar_height_portrait", "dimen", "android");
        if (resourceId > 0) {
            //根据资源ID获取响应的尺寸值
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }

    /**
     * 获取NavigationBar的高度
     */
    public static int getNavigationBarHeight(Context context) {
        if (!hasNavigationBar(context)) {
            return 0;
        }
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height",
                "dimen", "android");
        //获取NavigationBar的高度
        return resources.getDimensionPixelSize(resourceId);
    }

    /**
     * 获取NavigationBar的高度
     */
    public static int getNavigationBarHeightV2(Activity activity) {
        // 获取包含导航栏的物理屏幕真实高度
        DisplayMetrics dm = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        }
        int realHeight = dm.heightPixels;

        // 获取当前窗口的可视区域（不包含导航栏）
        Rect rect = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        int visibleBottom = rect.bottom;

        // 差值即为导航栏（小白条）高度
        // 注意：如果是横屏，导航栏可能在右侧，此时需要计算 width 的差值
        int heightDiff = realHeight - visibleBottom;

        return Math.max(heightDiff, 0);
    }

    /**
     * 是否存在NavigationBar
     */
    public static boolean hasNavigationBar(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Display display = getWindowManager(context).getDefaultDisplay();
            Point size = new Point();
            Point realSize = new Point();
            display.getSize(size);
            display.getRealSize(realSize);
            return realSize.x != size.x || realSize.y != size.y;
        } else {
            boolean menu = ViewConfiguration.get(context).hasPermanentMenuKey();
            boolean back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
            return !(menu || back);
        }
    }

    /**
     * 获取屏幕宽度
     */
    public static int getScreenWidth(Context context, boolean isIncludeNav) {
        if (isIncludeNav) {
            return context.getResources().getDisplayMetrics().widthPixels + getNavigationBarHeight(context);
        } else {
            return context.getResources().getDisplayMetrics().widthPixels;
        }
    }

    /**
     * 获取屏幕高度
     */
    public static int getScreenHeight(Context context, boolean isIncludeNav) {
        if (isIncludeNav) {
            return context.getResources().getDisplayMetrics().heightPixels + getNavigationBarHeight(context);
        } else {
            return context.getResources().getDisplayMetrics().heightPixels;
        }
    }

    /**
     * 获取DecorView
     */
    public static ViewGroup getDecorView(Context context) {
        Activity activity = ExoPlayerUtils.scanForActivity(context);
        if (activity == null) return null;
        return (ViewGroup) activity.getWindow().getDecorView();
    }

    /**
     * 获取activity中的content view,其id为android.R.id.content
     */
    public static ViewGroup getContentView(Context context) {
        Activity activity = ExoPlayerUtils.scanForActivity(context);
        if (activity == null) return null;
        return activity.findViewById(android.R.id.content);
    }

    /**
     * 获取Activity
     */
    public static Activity scanForActivity(Context context) {
        if (context == null) return null;
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) context).getBaseContext());
        }
        return null;
    }

    /**
     * 如果WindowManager还未创建，则创建一个新的WindowManager返回。否则返回当前已创建的WindowManager。
     */
    public static WindowManager getWindowManager(Context context) {
        return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * 边缘检测
     */
    public static boolean isEdge(Context context, MotionEvent e) {
        int edgeSize = ExoDensityUtil.dp2px(context, 40);
        return e.getRawX() < edgeSize
                || e.getRawX() > getScreenWidth(context, true) - edgeSize
                || e.getRawY() < edgeSize
                || e.getRawY() > getScreenHeight(context, true) - edgeSize;
    }


    /**
     * 通过反射获取Application
     *
     * @deprecated 不在使用，后期谷歌可能封掉改接口
     */
    @SuppressLint("PrivateApi")
    @Deprecated
    public static Application getApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static void toggleImmersiveMode(Activity activity, boolean isFullScreen) {
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            activity.getWindow().setDecorFitsSystemWindows(!isFullScreen);
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                if (isFullScreen) {
                    // 隐藏状态栏和导航栏
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    // 这里的 Behavior 决定了滑动显示后是否自动隐藏
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            View decorView = activity.getWindow().getDecorView();
            int uiOptions = decorView.getSystemUiVisibility();
            if (isFullScreen) {
                uiOptions |= (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                uiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                uiOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static boolean isBehindLiveWindow(Throwable e) {
        if (e == null) {
            return false;
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof androidx.media3.exoplayer.source.BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public static String getStackTrace() {
        StringBuffer err = new StringBuffer();
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            err.append("\tat ");
            err.append(stack[i].toString());
            err.append("\n");
        }
        return err.toString();
    }

    public static String getStackTrace(Throwable throwable) {
        StringBuffer err = new StringBuffer();
        err.append(throwable.getLocalizedMessage());
        StackTraceElement[] stack = throwable.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            err.append("\tat ");
            err.append(stack[i].toString());
            err.append("\n");
        }
        return err.toString();
    }

    public static void setScreenOrientation(Activity activity, int orientation) {
        if (
                orientation == ExoScreenOrientation.ORIENTATION_LEFT // 横屏 屏幕朝左
                        || orientation == ExoScreenOrientation.ORIENTATION_RIGHT // 横屏 屏幕朝右
                        || orientation == ExoScreenOrientation.ORIENTATION_PORTRAIT_USER //竖屏 屏幕面向用户

        ) {

            activity.setRequestedOrientation(orientation);
        }
    }

    public static void fitNavigationBars(Activity activity, boolean hide) {
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (hide) {
                    controller.hide(WindowInsets.Type.navigationBars());
                } else {
                    controller.show(WindowInsets.Type.navigationBars());
                }

            }
        }
    }

    /**
     * 获取当前导航栏的背景色
     *
     * @param activity 页面
     * @return 导航栏颜色的十六进制值（如0xFF000000=黑色），API 21以下返回-1
     */
    public static int getNavigationBarColor(Activity activity) {
        if (activity == null) {
            return -1;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return -1;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 直接获取系统导航栏颜色（虚拟导航栏场景）
            return window.getNavigationBarColor();
        }
        return -1; // 低于Android 5.0无导航栏颜色API，返回-1
    }

    /**
     * 设置系统导航栏背景色
     *
     * @param activity 页面
     * @param color    十六进制颜色值（如0xFFFF0000=红色，0xFF00FF00=绿色）
     */
    public static void setNavigationBarColor(Activity activity, int color) {
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(color);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = window.getDecorView().getSystemUiVisibility();
                if (isLightColor(color)) {
                    // 颜色偏浅时，文字设为深色
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    // 颜色偏深时，文字设为浅色
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }

        // 适配Android 12+（API 31）的导航栏样式（国产手机新系统适配）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 强制导航栏使用自定义颜色，屏蔽系统默认适配
            try {
                WindowManager.LayoutParams lp = window.getAttributes();
                Field field = WindowManager.LayoutParams.class
                        .getDeclaredField("navigationBarContrastEnforced");
                field.setAccessible(true);
                field.setBoolean(lp, false);
                window.setAttributes(lp);
            } catch (Throwable ignored) {
                // 字段不存在 / ROM 不支持，直接忽略
            }
        }
    }

    /**
     * 判断颜色是否为浅色（用于适配导航栏文字颜色）
     *
     * @param color 十六进制颜色值（带Alpha通道）
     * @return true=浅色，false=深色
     */
    public static boolean isLightColor(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        // 计算亮度（标准公式）
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        return luminance > 0.5;
    }
}
