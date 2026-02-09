package com.sss.michael.exo.constant;

/**
 * @author Michael by SSS
 * @date 2025/12/24 0024 20:22
 * @Description 缩放模式定义
 */
public class ExoCoreScale {
    /**
     * 自动智能缩放
     */
    public static final int SCALE_AUTO = 5;
    /**
     * 等比适配（完整显示）
     */
    public static final int SCALE_FIT = 0;
    /**
     * 等比裁剪填充
     */
    public static final int SCALE_FILL_CUT = 1;
    /**
     * 拉伸铺满
     */
    public static final int SCALE_STRETCH = 2;
    /**
     * 固定16:9
     */
    public static final int SCALE_16_9 = 3;
    /**
     * 固定16:9
     */
    public static final int SCALE_21_9 = 4;

    public static String getScaleModeName(int mode) {
        switch (mode) {
            case ExoCoreScale.SCALE_FIT:
                return "自适应";
            case ExoCoreScale.SCALE_FILL_CUT:
                return "裁剪铺满";
            case ExoCoreScale.SCALE_STRETCH:
                return "拉伸";
            case ExoCoreScale.SCALE_16_9:
                return "16:9";
            case ExoCoreScale.SCALE_21_9:
                return "21:9";
            case ExoCoreScale.SCALE_AUTO:
                return "自动";
            default:
                return "未知";
        }
    }
}
