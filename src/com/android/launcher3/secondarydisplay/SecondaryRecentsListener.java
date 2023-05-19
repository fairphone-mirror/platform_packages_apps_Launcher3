package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.content.pm.LauncherApps;
import android.util.SparseBooleanArray;

import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.secondarydisplay.SecondaryRecentBean;
import android.content.pm.LauncherActivityInfo;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

@TargetApi(Build.VERSION_CODES.R)
public class SecondaryRecentsListener implements TaskStackChangeListener {

    private final LooperExecutor mMainThreadExecutor;
    private final ActivityManagerWrapper mActivityManagerWrapper;
    private ArrayList<LauncherActivityInfo> recentsList = new ArrayList();
    private List<ActivityManager.RunningTaskInfo> mRawList = new ArrayList();
    private LauncherApps mLauncherApps;
    private int mDisplayId;
    private Consumer<ArrayList<LauncherActivityInfo>> mCallback;
    private final int RECENT_SIZE = 8;

    public SecondaryRecentsListener(LooperExecutor mainThreadExecutor,
            ActivityManagerWrapper activityManagerWrapper,
            LauncherApps launcherApps,
            int displayId) {
        mMainThreadExecutor = mainThreadExecutor;
        mActivityManagerWrapper = activityManagerWrapper;
        mLauncherApps = launcherApps;
        mDisplayId = displayId;
        TaskStackChangeListeners.getInstance().registerTaskStackListener(this);
    }

    @Override
    public void onTaskStackChanged() {
        getTaskKeys(RECENT_SIZE);
    }

    @Override
    public void onRecentTaskListUpdated() {
        //getTaskKeys(RECENT_SIZE);
    }

    @Override
    public void onTaskRemoved(int taskId) {

    }

    public void setTaskInfoListCallback(Consumer<ArrayList<LauncherActivityInfo>> callback){
        mCallback = callback;
    }

    private ArrayList<LauncherActivityInfo> getRecentTasks(int numTasks){
        recentsList.clear();
        mRawList.clear();
        int currentUserId = Process.myUserHandle().getIdentifier();
        ActivityManager.RunningTaskInfo[] rawTasks = 
                mActivityManagerWrapper.getRunningTasks(true);
        mRawList.addAll(Arrays.asList(rawTasks));
        for(ActivityManager.RunningTaskInfo taskInfo :rawTasks){
            if(taskInfo.baseActivity != null){
                String packageName = taskInfo.baseActivity.getPackageName();
                if("com.fp5.camera".equals(packageName)){
                    continue;
                }
                List<LauncherActivityInfo> appList = mLauncherApps.getActivityList(packageName,Process.myUserHandle());
                if(!appList.isEmpty()){
                    LauncherActivityInfo activityInfo = appList.get(0);
                    recentsList.add(activityInfo);
                }
            }
        }
        return recentsList;
    }

    public void getTaskKeys(int numTasks) {
        UI_HELPER_EXECUTOR.execute(() -> {
            ArrayList<LauncherActivityInfo> tasks = getRecentTasks(numTasks);
            mMainThreadExecutor.execute(() ->{
                if(mCallback != null){
                    mCallback.accept(tasks);
                }
            });
        });
    }

    public List<ActivityManager.RunningTaskInfo> getRecentsList(){
        return mRawList;
    }

    public void getTaskThumbnail(Consumer<List<SecondaryRecentBean>> callback) {
        UI_HELPER_EXECUTOR.execute(() -> {
            List<SecondaryRecentBean> taskList = new ArrayList<>();
            int currentUserId = Process.myUserHandle().getIdentifier();
            ActivityManager.RunningTaskInfo[] rawTasks = 
                    mActivityManagerWrapper.getRunningTasks(true);
            for(ActivityManager.RunningTaskInfo taskInfo :rawTasks){
                if(taskInfo.baseActivity != null){
                    String packageName = taskInfo.baseActivity.getPackageName();
                    if("com.fp5.camera".equals(packageName)){
                        continue;
                    }
                    List<LauncherActivityInfo> appList = mLauncherApps.getActivityList(packageName,Process.myUserHandle());
                    if(!appList.isEmpty()){
                        LauncherActivityInfo activityInfo = appList.get(0);
                        int taskId = taskInfo.taskId;
                        ThumbnailData thumbnailData = mActivityManagerWrapper.getTaskThumbnail(taskId,false);
                        SecondaryRecentBean recentBean;
                        if(mDisplayId == taskInfo.displayId){
                            recentBean= new SecondaryRecentBean(taskId,thumbnailData,activityInfo);
                        } else {
                            recentBean= new SecondaryRecentBean(-1,thumbnailData,activityInfo);
                        }
                        if(!taskList.contains(recentBean)) {
                            taskList.add(recentBean);
                        }
                    }
                }
            }
            mMainThreadExecutor.execute(() ->{
                    callback.accept(taskList);
            });
        });
    }

    public void onDestory(){
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(this);
    }
}