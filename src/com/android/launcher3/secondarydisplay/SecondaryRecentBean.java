package com.android.launcher3.secondarydisplay;

import com.android.systemui.shared.recents.model.ThumbnailData;
import android.content.pm.LauncherActivityInfo;
import java.util.Arrays;

public class SecondaryRecentBean {
    private ThumbnailData data;
    private int taskId;
    private LauncherActivityInfo activityInfo;

    public SecondaryRecentBean(int taskId,ThumbnailData data,LauncherActivityInfo activityInfo){
        this.taskId = taskId;
        this.data = data;
        this.activityInfo = activityInfo;
    }

    public ThumbnailData getThumbnailData(){
        return this.data;
    }

    public int getTaskId(){
        return this.taskId;
    }

    public LauncherActivityInfo getActivityInfo(){
        return this.activityInfo;
    }

    @Override
    public boolean equals(Object o) {
        SecondaryRecentBean other = (SecondaryRecentBean) o;
        return other.getTaskId() == this.taskId;
    }

    @Override
    public int hashCode(){
        return Arrays.hashCode(new Object[] {taskId});
    }
}
