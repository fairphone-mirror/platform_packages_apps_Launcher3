
package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.InsetsSource.FLAG_SUPPRESS_SCRIM;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static android.view.Display.INVALID_DISPLAY;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.APP_INFO;
import static com.android.launcher3.secondarydisplay.OverlayShortcut.PinTaskBarShortcut;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_TASKBAR;
import static com.android.launcher3.Utilities.dpToPx;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.content.pm.LauncherApps;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.InputDevice;
import android.view.MotionEvent;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.format.DateFormat;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.BatteryManager;
import android.content.res.Resources;
import java.util.Date;
import java.util.List;
import java.lang.System;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import android.graphics.Insets;
import android.view.InsetsFrameProvider;

import android.provider.Settings;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;
import com.android.launcher3.secondarydisplay.SecondaryTaskBarListner;
import com.android.launcher3.secondarydisplay.SecondaryRecentsListener;
import com.android.launcher3.secondarydisplay.SecondarySystemUIProxy;
import com.android.launcher3.secondarydisplay.SecondaryPopupContainerView;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import com.android.launcher3.secondarydisplay.OverlayShortcut;
import com.android.launcher3.secondarydisplay.OverlayManager;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.AbstractFloatingView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import android.content.pm.LauncherActivityInfo;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.content.pm.LauncherApps;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.SubscriptionManager;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.view.WindowInsets;
import android.os.Process;
import android.os.UserHandle;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class SecondaryTaskBarView extends BaseOverlayView implements OnSharedPreferenceChangeListener {

    private static final String WINDOW_TITLE = "SecondaryTaskbar";
    private final SecondaryDisplayLauncher mLauncher;
    private final LayoutInflater mLayoutInflater;
    private final int maxSize = 12;
    private View mLayout;
    private View mStartButton;
    private ImageView mBatteryView;
    private ImageView mWifiView;
    private ImageView mCellular1View;
    private ImageView mCellularUnable;
    private View mCellularLayout;
    private TextView mTimeView;
    private LinearLayout mStatusBarLayout;
    private HorizontalScrollView mTaskScroll;
    private LinearLayout mTaskbar;
    private ImageView mIvBack;
    private ImageView mIvHome;
    private ImageView mIvRecent;
    private SecondaryTaskBarListner mListener;
    private SecondaryTaskBarReceiver mReceiver;
    private int mLevel;
    private ConnectivityManager mConnectivityManager;
    private SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private WifiManager mWifiManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private SimTelephonyCallback mTelephonyCallback;
    private int cellStrength = -1;
    private AllAppsStore mAllAppsList;
    private static final String PINNED_TASKBAR_APPS_KEY = "pinned_taskbar_apps";
    private SharedPreferences mTaskbarPrefs;
    private final Set<ComponentKey> mTaskbarPinnedApps = new HashSet<>();
    private final ArrayList<AppInfo> mItems = new ArrayList<>();
    private final ArrayList<AppInfo> mRecentItems = new ArrayList<>();
    private SecondaryPopupContainerView mPopupContainer;
    private final IBinder mInsetsOwner = new Binder();

    public SecondaryTaskBarView(SecondaryDisplayLauncher launcher,AllAppsStore allAppsStore,SecondaryTaskBarListner listener){
        super(launcher.getBaseContext(), Themes.getActivityThemeRes(launcher.getBaseContext()));
        mLauncher = launcher;
        mPopupContainer = new SecondaryPopupContainerView(mLauncher);
        mListener = listener;
        mAllAppsList = allAppsStore;
        mLayoutInflater = launcher.getLayoutInflater();
        mLayout = mLayoutInflater
                .inflate(R.layout.secondary_task_bar, null, false);
        mStartButton = mLayout.findViewById(R.id.start_button);
        mBatteryView = (ImageView) mLayout.findViewById(R.id.battery);
        mTimeView = (TextView) mLayout.findViewById(R.id.tv_time);
        mWifiView =  (ImageView) mLayout.findViewById(R.id.wifi);
        mCellularLayout = mLayout.findViewById(R.id.cellular_layout);
        mCellular1View = (ImageView) mLayout.findViewById(R.id.cellular1);
        mCellularUnable = (ImageView) mLayout.findViewById(R.id.cellualar_unable);
        mStatusBarLayout = (LinearLayout) mLayout.findViewById(R.id.status_bar);
        mTaskScroll = (HorizontalScrollView) mLayout.findViewById(R.id.taskbar_scrollview);
        mTaskbar = (LinearLayout) mLayout.findViewById(R.id.taskbar);
        mIvBack = (ImageView) mLayout.findViewById(R.id.iv_back);
        mIvHome = (ImageView) mLayout.findViewById(R.id.iv_home);
        mIvRecent = (ImageView) mLayout.findViewById(R.id.iv_recent);
        mTimeView.setOnClickListener(this::onCalendar);
        mIvBack.setOnClickListener(this::onBack);
        mIvHome.setOnClickListener(this::onHome);
        mIvRecent.setOnClickListener(this::onRecent);
        mStartButton.setOnClickListener(this::onStartButton);
        mStatusBarLayout.setOnClickListener(this::onStatusBar);
        updateTime(launcher.getBaseContext());

        mReceiver = new SecondaryTaskBarReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(mReceiver,filter);
        Display display = launcher.getBaseContext().getDisplay();
        mContext = display.getDisplayId() == Display.DEFAULT_DISPLAY
                ? launcher.getBaseContext().getApplicationContext()
                : launcher.getBaseContext().getApplicationContext().createDisplayContext(display);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback, mHandler);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager = mTelephonyManager.createForSubscriptionId(
            SubscriptionManager.getDefaultSubscriptionId());
        mTelephonyCallback = new SimTelephonyCallback();
        mTelephonyManager.registerTelephonyCallback(mContext.getMainExecutor(),mTelephonyCallback);
        mTaskbarPrefs = mContext.getSharedPreferences(PINNED_TASKBAR_APPS_KEY,MODE_PRIVATE);
        mTaskbarPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mTaskbarPrefs,PINNED_TASKBAR_APPS_KEY);

        mAllAppsList.addUpdateListener(this::onAppsUpdated);
        mLauncher.getSecondaryRecentsListener().setTaskInfoListCallback(this::applyRecentsTask);
    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_TASKBAR) != 0;
    }

    @Override
    public void show() {
        int height = dpToPx(48);
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
        mWindowLayoutParams.gravity = Gravity.BOTTOM;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        final InsetsFrameProvider provider =
                new InsetsFrameProvider(mInsetsOwner, 0, WindowInsets.Type.tappableElement());
        provider.setInsetsSize(Insets.of(0, 0, 0, height));
        mWindowLayoutParams.providedInsets = new InsetsFrameProvider[] {
            provider
        };

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
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
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        if(mPopupContainer != null){
            mPopupContainer.onDestroy();
        }
        mHandler.removeCallbacksAndMessages(null);
        mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
        mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        mTaskbarPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mWindowManager.removeViewImmediate(mLayout);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PINNED_TASKBAR_APPS_KEY.equals(key)){
            Executors.MODEL_EXECUTOR.submit(() -> {
                Set<ComponentKey> apps = mTaskbarPrefs.getStringSet(key, Collections.emptySet())
                        .stream()
                        .map(this::parseComponentKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mTaskbarPinnedApps.clear();
                    mTaskbarPinnedApps.addAll(apps);
                    updateRecents();
                });
            });
        }
    }

    private ComponentKey parseComponentKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user = Process.myUserHandle();
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            return new ComponentKey(cn, user);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateRecents(){
        createFilteredAppsList();
        mTaskbar.removeAllViews();
        int size = Math.min(mItems.size() + mRecentItems.size(),maxSize);
        calculateScrollViewParams(size);
        for(int i =0;i<mItems.size();i++){
            AppInfo info = mItems.get(i);
            mTaskbar.addView(createIconView(info));
        }
        for(int i =0; i<mRecentItems.size(); i++){
            AppInfo info = mRecentItems.get(i);
            mTaskbar.addView(createIconView(info));
        }
    }
    
    private void calculateScrollViewParams(int size){
        ViewGroup.LayoutParams params = mTaskScroll.getLayoutParams();
        int recentsSize = getResources().getDimensionPixelSize(R.dimen.secondary_taskbar_icon_size) * size;
        params.width = recentsSize;
        mTaskScroll.setLayoutParams(params);
    }

    private void createFilteredAppsList() {
        mItems.clear();
        mTaskbarPinnedApps.stream().map(mAllAppsList::getApp)
                .filter(Objects::nonNull).forEach(mItems::add);
        Iterator<AppInfo> iterator = mRecentItems.iterator();
        while(iterator.hasNext()){
            AppInfo info = iterator.next();
            boolean isPinned = mTaskbarPinnedApps.contains(new ComponentKey(info.getTargetComponent(), info.user));
            if(isPinned){
                iterator.remove();
            }
        }
    }

    private void onAppsUpdated() {
        updateUninstall();
        createFilteredAppsList();
    }

    private void updateUninstall(){
        Iterator<ComponentKey> iterator = mTaskbarPinnedApps.iterator();
        boolean removeUninstall = false;
        while(iterator.hasNext()){
            ComponentKey key = iterator.next();
            AppInfo info = mAllAppsList.getApp(key);
            if(info == null){
                removeUninstall = true;
                iterator.remove();
            }
        }
        if(removeUninstall) {
            Set<ComponentKey> copy = new HashSet<>(mTaskbarPinnedApps);
            Executors.MODEL_EXECUTOR.submit(() ->
                    mTaskbarPrefs.edit().putStringSet(PINNED_TASKBAR_APPS_KEY,
                        copy.stream().map(this::encode).collect(Collectors.toSet()))
                        .apply());
        }
    }

    private String encode(ComponentKey key) {
        return key.componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(mLauncher).getSerialNumberForUser(key.user);
    }

    private View createIconView(AppInfo info){
        View convertView = mLayoutInflater.inflate(R.layout.secondary_taskbar_icon,mLauncher.getDragLayer(),false);
        BubbleTextView icon = convertView.findViewById(R.id.icon);
        ImageView pinnedIcon = convertView.findViewById(R.id.pinned_icon);
        icon.applyFromApplicationInfo(info);
        icon.setTextVisibility(false);
        boolean isShowPinned = mTaskbarPinnedApps.contains(new ComponentKey(info.getTargetComponent(), info.user));
        pinnedIcon.setVisibility(isShowPinned ? View.VISIBLE :View.GONE);
        icon.setOnClickListener(view->{
            int taskId = -1;
            int displayId = mLauncher.getBaseContext().getDisplay().getDisplayId();
            for(ActivityManager.RunningTaskInfo taskInfo : mLauncher.getSecondaryRecentsListener().getRecentsList()){
                if(displayId == taskInfo.displayId && taskInfo.baseActivity !=null
                        && taskInfo.baseActivity.equals(info.getTargetComponent())){
                    taskId = taskInfo.taskId;
                    break;
                }
            }
            mListener.onIconClick(view, info,taskId);
        });
        icon.setOnLongClickListener(view->{
            onIconLongClicked(view,info);
            return true;
        });
        icon.setOnTouchListener((view,event)->{
            if(event.getAction() == MotionEvent.ACTION_DOWN
                    && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                onIconLongClicked(view,info);
            }
            return false;
        });
        return convertView;
    }

    private void onIconLongClicked(View view,AppInfo info){
        mPopupContainer.dissmiss();

        List<OverlayShortcut> shortcuts = new ArrayList<>();
        shortcuts.add(APP_INFO.getShortcut(mPopupContainer, info));
        ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);
        shortcuts.add(new PinTaskBarShortcut(mPopupContainer,info,mTaskbarPinnedApps.contains(key)));
        int index = mTaskbar.indexOfChild((ViewGroup) view.getParent());
        int x = (index + 1)* getResources().getDimensionPixelSize(R.dimen.secondary_taskbar_icon_size);
        int y = mLauncher.getDragLayer().getHeight() - dpToPx(270);
        mPopupContainer.showPopupContainter(shortcuts, x, y);
    }

    protected synchronized void applyRecentsTask(ArrayList<LauncherActivityInfo> tasks) {
        mRecentItems.clear();
        int size = tasks.size();
        for(int i = 0;i < size; i++){
            LauncherActivityInfo activityInfo = tasks.get(i);
            AppInfo appInfo = new AppInfo(this,activityInfo,Process.myUserHandle());
            mRecentItems.add(appInfo);
        }
        updateRecents();
    }

    private void onStartButton(View v){
        mListener.onStartButtonClick();
    }
    
    private void onStatusBar(View v){
        mListener.onStatusBarClick();
    }

    private void onBack(View v){
        navigateBack();
    }

    private void onHome(View v){
        mListener.onHomeClick();
    }

    private void onRecent(View v) {
        mListener.onRecentClick();
    }

    private void onCalendar(View v){
        mListener.onCalendarClick();
    }

    private void navigateBack() {
        AbstractFloatingView.closeAllOpenViews(mLauncher);
        OverlayManager.getInstance().dissmissAll();
        SecondarySystemUIProxy.INSTANCE.get(mContext).onBackPressed();
    }

    private class SecondaryTaskBarReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mLevel = (int)(100f
                        * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                String batDrawable;
                if(mLevel < 10 ){
                    batDrawable = "alert";
                } else if(mLevel < 25) {
                    batDrawable = "20";
                } else if(mLevel < 40) {
                    batDrawable = "30";
                } else if(mLevel < 55) {
                    batDrawable = "50";
                } else if(mLevel < 70) {
                    batDrawable = "60";
                } else if(mLevel < 85) {
                    batDrawable = "80";
                } else if(mLevel < 95) {
                    batDrawable = "90";
                } else {
                    batDrawable = "full";
                }
                String batRes = "tb_battery_" + batDrawable;
                mBatteryView.setImageDrawable(getDrawableForSysTray(getResourceIdFor(batRes)));
            } else if (Intent.ACTION_TIME_TICK.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)){
                updateTime(context);
            }

        }
    }

    private final NetworkCallback mDefaultNetworkCallback =
                  new NetworkCallback(NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
              @Override
              public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                    updateWifi();
                }
              }
              @Override
              public void onLost(Network network) {
                updateWifi();
              }
    };

    class SimTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.DataConnectionStateListener,
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.ServiceStateListener{
        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            updateCellularSignal();
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if(signalStrength != null) {
                cellStrength = signalStrength.getLevel();
            }
            updateCellularSignal();
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {

        }
    }

    private int getResourceIdFor(String name) {
        String packageName = getResources().getResourcePackageName(R.drawable.tb_battery_80);
        return getResources().getIdentifier(name, "drawable", packageName);
    }

    private void updateTime(Context context) {
        mTimeView.setText(context.getString(R.string.tb_clock,
                DateFormat.getTimeFormat(context).format(new Date()),
                DateFormat.getDateFormat(context).format(new Date())));
    }

    private void updateWifi(){
        if(getWifiDrawable() == null) {
            mWifiView.setVisibility(View.GONE);
            return;
        }
        mWifiView.setVisibility(View.VISIBLE);
        mWifiView.setImageDrawable(getWifiDrawable());
    }

    private void updateCellularSignal(){
        int subId = SubscriptionManager.getDefaultSubscriptionId();
        if(subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID){
            mCellularLayout.setVisibility(View.GONE);
            return;
        }
        if(cellStrength == -1){
            mCellularLayout.setVisibility(View.GONE);
            return;
        }
        mCellularLayout.setVisibility(View.VISIBLE);
        mCellular1View.setImageDrawable(getCellularDrawable());
        boolean dataEnabled = mTelephonyManager.isDataEnabled();
        mCellularUnable.setVisibility(dataEnabled ? View.GONE : View.VISIBLE);
    }
    private Drawable getDrawableForSysTray(int id) {
        Drawable drawable = null;
        try {
            drawable = getDrawable(id);
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
        return drawable;
    }

    private Drawable getWifiDrawable() {

        NetworkInfo wifi = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(wifi == null || !wifi.isConnected())
            return null;


        int numberOfLevels = 5;

        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        int level = mWifiManager.calculateSignalLevel(wifiInfo.getRssi(), numberOfLevels);

        String wifiRes = "tb_signal_wifi_" + level + "_bar";
        int id = getResourceIdFor(wifiRes);

        return getDrawableForSysTray(id);
    }

    private Drawable getCellularDrawable() {
        if(Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
            return getDrawableForSysTray(R.drawable.tb_airplanemode_active);

        if(cellStrength == -1)
            return null;

        String cellRes = "tb_signal_cellular_" + cellStrength + "_bar";
        int id = getResourceIdFor(cellRes);

        return getDrawableForSysTray(id);
    }
}
