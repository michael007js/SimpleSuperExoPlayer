package com.sss.michael.exo.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

public class ExoNetworkUtil {
    public static final int NO_NETWORK = 0;
    public static final int NETWORK_CLOSED = 1;
    public static final int NETWORK_ETHERNET = 2;
    public static final int NETWORK_MOBILE_3G = 3;
    public static final int NETWORK_MOBILE_4G = 4;
    public static final int NETWORK_MOBILE_5G = 5;
    public static final int NETWORK_WIFI = 6;
    public static final int NETWORK_OTHER = 7;
    public static final int NETWORK_UNKNOWN = -1;

    /**
     * 判断当前网络类型
     */
    public static int getNetworkType(Context context) {
        //改为context.getApplicationContext()，防止在Android 6.0上发生内存泄漏
        ConnectivityManager connectMgr = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectMgr == null) {
            return NO_NETWORK;
        }

        NetworkInfo networkInfo = connectMgr.getActiveNetworkInfo();
        if (networkInfo == null) {
            // 没有任何网络
            return NO_NETWORK;
        }
        if (!networkInfo.isConnected()) {
            // 网络断开或关闭
            return NETWORK_CLOSED;
        }
        if (networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
            // 以太网网络
            return NETWORK_ETHERNET;
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            // wifi网络，当激活时，默认情况下，所有的数据流量将使用此连接
            return NETWORK_WIFI;
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            // 移动数据连接,不能与连接共存,如果wifi打开，则自动关闭
            switch (networkInfo.getSubtype()) {
                // 2G
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return NETWORK_OTHER;
                // 3G
                case TelephonyManager.NETWORK_TYPE_UMTS:
                    return NETWORK_MOBILE_3G;
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return NETWORK_OTHER;
                // 4G
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return NETWORK_MOBILE_4G;
                // 5G
                case TelephonyManager.NETWORK_TYPE_NR:
                    return NETWORK_MOBILE_5G;
            }
        }
        // 未知网络
        return NETWORK_UNKNOWN;
    }


    public static int getRealWifiLevel(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return 0;

        WifiInfo info = wifiManager.getConnectionInfo();
        int rssi = (info != null) ? info.getRssi() : -100;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return wifiManager.calculateSignalLevel(rssi);
        } else {
            return WifiManager.calculateSignalLevel(rssi, 5);
        }
    }

    public static int getWifiDbm(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return Integer.MIN_VALUE;

        WifiInfo info = wifiManager.getConnectionInfo();
        if (info != null) {
            return info.getRssi();
        }
        return Integer.MIN_VALUE;
    }

    /**
     * 判断是否WiFi连接
     */
    public static boolean isWifiConnected(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            // 兼容低版本（可根据需求实现）
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
        }
    }

    /**
     * 判断网络质量是否较差
     */
    public static boolean isNetworkPoor(Context context) {
        if (context == null) {
            return false;
        }
        return !isWifiConnected(context);
    }
}
