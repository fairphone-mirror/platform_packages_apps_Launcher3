package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.APP_INFO;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.UNINSTALL;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.PinTaskBarShortcut;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.PinDesktopShortcut;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_ALL_APPS;
import static android.content.Context.MODE_PRIVATE;

import android.util.Log;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.view.Display;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.net.Uri;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;
import android.widget.EditText;
import android.view.inputmethod.InputMethodManager;
import android.content.pm.ActivityInfo;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.pm.ApplicationInfo;
import android.text.TextWatcher;
import android.text.Editable;
import com.android.systemui.shared.system.PackageManagerWrapper;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;
import com.android.launcher3.secondarydisplay.OverlayShortcut;
import com.android.launcher3.secondarydisplay.PinIconUtils;
import com.android.launcher3.util.Executors;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SecondaryAllAppsView extends BaseOverlayView implements BgDataModel.Callbacks, OnSharedPreferenceChangeListener, TextWatcher {

    private static final String WINDOW_TITLE = "SecondaryAllApps";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private RecyclerView mRecyclerView;
    private ExtendedEditText mEtSearch;
    private SecondaryAllAppsAdapter mAppsAdapter;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private LauncherModel mModel;
    private SecondaryDisplayLauncher mLauncher;
    private SecondaryPopupContainerView mPopupContainer;
    private static final String PINNED_DESKTOP_APPS_KEY = "pinned_desktop_apps";
    private static final String PINNED_TASKBAR_APPS_KEY = "pinned_taskbar_apps";
    private final SharedPreferences mDesktopPrefs;
    private final Set<DesktopIconKey> mDesktopApps = new HashSet<>();
    private SharedPreferences mTaskbarPrefs;
    private final Set<ComponentKey> mTaskbarPinnedApps = new HashSet<>();

    public SecondaryAllAppsView(Context context) {
        super(context, Themes.getActivityThemeRes(context));
        mLauncher = SecondaryDisplayLauncher.fromContext(context);
        mModel = LauncherAppState.getInstance(mContext).getModel();
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater.inflate(R.layout.secondary_all_apps, null, false);
        mRecyclerView = (RecyclerView) mLayout.findViewById(R.id.rv_apps);
        mEtSearch = (ExtendedEditText) mLayout.findViewById(R.id.et_search);
        mPopupContainer = new SecondaryPopupContainerView(context);
        mRecyclerView.setLayoutManager(new GridLayoutManager(mContext, 5));
        mAppsAdapter = new SecondaryAllAppsAdapter(mContext);
        mAppsAdapter.setListener(new SecondaryAllAppsAdapter.SecondaryAllAppsListener(){
            @Override
            public void onIconClick(View v,ItemInfo info, int taskId){
                mLauncher.onIconClick(v, info, -1);
                dissmiss();
            }

            @Override
            public void onIconLongClick(View v,ItemInfo info){
                onLongClick(v, info);
            }
        });
        mRecyclerView.setAdapter(mAppsAdapter);
        mLayout.setOnClickListener(view ->{
            dissmiss();
        });
        mEtSearch.addTextChangedListener(this);
        mDesktopPrefs = context.getSharedPreferences(PINNED_DESKTOP_APPS_KEY, MODE_PRIVATE);
        mTaskbarPrefs = context.getSharedPreferences(PINNED_TASKBAR_APPS_KEY,MODE_PRIVATE);
        mDesktopPrefs.registerOnSharedPreferenceChangeListener(this);
        mTaskbarPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mDesktopPrefs, PINNED_DESKTOP_APPS_KEY);
        onSharedPreferenceChanged(mTaskbarPrefs, PINNED_TASKBAR_APPS_KEY);
        mModel.addCallbacksAndLoad(this);

    }

    private void onLongClick(View v,ItemInfo info){
        List<OverlayShortcut> shortcuts = new ArrayList<>();
        shortcuts.add(APP_INFO.getShortcut(mPopupContainer, info));
        ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);
        shortcuts.add(new PinTaskBarShortcut(mPopupContainer,info,mTaskbarPinnedApps.contains(key)));
        boolean pinnedDesktop = false;
        for(DesktopIconKey desktopKey : mDesktopApps){
            if(key.equals(desktopKey.getComponentKey())){
                pinnedDesktop = true;
                break;
            }
        }

        shortcuts.add(new PinDesktopShortcut(mPopupContainer,info,pinnedDesktop));
        ActivityInfo activityInfo = PackageManagerWrapper.getInstance().getActivityInfo(
            info.getTargetComponent(), info.user.myUserId());
        if(activityInfo !=null){
            if((activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0){
                shortcuts.add(UNINSTALL.getShortcut(mPopupContainer, info));
            }
        }
        mPopupContainer.showPopupContainter(shortcuts, v);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        String query = s.toString();
        mAppsAdapter.searchFilter(query);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PINNED_DESKTOP_APPS_KEY.equals(key)) {
            Executors.MODEL_EXECUTOR.submit(() -> {
                Set<DesktopIconKey> apps = prefs.getStringSet(key, Collections.emptySet())
                        .stream()
                        .map(s->PinIconUtils.parseDesktopIconKey(s))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mDesktopApps.clear();
                    mDesktopApps.addAll(apps);
                });
            });
        } else if (PINNED_TASKBAR_APPS_KEY.equals(key)){
            Executors.MODEL_EXECUTOR.submit(() -> {
                Set<ComponentKey> apps = prefs.getStringSet(key, Collections.emptySet())
                        .stream()
                        .map(s->PinIconUtils.parseComponentKey(s))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mTaskbarPinnedApps.clear();
                    mTaskbarPinnedApps.addAll(apps);
                });
            });
        }
    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_ALL_APPS) != 0;
    }

    @Override
    public void show(){
        if(isShow()){
            return;
        }
        int height = mContext.getDisplay().getHeight() - dpToPx(32);
        mWindowLayoutParams = new WindowManager.LayoutParams(
                MATCH_PARENT,
                height,
                TYPE_APPLICATION_OVERLAY,
                //WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_SLIPPERY
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = getPackageName();
        mWindowLayoutParams.gravity = Gravity.RIGHT|Gravity.TOP;
        mWindowLayoutParams.windowAnimations = R.style.bottom_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
    }


    @Override
    public void dissmiss(){
        if(!isShow()){
            return;
        }
        mEtSearch.reset();
        setShow(false);
        mWindowManager.removeViewImmediate(mLayout);
    }

    @Override
    public void onDestroy() {
        mContext = null;
        mDesktopPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mTaskbarPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mHandler.removeCallbacksAndMessages(null);
        mModel.removeCallbacks(this);
    }

    @Override
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        List<AppInfo> appList = new ArrayList<>();
        Collections.addAll(appList, apps);
        mAppsAdapter.setDatas(appList);
    }
}
