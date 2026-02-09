package cn.sss.michael.exo.helper;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * @author Michael by SSS
 * @date 2025/12/27 0027 17:20
 * @Description 震动工具
 */
public class ExoVibratorHelper {

    // 定义震动类型
    public enum VibrateType {
        TICKS(8, 30),      // 刻度感：短促、极轻微
        ANCHOR(12, 55),    // 锚点感（0dB）：清脆、有落位感
        BOUNDARY(15, 75);  // 边界感（撞击）：稍强、有阻挡感

        final long duration;
        final int amplitude;

        VibrateType(long duration, int amplitude) {
            this.duration = duration;
            this.amplitude = amplitude;
        }
    }

    private static float mLastVibrateGain = -100f;
    private static long mLastVibrateTime = 0;

    /**
     * 基础震动方法
     */
    public static void vibrator(Context context) {
        vibrator(context, 50, 100);
    }

    /**
     * 基础震动方法
     */
    public static void vibrator(Context context, long milliseconds, int amplitude) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 限制振幅范围 1-255
            int safeAmplitude = Math.max(1, Math.min(amplitude, 255));
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, safeAmplitude));
        } else {
            vibrator.vibrate(Math.max(10, milliseconds)); // 旧设备最小震动10ms
        }
    }

    /**
     * 模仿澎湃OS的均衡器专场震动逻辑
     */
    public static void processEqVibrate(Context context, float currentGain, float minDb, float maxDb) {
        long currentTime = System.currentTimeMillis();
        float deltaGain = Math.abs(currentGain - mLastVibrateGain);

        // 边界反馈 (撞墙)
        if ((currentGain >= maxDb - 0.1f || currentGain <= minDb + 0.1f)) {
            if (deltaGain > 0.5f) { // 避免在边界细微抖动时重复震动
                trigger(context, VibrateType.BOUNDARY);
                mLastVibrateGain = currentGain;
                mLastVibrateTime = currentTime;
            }
            return;
        }

        // 零位锚点反馈 (过0点)
        if ((mLastVibrateGain > 0 && currentGain <= 0) || (mLastVibrateGain < 0 && currentGain >= 0)) {
            if (currentTime - mLastVibrateTime > 50) {
                trigger(context, VibrateType.ANCHOR);
                mLastVibrateGain = currentGain;
                mLastVibrateTime = currentTime;
            }
            return;
        }

        // 常规步进反馈 (刻度感)
        // 门槛设为 0.8dB，最小间隔 45ms 保证颗粒感不模糊
        if (deltaGain >= 0.8f && (currentTime - mLastVibrateTime > 45)) {
            trigger(context, VibrateType.TICKS);
            mLastVibrateGain = currentGain;
            mLastVibrateTime = currentTime;
        }
    }

    private static void trigger(Context context, VibrateType type) {
        vibrator(context, type.duration, type.amplitude);
    }

    /**
     * 触摸结束时重置状态
     */
    public static void reset() {
        mLastVibrateGain = -100f;
        mLastVibrateTime = 0;
    }
}