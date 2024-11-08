package com.android.launcher3.secondarydisplay;

import android.service.notification.StatusBarNotification;
import java.util.List;

public interface SecondaryNotificationsChangeListener {

    void onNotificationPosted(StatusBarNotification sbn);
    void onNotificationRemoved(StatusBarNotification sbn);
    void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications);
    
}
