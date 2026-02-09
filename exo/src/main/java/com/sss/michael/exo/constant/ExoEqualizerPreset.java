package com.sss.michael.exo.constant;

import com.sss.michael.exo.ExoConfig;

/**
 * @author Michael by 61642
 * @date 2026/1/4 10:27
 * @Description 均衡器预设模型
 */
public enum ExoEqualizerPreset {
    DEFAULT("默认", new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}),
    BassBoost("重低音", new float[]{9f, 7f, -8f, 2f, 0f, -1f, -2f, -1f, 0f, 0f}),
    Elegant("优雅午后", new float[]{2, 3, 2, 0, -1, -1, 1, 2, 3, 2}),
    ConcertHall("音乐厅", new float[]{7f, 5f, 3f, 0f, -3f, -3f, 0f, 3f, 6f, 8f}),
    POP("流行", new float[]{-1f, 2f, 5f, 2f, 0f, 1f, 3f, 5f, 2f, 1f}),
    Jazz("爵士", new float[]{2f, 4f, 6f, 3f, 1f, 2f, 3f, 2f, 2f, 1f}),
    Classical("古典", new float[]{6f, 5f, 2f, 1f, 0f, 0f, 2f, 4f, 6f, 7f}),
    Garage("车库", new float[]{4f, 7f, 5f, 2f, 0f, 2f, 6f, 8f, 4f, 2f}),
    Voice("人声", new float[]{-3f, -2f, 0f, 1.5f, 2.8f, 3.2f, 2.5f, 1.2f, 0f, -1.5f}),
    CUSTOM("自定义", new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f}),

    ;
    /**
     * 描述
     */
    private final String description;
    /**
     * 增益值
     */
    private final float[] gains;

    ExoEqualizerPreset(String description, float[] gains) {
        this.description = description;
        this.gains = gains;
    }

    public String getDescription() {
        return description;
    }

    public float[] getGains() {
        return gains;
    }


    public void save(float[] gains) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gains.length; i++) {
            sb.append(gains[i]);
            if (i < gains.length - 1) sb.append(",");
        }
        ExoConfig.SP_UTILS.put(ExoConfig.SP_EQ_GAINS, sb.toString());
    }
}
