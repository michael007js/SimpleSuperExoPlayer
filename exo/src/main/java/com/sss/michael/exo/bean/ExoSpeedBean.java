package com.sss.michael.exo.bean;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author Michael by 61642
 * @date 2026/1/5 16:56
 * @Description 播放速度模型
 */
public class ExoSpeedBean implements Parcelable {
    public float speed;
    public String title;
    public boolean checked;

    public ExoSpeedBean(float speed, String title, boolean checked) {
        this.speed = speed;
        this.title = title;
        this.checked = checked;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(this.speed);
        dest.writeString(this.title);
        dest.writeByte(this.checked ? (byte) 1 : (byte) 0);
    }

    protected ExoSpeedBean(Parcel in) {
        this.speed = in.readFloat();
        this.title = in.readString();
        this.checked = in.readByte() != 0;
    }

    public static final Creator<ExoSpeedBean> CREATOR = new Creator<ExoSpeedBean>() {
        @Override
        public ExoSpeedBean createFromParcel(Parcel source) {
            return new ExoSpeedBean(source);
        }

        @Override
        public ExoSpeedBean[] newArray(int size) {
            return new ExoSpeedBean[size];
        }
    };
}
