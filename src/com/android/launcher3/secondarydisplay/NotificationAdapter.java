package com.android.launcher3.secondarydisplay;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;
import java.lang.Exception;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<StatusBarNotification> notificationList = new ArrayList<>();
    private Context mContext;
    private NotificationCallback mNotificationCallback;
    private long addNotifyTime = -1L;

    public NotificationAdapter(Context context){
        mContext =context;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder( ViewGroup parent, int viewType) {
       View view= LayoutInflater.from(mContext).inflate(R.layout.item_notification,parent,false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder( RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof NotificationViewHolder){
            NotificationViewHolder notificationViewHolder = (NotificationViewHolder) holder;
            StatusBarNotification sbn = notificationList.get(position);
            Notification notification = sbn.getNotification();
            Icon icon = notification.getSmallIcon();
            CharSequence title =notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if(icon != null){
                notificationViewHolder.mIcon.setImageDrawable(icon.loadDrawable(mContext));
            }
            if(title != null) {
                notificationViewHolder.mTitle.setText(title.toString());
            }
            if(text != null) {
                notificationViewHolder.mText.setText(text.toString());
            }
            notificationViewHolder.itemView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    if(notification.contentIntent !=null){
                        try {
                            notification.contentIntent.send();
                        } catch (Exception e) {
                            //TODO: handle exception
                        }
                    }
                    if(mNotificationCallback != null) {
                        mNotificationCallback.onClickNotification(sbn);
                    }
                }
            });

        }

    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public void setNotificationList(List<StatusBarNotification> activeNotifications) {
        notificationList.clear();
        notificationList.addAll(activeNotifications);
        notifyDataSetChanged();
    }

    public void addNotification(StatusBarNotification sbn){
        boolean isExisted = false;
        for(StatusBarNotification temp :notificationList){
            if(temp.getKey()!=null && temp.getKey().equals(sbn.getKey())) {
                isExisted = true;
                break;
            }
        }
        Notification notification = sbn.getNotification();
        if(!isExisted) {
            if("phone_missed_call".equals(notification.getChannelId())){
                if(System.currentTimeMillis() - addNotifyTime > 100){
                    notificationList.add(0, sbn);
                    notifyItemInserted(0);
                    addNotifyTime = System.currentTimeMillis();
                }
            }else {
                notificationList.add(0, sbn);
                notifyItemInserted(0);
            }
        }
    }

    public void removeNotification(StatusBarNotification sbn){
        int index = -1;
        for(int i = 0;i < notificationList.size(); i++){
            if(notificationList.get(i).getKey().equals(sbn.getKey())){
                index = i;
                break;
            }
        }
        if(index >= 0){
            notificationList.remove(index);
            notifyItemRemoved(index);
        }
    }

    public void setNotificationCallback(NotificationCallback callback){
        this.mNotificationCallback = callback;
    }

    private class NotificationViewHolder extends RecyclerView.ViewHolder{
        public ImageView mIcon;
        public TextView mTitle;
        public TextView mText;

        public NotificationViewHolder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.icon);
            mTitle = itemView.findViewById(R.id.title);
            mText = itemView.findViewById(R.id.content);
        }
    }

    public interface NotificationCallback {
        void onClickNotification(StatusBarNotification sbn);
    }
}
