package cn.sss.michael.exo.cache;

/**
 * @author Michael by 61642
 * @date 2025/12/30 18:00
 * @Description 预加载任务状态回调接口
 */
public interface ExoPreloadCallback {
    /**
     * 预加载成功
     *
     * @param url 视频URL
     */
    void onPreloadSuccess(String url);

    /**
     * 预加载失败
     *
     * @param url      视频URL
     * @param errorMsg 失败信息
     */
    void onPreloadFailed(String url, String errorMsg);

    /**
     * 预加载进度更新
     *
     * @param url         视频URL
     * @param loadedBytes 已加载字节数
     * @param totalBytes  总需加载字节数（预加载大小）
     */
    void onPreloadProgress(String url, long loadedBytes, long totalBytes);

    /**
     * 预加载被取消
     *
     * @param url 视频URL
     */
    void onPreloadCanceled(String url);
}