package cn.sss.michael.exo.helper;

import android.content.Context;
import android.view.OrientationEventListener;
/**
 * @author Michael by SSS
 * @date 2025/12/25 0025 21:43
 * @Description 设备方向监听
 */
public class ExoOrientationHelper extends OrientationEventListener {

    private long mLastTime;

    private OnOrientationChangeListener mOnOrientationChangeListener;

    public ExoOrientationHelper(Context context) {
        super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastTime < 300) return;//300毫秒检测一次
        if (mOnOrientationChangeListener != null) {
            mOnOrientationChangeListener.onOrientationChanged(orientation);
        }
        mLastTime = currentTime;
    }


    public interface OnOrientationChangeListener {
        void onOrientationChanged(int orientation);
    }

    public void setOnOrientationChangeListener(OnOrientationChangeListener onOrientationChangeListener) {
        mOnOrientationChangeListener = onOrientationChangeListener;
    }
}
