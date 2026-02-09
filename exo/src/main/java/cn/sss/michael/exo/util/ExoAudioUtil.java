package cn.sss.michael.exo.util;

import android.content.Context;
import android.media.AudioManager;

/**
 * @author Michael by SSS
 * @date 2025/12/26 0026 23:47
 * @Description 音频工具
 */
public class ExoAudioUtil {

    public static void setStreamVolume(Context context, int streamType, int vol) {
        getAudioManager(context).setStreamVolume(streamType, vol, 0);
    }

    public static int getStreamMaxVolume(Context context, int streamType) {
        return getAudioManager(context).getStreamMaxVolume(streamType);
    }

    public static float getStreamVolume(Context context, int streamType) {
        return getAudioManager(context).getStreamVolume(streamType);

    }

    public static AudioManager getAudioManager(Context context) {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    }


}
