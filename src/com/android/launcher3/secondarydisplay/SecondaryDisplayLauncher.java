/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;
import static com.android.launcher3.secondarydisplay.wallpaper.SecondaryWallpaperPicker.SECONDARY_WALLPAPER;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.Utilities.dpToPx;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.content.Context;
import androidx.annotation.UiThread;
import android.view.WindowManager;
import android.graphics.Color;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.provider.Settings;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.touch.ItemClickHandler.ItemClickProxy;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.secondarydisplay.SecondaryOptionsPopupView;
import com.android.launcher3.secondarydisplay.SecondaryTaskBarView;
import com.android.launcher3.secondarydisplay.SecondaryTaskBarListner;
import com.android.launcher3.secondarydisplay.SpaceItemDecoration;
import com.android.launcher3.secondarydisplay.SecondarySlideBarView;
import com.android.launcher3.secondarydisplay.SecondaryRecentsView;
import com.android.launcher3.secondarydisplay.CalendarMonthView;
import com.android.launcher3.secondarydisplay.OverlayManager;
import com.android.launcher3.secondarydisplay.SecondaryRecentsListener;
import android.service.notification.StatusBarNotification;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;

/**
 * Launcher activity for secondary displays
 */
public class SecondaryDisplayLauncher extends BaseDraggingActivity
        implements BgDataModel.Callbacks, DragController.DragListener, SecondaryTaskBarListner,OnSharedPreferenceChangeListener {

    private LauncherModel mModel;
    private BaseDragLayer mDragLayer;
    private SecondaryDragController mDragController;
    private ActivityAllAppsContainerView<SecondaryDisplayLauncher> mAppsView;
    private View mAppsButton;
    private SecondaryDisplayLauncher mLauncher;

    private PopupDataProvider mPopupDataProvider;

    private boolean mAppDrawerShown = false;

    private StringCache mStringCache;
    private OnboardingPrefs<?> mOnboardingPrefs;
    private boolean mBindingItems = false;
    private SecondaryDisplayPredictions mSecondaryDisplayPredictions;

    private final int[] mTempXY = new int[2];
    private SecondaryTaskBarView mTaskBarView;
    private SecondarySlideBarView mSlideBar;
    private SecondaryRecentsListener mRecentsListener;
    private LauncherApps mLauncherApps;
    private SecondaryRecentsView mRecentsView;
    private SecondaryAllAppsView mAllApps;
    private CalendarMonthView mCalendarView;
    private SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel = LauncherAppState.getInstance(this).getModel();
        mLauncher = this;
        mDragController = new SecondaryDragController(this);
        mOnboardingPrefs = new OnboardingPrefs<>(this, LauncherPrefs.getPrefs(this));
        mSecondaryDisplayPredictions = SecondaryDisplayPredictions.newInstance(this);
        if (getWindow().getDecorView().isAttachedToWindow()) {
            initUi();
        }
        hideSystemUI();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        initUi();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        this.getDragController().removeDragListener(this);
    }

    private void initUi() {
        if (mDragLayer != null) {
            return;
        }
        InvariantDeviceProfile currentDisplayIdp = new InvariantDeviceProfile(
                this, getWindow().getDecorView().getDisplay());

        // Disable transpose layout and use multi-window mode so that the icons are scaled properly
        mDeviceProfile = currentDisplayIdp.getDeviceProfile(this)
                .toBuilder(this)
                .setMultiWindowMode(true)
                .setTransposeLayoutWithOrientation(false)
                .build();
        mDeviceProfile.autoResizeAllAppsCells();

        setContentView(R.layout.secondary_launcher);
        mDragLayer = findViewById(R.id.drag_layer);
        GestureDetector detector =new GestureDetector(this, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if(!isAppDrawerShown()){
                    float x = e.getAxisValue(MotionEvent.AXIS_X);
                    float y = e.getAxisValue(MotionEvent.AXIS_Y);
                    SecondaryOptionsPopupView.showDefaultOptions(mLauncher,x,y);
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return false;
            }
        });
        mDragLayer.setOnTouchListener((v,event)->{
            if(detector!=null){
                detector.onTouchEvent(event);
            }
            return false;
        });
        mAppsView = findViewById(R.id.apps_view);
        mAppsButton = findViewById(R.id.all_apps_button);

        mDragController.addDragListener(this);
        mPopupDataProvider = new PopupDataProvider(
                mAppsView.getAppsStore()::updateNotificationDots);

        mModel.addCallbacksAndLoad(this);

        getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        mDragLayer.setBackground(getResources().getDrawable(R.drawable.wallpaper_02));
        mSharedPreferences = LauncherPrefs.getPrefs(this);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mSharedPreferences, SECONDARY_WALLPAPER);
        mLauncherApps = getSystemService(LauncherApps.class);
        int displayId = getDisplay().getDisplayId();
        mRecentsListener = new SecondaryRecentsListener(MAIN_EXECUTOR, ActivityManagerWrapper.getInstance(),
                mLauncherApps, displayId, SecondarySystemUIProxy.INSTANCE.get(this));
        mTaskBarView = new SecondaryTaskBarView(this, mAppsView.getAppsStore(), this);
        mTaskBarView.show();
        mSlideBar = new SecondarySlideBarView(this);
        mRecentsView = new SecondaryRecentsView(this);
        mAllApps = new SecondaryAllAppsView(this);
        mCalendarView = new CalendarMonthView(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDragController.cancelDrag();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // Hide keyboard.
            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(
                        v.getWindowToken(), 0);
            }
        }

        // A new intent will bring the launcher to top. Hide the app drawer to reset the state.
        showAppDrawer(false);
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void onBackPressed() {
        if (finishAutoCancelActionMode()) {
            return;
        }

        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
            return;
        }

        // Note: There should be at most one log per method call. This is enforced implicitly
        // by using if-else statements.
        AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
        if (topView != null && topView.canHandleBack()) {
            // Handled by the floating view.
            topView.onBackInvoked();
        } else {
            showAppDrawer(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mSharedPreferences != null){
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        OverlayManager.getInstance().release();
        if(mCalendarView != null){
            mCalendarView.onDestroy();
        }
        if(mAllApps != null){
            mAllApps.onDestroy();
        }
        if(mRecentsListener != null) {
            mRecentsListener.onDestory();
        }
        if(mTaskBarView != null){
            mTaskBarView.onDestroy();
        }
        if(mSlideBar != null) {
            mSlideBar.onDestroy();
        }
        if(mRecentsView != null) {
            mRecentsView.onDestroy();
        }
        mModel.removeCallbacks(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (SECONDARY_WALLPAPER.equals(key)) {
            String path = prefs.getString(key,null);
            if(path != null){
                if(path.endsWith("png")){
                    UI_HELPER_EXECUTOR.execute(()->{
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        MAIN_EXECUTOR.execute(()->{
                            if(bitmap != null){
                                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
                                bitmapDrawable.setFilterBitmap(true);
                                mDragLayer.setBackground(bitmapDrawable);
                            }
                        });
                    });
                } else {
                    mDragLayer.setBackground(getResources().getDrawable(Integer.parseInt(path)));
                }
            }
        }
    }

    @Override
    public void onStartButtonClick(){
        if(mAllApps.isShow()){
            mAllApps.dissmiss();
        } else {
            mAllApps.show();
        }
        //showAppDrawer(!isAppDrawerShown());
    }

    @Override
    public void onStatusBarClick(){
        if(mSlideBar.isShow()){
            mSlideBar.dissmiss();
        }else {
            mSlideBar.show();
        }
    }

    @Override
    public void onIconClick(View v,ItemInfo info, int taskId) {
        Intent intent = info.getIntent();
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        if(taskId == -1){
            startActivitySafely(v, intent, info);
        } else {
            ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId, null);
        }
    }

    @Override
    public void onRecentClick(){
        if(mRecentsView.isShow()){
            mRecentsView.dissmiss();
        } else {
            mRecentsView.show();
        }
    }

    @Override
    public void onHomeClick(){
        if(mRecentsView.isShow()){
            mRecentsView.dissmiss();
        }
        if(isDesktopModeOn()){
            navigateHome();
        }
    }

    @Override
    public void onCalendarClick(){
        if(mCalendarView.isShow()){
            mCalendarView.dissmiss();
        } else {
            if(mSlideBar.isShow()){
                mSlideBar.dissmiss();
            }
            mCalendarView.show();
        }
    }

    private boolean isDesktopModeOn(){
        boolean desktopOn = Settings.Global.getInt(getContentResolver(),
                DEVELOPMENT_FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS,0) == 1;
        return desktopOn;
    }

    private void navigateHome() {
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_SECONDARY_HOME));
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    public SecondaryRecentsListener getSecondaryRecentsListener(){
        return mRecentsListener;
    }

    public boolean isAppDrawerShown() {
        return mAppDrawerShown;
    }

    @Override
    public ActivityAllAppsContainerView<SecondaryDisplayLauncher> getAppsView() {
        return mAppsView;
    }

    @Override
    public <T extends View> T getOverviewPanel() {
        return null;
    }

    @Override
    public View getRootView() {
        return mDragLayer;
    }

    @Override
    protected void reapplyUi() { }

    @Override
    public BaseDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public void bindIncrementalDownloadProgressUpdated(AppInfo app) {
        mAppsView.getAppsStore().updateProgressBar(app);
    }

    /**
     * Called when apps-button is clicked
     */
    public void onAppsButtonClicked(View v) {
        showAppDrawer(true);
    }

    /**
     * Show/hide app drawer card with animation.
     */
    public void showAppDrawer(boolean show) {
        if (show == mAppDrawerShown) {
            return;
        }

        float openR = (float) Math.hypot(mAppsView.getWidth(), mAppsView.getHeight());
        float closeR = Themes.getDialogCornerRadius(this);
        float startR = mAppsButton.getWidth() / 2f;

        float[] buttonPos = new float[]{startR, startR};
        mDragLayer.getDescendantCoordRelativeToSelf(mAppsButton, buttonPos);
        mDragLayer.mapCoordInSelfToDescendant(mAppsView, buttonPos);
        final Animator animator = ViewAnimationUtils.createCircularReveal(mAppsView,
                (int) buttonPos[0], (int) buttonPos[1],
                show ? closeR : openR, show ? openR : closeR);

        if (show) {
            mAppDrawerShown = true;
            mAppsView.setVisibility(View.VISIBLE);
            mAppsButton.setVisibility(View.GONE);
            mSecondaryDisplayPredictions.updateAppDivider();
        } else {
            mAppDrawerShown = false;
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mAppsView.setVisibility(View.INVISIBLE);
                    mAppsButton.setVisibility(View.GONE);
                    mAppsView.getSearchUiManager().resetSearch();
                }
            });
        }
        animator.start();
    }

    @Override
    public OnboardingPrefs<?> getOnboardingPrefs() {
        return mOnboardingPrefs;
    }

    @Override
    public void startBinding() {
        mBindingItems = true;
        mDragController.cancelDrag();
    }

    @Override
    public boolean isBindingItems() {
        return mBindingItems;
    }

    @Override
    public void finishBindingItems(IntSet pagesBoundFirst) {
        mBindingItems = false;
    }

    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMap);
    }

    @UiThread
    @Override
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        Preconditions.assertUIThread();
        AllAppsStore appsStore = mAppsView.getAppsStore();
        appsStore.setApps(apps, flags, packageUserKeytoUidMap);
        PopupContainerWithArrow.dismissInvalidPopup(this);
    }

    @Override
    public void bindExtraContainerItems(BgDataModel.FixedContainerItems item) {
        if (item.containerId == LauncherSettings.Favorites.CONTAINER_PREDICTION) {
            mSecondaryDisplayPredictions.setPredictedApps(item);
        }
    }

    public SecondaryDisplayPredictions getSecondaryDisplayPredictions() {
        return mSecondaryDisplayPredictions;
    }

    @Override
    public StringCache getStringCache() {
        return mStringCache;
    }

    @Override
    public void bindStringCache(StringCache cache) {
        mStringCache = cache;
    }

    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public OnClickListener getItemOnClickListener() {
        return this::onIconClicked;
    }

    private void onIconClicked(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) return;

        Object tag = v.getTag();
        if (tag instanceof ItemClickProxy) {
            ((ItemClickProxy) tag).onItemClicked(v);
        } else if (tag instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) tag;
            Intent intent;
            if (item instanceof ItemInfoWithIcon
                    && (((ItemInfoWithIcon) item).runtimeStatusFlags
                    & ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                ItemInfoWithIcon appInfo = (ItemInfoWithIcon) item;
                intent = appInfo.getMarketIntent(this);
            } else {
                intent = item.getIntent();
            }
            if (intent == null) {
                throw new IllegalArgumentException("Input must have a valid intent");
            }
            startActivitySafely(v, intent, item);
        }
    }

    public static SecondaryDisplayLauncher getLauncher(Context context) {
        return fromContext(context);
    }

    /**
     * Core functionality for beginning a drag operation for an item that will be dropped within
     * the secondary display grid home screen
     */
    public void beginDragShared(View child, DragSource source, DragOptions options) {
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }

    private void beginDragShared(View child, DragSource source,
            ItemInfo dragObject, DragPreviewProvider previewProvider, DragOptions options) {

        float iconScale = 1f;
        if (child instanceof BubbleTextView) {
            FastBitmapDrawable icon = ((BubbleTextView) child).getIcon();
            if (icon != null) {
                iconScale = icon.getAnimatedScale();
            }
        }

        // clear pressed state if necessary
        child.clearFocus();
        child.setPressed(false);
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        DraggableView draggableView = null;
        if (child instanceof DraggableView) {
            draggableView = (DraggableView) child;
        }

        final View contentView = previewProvider.getContentView();
        final float scale;
        // The draggable drawable follows the touch point around on the screen
        final Drawable drawable;
        if (contentView == null) {
            drawable = previewProvider.createDrawable();
            scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        } else {
            drawable = null;
            scale = previewProvider.getScaleAndPosition(contentView, mTempXY);
        }

        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();
        if (draggableView != null) {
            draggableView.getSourceVisualDragBounds(dragRect);
            dragLayerY += dragRect.top;
        }

        if (options.preDragCondition != null) {
            int xOffSet = options.preDragCondition.getDragOffset().x;
            int yOffSet = options.preDragCondition.getDragOffset().y;
            if (xOffSet != 0 && yOffSet != 0) {
                dragLayerX += xOffSet;
                dragLayerY += yOffSet;
            }
        }

        if (contentView != null) {
            mDragController.startDrag(
                    contentView,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    options);
        } else {
            mDragController.startDrag(
                    drawable,
                    draggableView,
                    dragLayerX,
                    dragLayerY,
                    source,
                    dragObject,
                    dragRect,
                    scale * iconScale,
                    scale,
                    options);
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) { }

    @Override
    public void onDragEnd() { }
}
