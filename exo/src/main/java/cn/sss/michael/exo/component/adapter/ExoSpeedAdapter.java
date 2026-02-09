package cn.sss.michael.exo.component.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.bean.ExoSpeedBean;
import cn.sss.michael.exo.util.ExoLog;

import java.util.List;

/**
 * @author Michael by 61642
 * @date 2026/1/5 17:01
 * @Description 播放速度列表适配器
 */
public class ExoSpeedAdapter extends RecyclerView.Adapter<ExoSpeedAdapter.SpeedViewHolder> {
    private Context mContext;
    private List<ExoSpeedBean> mData;
    private OnSpeedAdapterCallBack onSpeedAdapterCallBack;

    public ExoSpeedAdapter(Context context, List<ExoSpeedBean> data) {
        this.mContext = context;
        this.mData = data;
    }

    public void setOnSpeedAdapterCallBack(OnSpeedAdapterCallBack onSpeedAdapterCallBack) {
        this.onSpeedAdapterCallBack = onSpeedAdapterCallBack;
    }

    public void clear() {
        if (mData != null) {
            mData.clear();
            notifyDataSetChanged();
        }
        onSpeedAdapterCallBack = null;
        mContext = null;
    }

    public void setChecked(int position) {
        if (mData == null) {
            ExoLog.log("设置倍速失败：数据源为空");
            return;
        }
        if (position >= 0 && position <= mData.size() - 1) {
            for (int i = 0; i < mData.size(); i++) {
                mData.get(i).checked = false;
            }
            mData.get(position).checked = true;
            notifyDataSetChanged();
            ExoLog.log("设置倍速：" + position + "，倍速：" + mData.get(position).title);
        } else {
            ExoLog.log("设置倍速失败：位置越界，position=" + position + "，数据源长度=" + mData.size());
        }
    }

    @NonNull
    @Override
    public SpeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext)
                .inflate(R.layout.item_layout_exo_component_speed_view, parent, false);
        return new SpeedViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SpeedViewHolder holder, int position) {
        ExoSpeedBean item = mData.get(position);
        holder.tvSpeed.setText(item.title);
        if (item.checked) {
            holder.tvSpeed.setTextColor(ContextCompat.getColor(mContext, R.color.exo_colorAccent));
        } else {
            holder.tvSpeed.setTextColor(ContextCompat.getColor(mContext, R.color.exo_white));
        }
        holder.clickParent.setOnClickListener(v -> {
            if (onSpeedAdapterCallBack != null) {
                onSpeedAdapterCallBack.onClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public static class SpeedViewHolder extends RecyclerView.ViewHolder {
        TextView tvSpeed;
        View clickParent;

        public SpeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSpeed = itemView.findViewById(R.id.txt);
            clickParent = itemView.findViewById(R.id.click_parent);
        }
    }

    public interface OnSpeedAdapterCallBack {
        void onClick(ExoSpeedBean exoSpeedBean, int position);
    }
}