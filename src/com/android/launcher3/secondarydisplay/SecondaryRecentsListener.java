package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
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
import com.android.wm.shell.recents.IRecentTasksListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

@TargetApi(Build.VERSION_CODES.R)
public class SecondaryRecentsListener {

    private final LooperExecutor mMainThreadExecutor;
    private final ActivityManagerWrapper mActivityManagerWrapper;
    private ArrayList<LauncherActivityInfo> recentsList = new ArrayList();
    private List<ActivityManager.RunningTaskInfo> mRawList = new ArrayList();
    private LauncherApps mLauncherApps;
    private int mDisplayId;
    private Consumer<ArrayList<LauncherActivityInfo>> mCallback;
    private final int RECENT_SIZE = 8;
    private final SecondarySystemUIProxy mSysUiProxy;

    public SecondaryRecentsListener(LooperExecutor mainThreadExecutor,
            ActivityManagerWrapper activityManagerWrapper,
            LauncherApps launcherApps,
            int displayId,
            SecondarySystemUIProxy sysUiProxy) {
        mMainThreadExecutor = mainThreadExecutor;
        mActivityManagerWrapper = activityManagerWrapper;
        mLauncherApps = launcherApps;
        mDisplayId = displayId;
        mSysUiProxy = sysUiProxy;
        mSysUiProxy.registerRecentTasksListener(mRecentTaskListener);
    }

    private final IRecentTasksListener.Stub mRecentTaskListener = new IRecentTasksListener.Stub() {
        @Override
        public void onRecentTasksChanged() throws RemoteException {
            mMainThreadExecutor.execute(SecondaryRecentsListener.this::onRecentTasksChanged);
        }

        @Override
        public void onRunningTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
            mMainThreadExecutor.execute(() -> {
                SecondaryRecentsListener.this.onRunningTaskAppeared(taskInfo);
            });
        }

        @Override
        public void onRunningTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            mMainThreadExecutor.execute(() -> {
                SecondaryRecentsListener.this.onRunningTaskVanished(taskInfo);
            });
        }
    };

    private void onRecentTasksChanged() {
        getTaskKeys(RECENT_SIZE);
    }

    private void onRunningTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
    }

    private void onRunningTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
    }

    public void setTaskInfoListCallback(Consumer<ArrayList<LauncherActivityInfo>> callback){
        mCallback = callback;
    }

    private ArrayList<LauncherActivityInfo> getRecentTasks(int numTasks){
        recentsList.clear();
        mRawList.clear();
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<ActivityManager.RunningTaskInfo> runningList = mSysUiProxy.getRunningTasks(numTasks);
        mRawList.addAll(runningList);
        for(ActivityManager.RunningTaskInfo taskInfo : runningList){
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
            ArrayList<ActivityManager.RunningTaskInfo> runningList = mSysUiProxy.getRunningTasks(Integer.MAX_VALUE);
            for(ActivityManager.RunningTaskInfo taskInfo : runningList){
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
        mSysUiProxy.unregisterRecentTasksListener(mRecentTaskListener);
    }
}