package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.Utilities.dpToPx;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.view.LayoutInflater;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.search.StringMatcherUtility;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.lang.Exception;

public class SecondaryAllAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int MAX_RESULTS_COUNT = 5;
    private List<AppInfo> appList = new ArrayList<>();
    private List<AppInfo> allApps = new ArrayList<>();
    private Context mContext;
    private SecondaryAllAppsListener mListener;

    public SecondaryAllAppsAdapter(Context context){
        mContext = context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder( ViewGroup parent, int viewType) {
        BubbleTextView icon = (BubbleTextView)LayoutInflater.from(mContext).inflate(R.layout.all_apps_icon,parent,false);
        icon.getLayoutParams().height = dpToPx(108);
        return new ViewHolder(icon);
    }

    @Override
    public void onBindViewHolder( RecyclerView.ViewHolder holder, int position) {
        BubbleTextView icon = (BubbleTextView) holder.itemView;
        AppInfo info = appList.get(position);
        icon.reset();
        icon.applyFromApplicationInfo(info);
        icon.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v){
                if(mListener != null){
                    mListener.onIconClick(v, info, -1);
                }
            }
        });
        icon.setOnLongClickListener(new OnLongClickListener(){
            @Override
            public boolean onLongClick(View v){
                if(mListener != null){
                    mListener.onIconLongClick(v, info);
                }
                return true;
            }
        });
        icon.setOnGenericMotionListener((view, motionEvent) -> {
            int action = motionEvent.getAction();

            if(action == MotionEvent.ACTION_BUTTON_PRESS
                    && motionEvent.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                if(mListener != null){
                    mListener.onIconLongClick(view, info);
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public void setDatas(List<AppInfo> apps) {
        allApps.clear();
        appList.clear();
        allApps.addAll(apps);
        appList.addAll(apps);
        Iterator<AppInfo> iterator = appList.iterator();
        while(iterator.hasNext()){
            AppInfo info = iterator.next();
            if("com.fp5.camera".equals(info.getTargetComponent().getPackageName())){
                iterator.remove();
                break;
            }
        }
        appList.sort(AppInfo.TITLE_COMPARATOR);
        notifyDataSetChanged();
    }

    public void searchFilter(String query){
        appList.clear();
        if(query.isEmpty()){
            appList.addAll(allApps);
        } else {
            StringMatcherUtility.StringMatcher matcher =
                    StringMatcherUtility.StringMatcher.getInstance();
            int total = allApps.size();
            int resultCount = 0;
            String queryTextLower = query.toLowerCase();
            for (int i = 0; i < total && resultCount < MAX_RESULTS_COUNT; i++) {
                AppInfo info = allApps.get(i);
                if (StringMatcherUtility.matches(queryTextLower, info.title.toString(), matcher)) {
                    appList.add(info);
                    resultCount++;
                }
            }
        }
        notifyDataSetChanged();
    }

    private class ViewHolder extends RecyclerView.ViewHolder{

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void setListener(SecondaryAllAppsListener listener){
        this.mListener = listener;
    }

    public interface SecondaryAllAppsListener{
        void onIconClick(View v,ItemInfo info,int taskId);

        void onIconLongClick(View v, ItemInfo info);
    }

}
