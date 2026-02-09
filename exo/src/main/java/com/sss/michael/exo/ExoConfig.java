package com.sss.michael.exo;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import com.sss.michael.exo.constant.ExoEqualizerPreset;
import com.sss.michael.exo.util.ExoSPUtils;

/**
 * @author Michael by 61642
 * @date 2026/1/4 15:29
 * @Description 配置类，偏向于底层的功能
 */
public class ExoConfig {
    /********************************************* 基础配置 *********************************************/
    // 启用日志
    public static boolean LOG_ENABLE = true;
    // 播放错误时最大重试次数
    public static final int MAX_RETRY_LIMIT_PLAY_REQUEST_WHILE_ERROR = 2;
    // 允许内容延伸至挖孔区域（刘海屏）
    public static final boolean ALLOW_DISPLAY_TO_CUTOUT = false;

    /********************************************* 组件配置 *********************************************/
    // 启用debug组件
    public static boolean COMPONENT_DEBUG_ENABLE = true;
    // 启用频谱组件
    public static boolean COMPONENT_SPECTRUM_ENABLE = true;
    // 启用EQ组件
    public static boolean COMPONENT_EQ_ENABLE = true;

    /********************************************* 监控配置 *********************************************/
    // 监控执行间隔
    public static final long MONITOR_INTERVAL_MS = 1000;
    // 任务超时保护（避免阻塞）
    public static final long MONITOR_TASK_TIMEOUT_MS = 800;
    // 线程前缀
    public static final String MONITOR_THREAD_NAME_PREFIX = "ExoPageMonitor-";
    // 主线程任务消息标识
    public static final int MONITOR_MSG_MAIN_THREAD_TASK = 1001;

    /********************************************* 滤波器配置 *********************************************/
    // 系数平滑因子（sample级插值，默认0.005，兼顾过渡速度和平滑度）
    public static final float FILTER_COEFF_SMOOTH_FACTOR = 0.005f;
    // 系数收敛阈值（小于该值视为系数一致，停止插值）
    public static final double FILTER_COEFF_CONVERGE_THRESHOLD = 1e-6;
    // 旁路判断阈值（判断是否为直通状态）
    public static final double FILTER_BYPASS_EPS = 1e-5;

    /********************************************* 均衡器配置 *********************************************/
    // 品质因数
    // 值越高：带宽越窄。滤波器只影响非常精准的频率点。常用于消除特定频率的噪音。
    // 值越低：带宽越宽。滤波器影响的范围很大。常用于调节整体音色感，听感更自然。
    // 0.707：     2.0 八度极其宽泛、顺滑调节整体冷暖色调（如增加整体低音）
    // 1.0：       1.4 八度温暖、自然通用音乐调节
    // 1.414：     1.0 八度标准、平衡10 段固定频率
    // 2.0：       0.7 八度精准、稍尖锐突出特定乐器（如想单独拉高人声频率）
    // 4.0 - 10.0：极窄尖锐、突兀修复缺陷（如消除电流麦克风的啸叫声）
    public static final float EQ_QUALITY_FACTOR = 1.725f;
    // 
    // 中心频率
    // 索引0:31.25Hz（超低频）、索引1:62.5Hz（低频）、索引2:125Hz（低频）
    // 索引3:250Hz（中低频）、索引4:500Hz（中低频）、索引5:1000Hz（中频）
    // 索引6:2000Hz（中高频）、索引7:4000Hz（高频）、索引8:8000Hz（高频）
    // 索引9:16000Hz（超高频）
    public static final float[] EQ_CENTER_FREQUENCIES = {
            31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    };
    // 中心频率说明
    public static final String[] EQ_FREQ_COMMENTS = {
            "超低频", "低频", "低频", "中低频", "中低频", "中频", "中高频", "高频", "高频", "超高频"
    };

    // 波段总数
    public static final int EQ_BAND_COUNT = 10;
    // 最小分贝
    public static final float EQ_MIN_DB = -15f;
    // 最大分贝
    public static final float EQ_MAX_DB = 15f;
    // 全局增益平滑系数：控制全局增益过渡速度，值越小过渡越平滑
    public static final float EQ_GLOBAL_GAIN_SMOOTH = 0.1f;
    // 削波阈值（线性范围）：默认1.0f，范围(0,1.0]，超出该值的样本会被削波处理
    public static float EQ_CLIPPING_THRESHOLD = 0.85f;
    // 是否启用软削波：false=硬削波（直接截断，效率高）；true=软削波（S型曲线，失真小）
    public static boolean EQ_USE_SOFT_CLIPPING = false;
    // 增益跳过阈值（dB）：增益绝对值小于该值时，判定为无效增益，EQ标记为未激活
    public static float EQ_GAIN_SKIP_THRESHOLD = 0.1f;

    /********************************************* 频谱配置 *********************************************/
    // 默认FFT样本大小（2的幂）
    public static int FFT_SAMPLE_SIZE = 256;
    // 默认最大帧率（60帧/秒，避免过度消耗性能）
    public static int FFT_MAX_FPS = 20;
    // 性能消耗打印间隔
    public static long FFT_STAT_PRINT_INTERVAL_MS = 3000;
    // 是否计算频谱幅度（默认开启，便于UI绘制频谱）
    public static boolean FFT_CALCULATE_MAGNITUDE = true;
    //振幅放大系数 用于整体放大 / 压缩 FFT 幅度视觉效果
    public static float FFT_AMPLITUDE_BOOST = 0.2f;
    // 对数压缩因子
    // 取值范围：0~1
    // 用于对 FFT 幅度进行 log 压缩：v = v / (v + SPECTRUM_LOG_K)
    // 作用：
    // - 控制低幅度信号和高幅度信号的动态范围
    // - 值越小，压缩越少，低幅度信号更明显
    // - 值越大，压缩越强，低幅度信号更弱，高幅度信号相对突出
    public static float SPECTRUM_LOG_K = 0.28f;
    // 低频增强截止位置（频率归一化，0~1）
    // 作用：0~0.15 的频率段算低频
    public static float SPECTRUM_LOW_FREQ_END = 0.15f;
    // 低频基础增益
    // 作用：整体抬高低频的能量
    // 值越大，低频整体越“扎实”
    public static float SPECTRUM_LOW_BASE = 1.4f;
    // 低频增强强度
    // 作用：低频峰值放大系数，增强鼓点、贝斯冲击力
    // 值越大，低频峰值更夸张
    public static float SPECTRUM_LOW_STRENGTH = 1.1f;
    // 高频增强起始位置（频率归一化，0~1）
    // 0.001f → 高频增强几乎全频段开始
    public static float SPECTRUM_HIGH_FREQ_START = 0.001f;
    // 高频增强强度
    // 作用：补偿高频自然衰减
    // 值越大，高频越亮、清晰
    public static float SPECTRUM_HIGH_STRENGTH = 0.01f;
    // 中频增强开始位置（归一化 0~1）
    // 作用：保证人声和乐器中频不被压制
    public static float SPECTRUM_MID_START = 0.30f;
    // 中频增强结束位置（归一化 0~1）
    public static float SPECTRUM_MID_END = 0.50f;
    // 中频基础增益
    // 作用：整体抬高中频能量
    public static float SPECTRUM_MID_BASE = 1.0f;
    // 中频增强强度
    // 作用：在中频范围内做微小峰值增强 主要靠高频拉升整体感知
    public static float SPECTRUM_MID_STRENGTH = 0.10f;
    // 作用：控制频率归一化值的指数衰减
    // 值 < 1 → 高频相对增强，值 > 1 → 高频衰减更快
    // 用于调整整体频谱的能量分布感
    public static float SPECTRUM_SPREAD_EXP = 1.25f;
    // 当前 bin 权重（中心 bin）
    // 值越大，保留当前 bin 信号越强
    public static float SPECTRUM_SMOOTH_CENTER = 0.5f;
    // 邻近 bin 权重（左右 bin）
    // 用于平滑频谱，减少跳动
    // 值越大，频谱曲线越平滑，但分辨率略下降
    public static float SPECTRUM_SMOOTH_SIDE = 0.75f;

    /********************************************* 缓存配置 *********************************************/
    // 默认缓存大小：500MB
    public static final long CACHE_DEFAULT_CACHE_SIZE = 500 * 1024 * 1024;
    // 默认预加载大小：2MB
    public static final long CACHE_DEFAULT_PRELOAD_SIZE = 2 * 1024 * 1024;
    // 默认最大并行预加载任务数：3
    public static final int CACHE_DEFAULT_MAX_PRELOAD_TASK = 3;
    // 默认核心线程数：2
    public static final int CACHE_DEFAULT_CORE_THREAD_COUNT = 2;
    // 默认最大线程数：4
    public static final int CACHE_DEFAULT_MAX_THREAD_COUNT = 4;
    // 默认缓存过期时间：7天（单位：毫秒）
    public static final long CACHE_DEFAULT_CACHE_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000;
    // 默认任务超时时间：30秒（单位：毫秒）
    public static final long CACHE_DEFAULT_TASK_TIMEOUT = 30 * 1000;
    // 磁盘缓存淘汰策略管理器(ExpirableLruCacheEvictor)最大条目数
    public static final int CACHE_DEFAULT_MAX_METADATA_ENTRY_COUNT = 1000;


    /********************************************* 手势置 *********************************************/
    // 手势长按判定阈值（毫秒）
    public static final int GESTURE_LONG_PRESS_TIMEOUT = 500;
    // 默认缩放
    public static final float GESTURE_DEFAULT_SCALE = 1.0f;
    // 最大缩放
    public static final float GESTURE_MAX_SCALE = 1.0f;
    // 手指触摸期间的最大缩放 手指放开后会回到 GESTURE_MAX_SCALE 程度
    public static final float GESTURE_MAX_SCALE_WHILE_FINGER_TOUCHED = 1.2f;
    // 最小缩放
    public static final float GESTURE_MIN_SCALE = 1.0f;
    // 手指触摸期间的最小缩放 手指放开后会回到 GESTURE_MIN_SCALE 程度
    public static final float GESTURE_MIN_SCALE_WHILE_FINGER_TOUCHED = 0.9f;
    // 如果播放器的父类是滚动视图，边缘下拉时离开边缘区域时立刻恢复父类的滚动
    public static final boolean GESTURE_RESUME_SCROLL_LAYOUT_SCROLLING_WHEN_LEAVING_THE_EDGE_AREA_DURING_EDGE_PULL_DOWN = false;

    public static final float GESTURE_EDGE_LOCK_PERCENT = 0.2f;       // 边缘比例
    public static final int GESTURE_EDGE_LOCK_PIXEL = -1;              // 固定像素（>0 时优先）


    // SharedPreferences操作类
    public static final ExoSPUtils SP_UTILS = new ExoSPUtils();

    /********************************************* 初始化配置 *********************************************/
    // 均衡器SpKey
    public static final String SP_EQ_GAINS = "exo_current_eq_gains";

    /**
     * 初始化
     */
    public static void init(Application application, String spName, boolean debug) {
        LOG_ENABLE = debug;
        COMPONENT_DEBUG_ENABLE = debug;
        COMPONENT_SPECTRUM_ENABLE = debug;
        COMPONENT_EQ_ENABLE = debug;
        SP_UTILS.init(application, spName, Context.MODE_APPEND);
        String savedGains = ExoConfig.SP_UTILS.getString(ExoConfig.SP_EQ_GAINS, "");
        if (!TextUtils.isEmpty(savedGains)) {
            try {
                String[] parts = savedGains.split(",");
                float[] gains = new float[ExoConfig.EQ_BAND_COUNT];
                for (int i = 0; i < parts.length && i < gains.length; i++) {
                    gains[i] = Float.parseFloat(parts[i]);
                }
                System.arraycopy(gains, 0, ExoEqualizerPreset.CUSTOM.getGains(), 0, gains.length);

            } catch (Exception e) {
                e.printStackTrace();
                System.arraycopy(ExoEqualizerPreset.DEFAULT.getGains(), 0, ExoEqualizerPreset.CUSTOM.getGains(), 0, ExoEqualizerPreset.DEFAULT.getGains().length);
            }
        }

    }

}
