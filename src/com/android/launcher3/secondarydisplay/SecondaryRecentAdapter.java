package com.android.launcher3.secondarydisplay;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.util.DisplayMetrics;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.secondarydisplay.SecondaryRecentBean;
import com.android.systemui.shared.recents.model.ThumbnailData;
import android.content.pm.LauncherActivityInfo;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.lang.Exception;

public class SecondaryRecentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<SecondaryRecentBean> taskList = new ArrayList<>();
    private Context mContext;
    private TaskListener mListener;

    public SecondaryRecentAdapter(Context context){
        mContext = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder( ViewGroup parent, int viewType) {
       View view= LayoutInflater.from(mContext).inflate(R.layout.item_recent_view,parent,false);
        return new RecentViewHolder(view);
    }

    @Override
    public void onBindViewHolder( RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof RecentViewHolder){
            RecentViewHolder recentViewHolder = (RecentViewHolder) holder;
            SecondaryRecentBean bean = taskList.get(position);
            ThumbnailData thumbnail = bean.getThumbnailData();
            LauncherActivityInfo activityInfo = bean.getActivityInfo();
            recentViewHolder.mSnapshot.setImageBitmap(thumbnail.thumbnail);
            DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            recentViewHolder.mIcon.setImageDrawable(activityInfo.getIcon(displayMetrics.densityDpi));
            recentViewHolder.itemView.setOnClickListener(view ->{
                if(mListener !=null){
                    int taskId = bean.getTaskId();
                    mListener.onTaskClick(taskId, activityInfo);
                }

            });
        }

    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    public void setRecentTasks(List<SecondaryRecentBean> tasks) {
        taskList.clear();
        taskList.addAll(tasks);
        notifyDataSetChanged();
    }

    public void setTaskListener(TaskListener listener){
        this.mListener = listener;
    }

    public List<SecondaryRecentBean> getRecentsList(){
        return taskList;
    }

    public void removeTask(int position){
        if(position >=0 && position < taskList.size()){
            SecondaryRecentBean bean = taskList.get(position);
            ActivityManagerWrapper.getInstance().removeTask(bean.getTaskId());
            taskList.remove(position);
            notifyItemRemoved(position);
        }
        if(taskList.isEmpty()){
            if(mListener != null){
                mListener.onNoTaskExist();
            }
        }
    }

    private class RecentViewHolder extends RecyclerView.ViewHolder{
        public ImageView mIcon;
        public ImageView mSnapshot;

        public RecentViewHolder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.iv_app_icon);
            mSnapshot = itemView.findViewById(R.id.iv_task_snapshot);
        }
    }

    public interface TaskListener{
        void onTaskClick(int taskId,LauncherActivityInfo activityInfo);

        void onNoTaskExist();
    }
}
