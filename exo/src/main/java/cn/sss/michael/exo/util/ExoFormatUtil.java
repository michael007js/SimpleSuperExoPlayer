package cn.sss.michael.exo.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author Michael by SSS
 * @date 2025/12/27 0027 18:43
 * @Description 格式化工具
 */
public class ExoFormatUtil {
    /**
     * 格式化当前系统时间
     */
    public static String getCurrentSystemTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date date = new Date();
        return simpleDateFormat.format(date);
    }

    /**
     * 格式化时间
     */
    public static String stringForTime(long timeMs) {
        long totalSeconds = timeMs / 1000;

        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    /**
     * 格式化网速
     */
    public static String formatSpeed(long bitratebps) {
        bitratebps = bitratebps * 8;//字节转为位(bit)
        if (bitratebps <= 0) return "0 KB/s";
        double kbPerSecond = bitratebps / 8.0 / 1024.0;
        if (kbPerSecond < 1024) return String.format("%.1f KB/s", kbPerSecond);
        double mbPerSecond = kbPerSecond / 1024.0;
        return String.format("%.2f MB/s", mbPerSecond);
    }

    /**
     * 格式化总流量
     */
    public static String formatTotalSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);
        double mb = kb / 1024.0;
        return String.format("%.2f MB", mb);
    }


}
