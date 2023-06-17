package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_RECENTS;
import static com.android.launcher3.Utilities.dpToPx;

import android.app.Activity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import android.content.pm.LauncherActivityInfo;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.secondarydisplay.SecondaryRecentAdapter;
import com.android.launcher3.secondarydisplay.SpaceItemDecoration;
import com.android.launcher3.secondarydisplay.SecondaryRecentBean;
import com.android.launcher3.secondarydisplay.RecentSwipeCallback;
import com.android.launcher3.secondarydisplay.BaseOverlayView;

import java.util.Set;
import java.util.List;

public class SecondaryRecentsView extends BaseOverlayView {
    private static final String WINDOW_TITLE = "SecondarySlideBar";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private View mEmptyView;
    private SecondaryDisplayLauncher mLauncher;
    private RecyclerView mTaskRv;
    private TextView mClearAll;
    private SecondaryRecentAdapter mRecentAdapter;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public SecondaryRecentsView(SecondaryDisplayLauncher launcher) {
        super(launcher.getBaseContext(), Themes.getActivityThemeRes(launcher.getBaseContext()));
        mLauncher = launcher;
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater.inflate(R.layout.secondary_recents, null, false);
        mTaskRv = (RecyclerView) mLayout.findViewById(R.id.rv_task);
        mEmptyView = mLayout.findViewById(R.id.empty_view);
        mClearAll = (TextView) mLayout.findViewById(R.id.tv_clear_all);
        mTaskRv.addItemDecoration(new SpaceItemDecoration(dpToPx(8)));
        mLayout.setOnClickListener(view ->{
            dissmiss();
        });
        mClearAll.setOnClickListener(view ->{
            for(SecondaryRecentBean bean : mRecentAdapter.getRecentsList()){
                ActivityManagerWrapper.getInstance().removeTask(bean.getTaskId());
            }
            mHandler.postDelayed(()->{
                dissmiss();
            },500);
        });
        Display display = launcher.getBaseContext().getDisplay();
        mContext = display.getDisplayId() == Display.DEFAULT_DISPLAY
                ? launcher.getBaseContext().getApplicationContext()
                : launcher.getBaseContext().getApplicationContext().createDisplayContext(display);
        mRecentAdapter = new SecondaryRecentAdapter(mContext);
        mRecentAdapter.setTaskListener(new SecondaryRecentAdapter.TaskListener() {
            @Override
            public void onTaskClick(int taskId,LauncherActivityInfo activityInfo){
                if(taskId == -1){
                    mLauncher.startActivity(AppInfo.makeLaunchIntent(activityInfo));
                } else {
                    ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId, null);
                }
                dissmiss();
            }

            @Override
            public void onNoTaskExist(){
                dissmiss();
            }
        });
        mTaskRv.setLayoutManager(new LinearLayoutManager(mContext,LinearLayoutManager.HORIZONTAL,false));
        mTaskRv.setAdapter(mRecentAdapter);
        RecentSwipeCallback swipeCallback = new RecentSwipeCallback(mRecentAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
        itemTouchHelper.attachToRecyclerView(mTaskRv);

    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_RECENTS) != 0;
    }

    @Override
    public void show() {
        if(isShow()){
            return;
        }
        mLauncher.getSecondaryRecentsListener().getTaskThumbnail(this::updateRecents);

        int height = mLauncher.getBaseContext().getDisplay().getHeight() - dpToPx(16);
        mWindowLayoutParams = new WindowManager.LayoutParams(
                MATCH_PARENT,
                height,
                TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_SLIPPERY
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = getPackageName();
        mWindowLayoutParams.gravity = Gravity.TOP;
        mWindowLayoutParams.windowAnimations = R.style.recent_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
    }

    private void updateRecents(List<SecondaryRecentBean> taskList){
        mRecentAdapter.setRecentTasks(taskList);
        if(taskList.size() == 0) {
            mEmptyView.setVisibility(View.VISIBLE);
            mTaskRv.setVisibility(View.GONE);
            mClearAll.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mClearAll.setVisibility(View.VISIBLE);
            mTaskRv.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void dissmiss(){
        if(!isShow()){
            return;
        }
        setShow(false);
        mWindowManager.removeViewImmediate(mLayout);
    }

    @Override
    public void onDestroy(){
        mHandler.removeCallbacksAndMessages(null);
        mLauncher = null;
        mContext = null;
    }
}
