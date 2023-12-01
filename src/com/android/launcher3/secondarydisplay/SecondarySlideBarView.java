package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.telephony.TelephonyManager.SIM_STATE_READY;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS;
import static android.provider.Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_SLIDEBAR;
import static com.android.launcher3.Utilities.dpToPx;

import android.Manifest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.telephony.TelephonyCallback;
import android.telephony.ServiceState;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.SystemProperties;
import android.app.Activity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.content.pm.PackageManager;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.android.launcher3.secondarydisplay.SecondaryScreenshotView;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;
import com.android.launcher3.secondarydisplay.SecondaryNotificationsChangeListener;
import com.android.launcher3.secondarydisplay.NotificationAdapter;
import com.android.launcher3.secondarydisplay.SpaceItemDecoration;
import com.android.launcher3.secondarydisplay.ScreenshotUtils;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import com.android.launcher3.util.Executors;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import com.android.launcher3.notification.NotificationListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import android.os.Handler;
import android.os.Looper;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import android.provider.Settings;
import java.util.List;

public class SecondarySlideBarView extends BaseOverlayView {
    private static final String WINDOW_TITLE = "SecondarySlideBar";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private RecyclerView mNotificationRv;
    private NotificationAdapter mNotificationAdapter;
    private ImageButton mClearAll;
    private RadioButton mWifiSwitch;
    private RadioButton mBluetoothSwitch;
    private RadioButton mCellularSwitch;
    private RadioButton mSoundSwitch;
    private ImageButton mScreenShot;
    private RadioButton mDesktopMode;
    private TextView mWifiTitle;
    private TextView mBluetoothTitle;
    private TextView mCellularTitle;
    private TextView mSoundTitle;
    private TextView mScreenshotTitle;
    private TextView mDesktopModeTitle;
    private SeekBar mVolumeSeekbar;
    private SecondaryScreenshotView mScreenshotView;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Activity mActivity;
    private WifiManager mWifiManager;
    private TelephonyManager mTelephonyManager;
    private AudioManager mAudioManager;
    private BluetoothManager mBluetoothManager;
    private ConnectivityManager mConnectivityManager;
    private SlideBarBroadcastReceiver mReceiver;
    private SimTelephonyCallback mTelephonyCallback;

    public SecondarySlideBarView(Activity context) {
        super(context, Themes.getActivityThemeRes(context));
        mActivity = context;
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater
                .inflate(R.layout.secondary_slide_bar, null, false);
        mNotificationRv = (RecyclerView) mLayout.findViewById(R.id.rv_notification);
        mClearAll = (ImageButton) mLayout.findViewById(R.id.clear_all);
        mWifiSwitch = (RadioButton) mLayout.findViewById(R.id.wifi_switch);
        mWifiTitle = (TextView) mLayout.findViewById(R.id.tv_wifi_title);
        mBluetoothSwitch = (RadioButton) mLayout.findViewById(R.id.bluetooth_switch);
        mBluetoothTitle = (TextView) mLayout.findViewById(R.id.tv_bluetooth_title);
        mCellularSwitch = (RadioButton) mLayout.findViewById(R.id.cellular_switch);
        mCellularTitle = (TextView) mLayout.findViewById(R.id.tv_cellular_title);
        mSoundSwitch = (RadioButton) mLayout.findViewById(R.id.sound_switch);
        mSoundTitle = (TextView) mLayout.findViewById(R.id.tv_sound_title);
        mScreenShot = (ImageButton) mLayout.findViewById(R.id.screen_shot);
        mScreenshotTitle = (TextView) mLayout.findViewById(R.id.tv_screen_shot_title);
        mDesktopMode = (RadioButton) mLayout.findViewById(R.id.desktop_mode);
        mDesktopModeTitle = (TextView) mLayout.findViewById(R.id.tv_desktop_mode_title);
        mVolumeSeekbar = (SeekBar) mLayout.findViewById(R.id.volume_seekbar);
        mScreenshotView = new SecondaryScreenshotView(mActivity);
        mLayout.setOnClickListener(view->{
            dissmiss();
        });
        mClearAll.setOnClickListener(this::clearAll);
        mWifiSwitch.setOnClickListener(this::wifiSwitch);
        mBluetoothSwitch.setOnClickListener(this::bluetoothSwitch);
        mCellularSwitch.setOnClickListener(this::cellularSwitch);
        mSoundSwitch.setOnClickListener(this::soundSwitch);
        mScreenShot.setOnClickListener(this::screenShot);
        mDesktopMode.setOnClickListener(this::desktopModeSwitch);
        mNotificationAdapter = new NotificationAdapter(context);
        mNotificationRv.setLayoutManager(new LinearLayoutManager(context));
        mNotificationRv.addItemDecoration(new SpaceItemDecoration(dpToPx(8)));
        mNotificationAdapter.setNotificationCallback(sbn->{
            NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
            if(notificationListener != null){
                notificationListener.cancelNotification(sbn.getKey());
            }
            dissmiss();
        });
        mNotificationRv.setAdapter(mNotificationAdapter);

        Display display = context.getDisplay();
        Context c = display.getDisplayId() == Display.DEFAULT_DISPLAY
                ? context.getApplicationContext()
                : context.getApplicationContext().createDisplayContext(display);
        mWifiManager = context.getSystemService(WifiManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mTelephonyManager = mTelephonyManager.createForSubscriptionId(
            SubscriptionManager.getDefaultSubscriptionId());
        mAudioManager = context.getSystemService(AudioManager.class);
        mBluetoothManager = context.getSystemService(BluetoothManager.class);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback, mHandler);

        mReceiver = new SlideBarBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver,filter);

        mTelephonyCallback = new SimTelephonyCallback();
        mTelephonyManager.registerTelephonyCallback(context.getMainExecutor(),mTelephonyCallback);
        updateVoluemSeekbar();
        mAudioManager.registerVolumeGroupCallback(Executors.MAIN_EXECUTOR, mVolumeGroupCallback);
        mVolumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        updateWifiStatus();
        updateCellularStatus();
        boolean enabled = BluetoothAdapter.getDefaultAdapter().isEnabled();
        updateBluetoothStatus(enabled);
        updateSoundStatus();
        updateDesktopStatus();

        NotificationListener.setSecondaryNotificationsChangeListener(new SecondaryNotificationsChangeListener(){
            @Override
            public void onNotificationPosted(StatusBarNotification sbn){
                if(sbn !=null && mNotificationAdapter != null){
                    mNotificationAdapter.addNotification(sbn);
                }
            }
            @Override
            public void onNotificationRemoved(StatusBarNotification sbn) {
                if(sbn !=null && mNotificationAdapter != null){
                    mNotificationAdapter.removeNotification(sbn);
                }
            }
            @Override
            public void onNotificationFullRefresh(List<StatusBarNotification> activeNotifications){
                if(activeNotifications != null && mNotificationAdapter != null) {
                    mNotificationAdapter.setNotificationList(activeNotifications);
                }

            }
        });
    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_SLIDEBAR) != 0;
    }

    @Override
    public void show() {
        if(isShow()){
            return;
        }
        int height = mActivity.getDisplay().getHeight() - dpToPx(16);
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
        mWindowLayoutParams.gravity = Gravity.RIGHT|Gravity.TOP;
        mWindowLayoutParams.windowAnimations = R.style.slidebar_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

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
    public void onDestroy(){
        if(mScreenshotView != null){
            mScreenshotView.onDestroy();
        }
        mActivity = null;
        mContext = null;
        unregisterReceiver(mReceiver);
        mHandler.removeCallbacksAndMessages(null);
        mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
        mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        mAudioManager.unregisterVolumeGroupCallback(mVolumeGroupCallback);
        NotificationListener.removeSecondaryNotificationsChangeListener();
    }

    private void clearAll(View v){
        NotificationListener notificationListener = NotificationListener.getInstanceIfConnected();
        if(notificationListener != null) {
            notificationListener.cancelAllNotifications();
            NotificationListener.refreshFullNotification();
        }
    }

    private void screenShot(View v){
        if(mActivity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED){
            dissmiss();
            mActivity.requestPermissions(new String[]{ Manifest.permission.READ_MEDIA_IMAGES },100);
            return;
        }
        dissmiss();
        mHandler.postDelayed(()->{
            ScreenshotUtils.takeScreenShot(mActivity,this::showScreenshot);
        },300);
    }

    private void wifiSwitch(View v) {
        int wifiState = mWifiManager.getWifiState();
        boolean isEnable = wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_ENABLING;
        mWifiManager.setWifiEnabled(!isEnable);
        mHandler.postDelayed(()->{
            updateWifiStatus();
        },500);
    }

    private void bluetoothSwitch(View v){
        if(mActivity.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                || mActivity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || mActivity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED){
            dissmiss();
            mActivity.requestPermissions(new String[]{ Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},101);
            return;
        }
        boolean enabled = BluetoothAdapter.getDefaultAdapter().isEnabled();

        if(enabled){
            BluetoothAdapter.getDefaultAdapter().disable();
            updateBluetoothStatus(false);
        } else {
            BluetoothAdapter.getDefaultAdapter().enable();
            updateBluetoothStatus(true);
        }
    }

    private void cellularSwitch(View v) {
        if(mTelephonyManager.getSimState() != SIM_STATE_READY){
            updateCellularStatus();
            return;
        }
        boolean dataEnabled = mTelephonyManager.isDataEnabled();
        mTelephonyManager.setDataEnabled(!dataEnabled);
    }

    private void soundSwitch(View v) {
        boolean enable = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        mAudioManager.setRingerMode(enable ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
        updateSoundStatus();
    }

    private void desktopModeSwitch(View v) {
        boolean desktopOn = Settings.Global.getInt(getContentResolver(),
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,0) == 1 
                && Settings.Global.getInt(getContentResolver(),
                DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) ==1;
        Settings.Global.putInt(getContentResolver(),DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, desktopOn ? 0 : 1);
        Settings.Global.putInt(getContentResolver(),DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, desktopOn ? 0 : 1);
        if(desktopOn){
            Toast.makeText(mContext, R.string.desktop_mode_off_info, Toast.LENGTH_SHORT).show();
        }
        updateDesktopStatus();
        dissmiss();
        mHandler.postDelayed(()->{
            SecondaryDisplayLauncher.getLauncher(mActivity).finish();
        },200);
    }

    private void showScreenshot(Uri uri){
        mScreenshotView.showScreenshot(uri);
    }

    private void updateWifiStatus(){
        int wifiState = mWifiManager.getWifiState();
        boolean isEnable = wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_ENABLING;
        mWifiSwitch.setChecked(isEnable);
        mWifiTitle.setTextColor(isEnable ? getResources().getColor(R.color.active_color) :
                getResources().getColor(R.color.inactive_color));
    }

    private void updateBluetoothStatus(boolean enabled){
        mBluetoothSwitch.setChecked(enabled);
        mBluetoothTitle.setTextColor(enabled ? getResources().getColor(R.color.active_color) :
                getResources().getColor(R.color.inactive_color));
    }

    private void updateCellularStatus(){
        boolean isDataEnabled = mTelephonyManager.isDataEnabled();
        mCellularSwitch.setChecked(isDataEnabled);
        mCellularTitle.setTextColor(isDataEnabled ? getResources().getColor(R.color.active_color) :
                getResources().getColor(R.color.inactive_color));
    }

    private void updateSoundStatus(){
        boolean enable = mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        mSoundSwitch.setChecked(enable);
        mSoundTitle.setTextColor(enable ? getResources().getColor(R.color.active_color) :
                getResources().getColor(R.color.inactive_color));
    }

    private void updateDesktopStatus(){
        boolean enbled = Settings.Global.getInt(getContentResolver(),
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 0) == 1
                && Settings.Global.getInt(getContentResolver(),
                DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) ==1;
        mDesktopMode.setChecked(enbled);
        mDesktopModeTitle.setTextColor(enbled ? getResources().getColor(R.color.active_color) :
                getResources().getColor(R.color.inactive_color));
    }

    private void updateVoluemSeekbar(){
        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int min = mAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        int progress = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mVolumeSeekbar.setMax(max);
        mVolumeSeekbar.setMin(min);
        mVolumeSeekbar.setProgress(progress);
    }
    private final AudioManager.VolumeGroupCallback mVolumeGroupCallback =
              new AudioManager.VolumeGroupCallback() {
        @Override
        public void onAudioVolumeGroupChanged(int group, int flags) {
            updateVoluemSeekbar();
            updateSoundStatus();
        }
    };

    private final NetworkCallback mDefaultNetworkCallback =
            new NetworkCallback(NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                updateWifiStatus();
            }
        }
        @Override
        public void onLost(Network network) {
            updateWifiStatus();
        }
    };

    private class SlideBarBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                if(BluetoothAdapter.STATE_ON == state){
                    updateBluetoothStatus(true);
                } else if(BluetoothAdapter.STATE_OFF == state){
                    updateBluetoothStatus(false);
                }
            }
        }
    }

    private class SimTelephonyCallback extends TelephonyCallback implements TelephonyCallback.DataConnectionStateListener{

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            updateCellularStatus();
        }
    }
}
