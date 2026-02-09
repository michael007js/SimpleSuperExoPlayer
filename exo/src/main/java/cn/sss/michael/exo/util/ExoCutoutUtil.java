package cn.sss.michael.exo.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Michael by SSS
 * @date 2025/12/25 0025 22:10
 * @Description 刘海屏工具
 */
public final class ExoCutoutUtil {

    /**
     * 是否为允许全屏界面显示内容到刘海区域的刘海屏机型（与AndroidManifest中配置对应）
     */
    public static boolean allowDisplayToCutout(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 9.0系统全屏界面默认会保留黑边，不允许显示内容到刘海区域
            Window window = activity.getWindow();
            WindowInsets windowInsets = window.getDecorView().getRootWindowInsets();
            if (windowInsets == null) {
                return false;
            }
            DisplayCutout displayCutout = windowInsets.getDisplayCutout();
            if (displayCutout == null) {
                return false;
            }
            List<Rect> boundingRects = displayCutout.getBoundingRects();
            return boundingRects.size() > 0;
        } else {
            return hasCutoutHuawei(activity)
                    || hasCutoutHonor(activity)
                    || hasCutoutOPPO(activity)
                    || hasCutoutVIVO(activity)
                    || hasCutoutXIAOMI(activity)
                    || hasCutoutOneplus(activity)
                    || hasCutoutSamsung(activity);
        }
    }

    /**
     * 是否是荣耀刘海屏（兼容老款华为逻辑及新款荣耀逻辑）
     */
    private static boolean hasCutoutHonor(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("HONOR") && !Build.BRAND.equalsIgnoreCase("HONOR")) {
            return false;
        }
        try {
            // 荣耀新款机型也支持华为的 HwNotchSizeUtil 类名
            ClassLoader cl = activity.getClassLoader();
            Class HwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            Method get = HwNotchSizeUtil.getMethod("hasNotchInScreen");
            return (boolean) get.invoke(HwNotchSizeUtil);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否是一加刘海屏
     */
    private static boolean hasCutoutOneplus(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("oneplus")) {
            return false;
        }
        try {
            // 一加主要通过系统属性判定
            ClassLoader cl = activity.getClassLoader();
            Class SystemProperties = cl.loadClass("android.os.SystemProperties");
            Method get = SystemProperties.getMethod("get", String.class);
            String feature = (String) get.invoke(SystemProperties, "ro.oneplus.notch_size");
            return feature != null && !feature.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否是三星刘海屏 (Infinity Display / Notch)
     */
    private static boolean hasCutoutSamsung(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            return false;
        }
        try {
            // 三星在 Android O 上的实现非常特殊，通常是通过反射获取是否有特殊的屏下区域
            // 但绝大多数有刘海/挖孔的三星手机都已经更新到 Android 9+
            // 这里提供一个通过资源字段判定的兜底方案
            @SuppressLint("InternalInsetResource")
            int resId = activity.getResources().getIdentifier("config_mainBuiltInDisplayCutout", "string", "android");
            if (resId > 0) {
                String spec = activity.getResources().getString(resId);
                return spec != null && !spec.isEmpty();
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    /**
     * 是否是华为刘海屏机型
     */
    @SuppressWarnings("unchecked")
    private static boolean hasCutoutHuawei(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("HUAWEI")) {
            return false;
        }
        try {
            ClassLoader cl = activity.getClassLoader();
            Class HwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            if (HwNotchSizeUtil != null) {
                Method get = HwNotchSizeUtil.getMethod("hasNotchInScreen");
                return (boolean) get.invoke(HwNotchSizeUtil);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否是oppo刘海屏机型
     */
    private static boolean hasCutoutOPPO(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("oppo")) {
            return false;
        }
        return activity.getPackageManager().hasSystemFeature("com.oppo.feature.screen.heteromorphism");
    }

    /**
     * 是否是vivo刘海屏机型
     */
    @SuppressWarnings("unchecked")
    @SuppressLint("PrivateApi")
    private static boolean hasCutoutVIVO(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("vivo")) {
            return false;
        }
        try {
            ClassLoader cl = activity.getClassLoader();
            Class ftFeatureUtil = cl.loadClass("android.util.FtFeature");
            if (ftFeatureUtil != null) {
                Method get = ftFeatureUtil.getMethod("isFeatureSupport", int.class);
                return (boolean) get.invoke(ftFeatureUtil, 0x00000020);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否是小米刘海屏机型
     */
    @SuppressWarnings("unchecked")
    @SuppressLint("PrivateApi")
    private static boolean hasCutoutXIAOMI(Activity activity) {
        if (!Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
            return false;
        }
        try {
            ClassLoader cl = activity.getClassLoader();
            Class SystemProperties = cl.loadClass("android.os.SystemProperties");
            Class[] paramTypes = new Class[2];
            paramTypes[0] = String.class;
            paramTypes[1] = int.class;
            Method getInt = SystemProperties.getMethod("getInt", paramTypes);
            //参数
            Object[] params = new Object[2];
            params[0] = "ro.miui.notch";
            params[1] = 0;
            int hasCutout = (int) getInt.invoke(SystemProperties, params);
            return hasCutout == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 适配刘海屏，针对Android P以上系统
     */
    public static void adaptCutoutAboveAndroidP(Context context, boolean isAdapt) {
        Activity activity = ExoPlayerUtils.scanForActivity(context);
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            if (isAdapt) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } else {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
            activity.getWindow().setAttributes(lp);
        }
    }

}
