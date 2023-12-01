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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.config.FeatureFlags.ENABLE_MATERIAL_U_POPUP;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;
import android.content.Intent;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.Color;
import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.SparseArray;
import android.view.DragEvent;
import android.content.ClipData;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import com.android.systemui.shared.system.PackageManagerWrapper;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.secondarydisplay.SecondaryOptionsPopupView;
import com.android.launcher3.secondarydisplay.PinTaskBarShortcut;
import com.android.launcher3.secondarydisplay.DesktopIconKey;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.Executors;
import static com.android.launcher3.Utilities.dpToPx;

import java.util.Iterator;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DragLayer for Secondary launcher
 */
public class SecondaryDragLayer extends BaseDragLayer<SecondaryDisplayLauncher> implements OnSharedPreferenceChangeListener{

    private View mAllAppsButton;
    private ActivityAllAppsContainerView<SecondaryDisplayLauncher> mAppsView;

    private PinnedAppsAdapter mPinnedAppsAdapter;
    private GridLayout mWorkspaceLayout;
    private ImageView mIvArrange;
    private int mColumns;
    private int mRows;
    private AllAppsStore mAllAppsList;
    private static final String PINNED_DESKTOP_APPS_KEY = "pinned_desktop_apps";
    private static final String PINNED_TASKBAR_APPS_KEY = "pinned_taskbar_apps";
    private final SharedPreferences mDesktopPrefs;
    private final Set<DesktopIconKey> mDesktopApps = new HashSet<>();
    private final SparseArray<ComponentKey> mDesktops = new SparseArray<>();
    private SharedPreferences mTaskbarPrefs;
    private final Set<ComponentKey> mTaskbarPinnedApps = new HashSet<>();
    private int mLastPos = -1;
    private boolean iconArrangeMode = false;
    private int startDragIndex = -1;
    private int endDragIndex = -1;
    static final int REQUEST_UNINSTALL = 77;

    public SecondaryDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        recreateControllers();
        mDesktopPrefs = context.getSharedPreferences(PINNED_DESKTOP_APPS_KEY, MODE_PRIVATE);
        mTaskbarPrefs = context.getSharedPreferences(PINNED_TASKBAR_APPS_KEY,MODE_PRIVATE);
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[]{new CloseAllAppsTouchController(),
                mActivity.getDragController()};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAllAppsButton = findViewById(R.id.all_apps_button);

        mAppsView = findViewById(R.id.apps_view);
        mAppsView.setOnIconLongClickListener(this::onIconLongClicked);
        mActivity.getSecondaryDisplayPredictions()
                .setLongClickListener(mAppsView, this::onIconLongClicked);
        // Setup workspace
        mPinnedAppsAdapter = new PinnedAppsAdapter(mActivity, mAppsView.getAppsStore(),
                this::onIconLongClicked);
        mAllAppsList = mAppsView.getAppsStore();
        mAllAppsList.addUpdateListener(this::onAppsUpdated);
        mWorkspaceLayout = findViewById(R.id.workspace_layout);
        mIvArrange = findViewById(R.id.iv_arrange);
        mIvArrange.setOnClickListener(view ->{
            arrangeDone();
        });
        int screenWidth = mActivity.getDisplay().getWidth();
        int screenHeight = mActivity.getDisplay().getHeight();
        int desktopIconSize = mActivity.getResources().getDimensionPixelSize(R.dimen.secondary_desktop_grid_width);
        mColumns = screenWidth  / desktopIconSize;
        mRows = (screenHeight - dpToPx(100)) / desktopIconSize;
        mWorkspaceLayout.setOrientation(GridLayout.VERTICAL);
        mWorkspaceLayout.setColumnCount(mColumns);
        mWorkspaceLayout.setRowCount(mRows);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDesktopPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mDesktopPrefs, PINNED_DESKTOP_APPS_KEY);
        mTaskbarPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mTaskbarPrefs, PINNED_TASKBAR_APPS_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDesktopPrefs.unregisterOnSharedPreferenceChangeListener(this);
        mTaskbarPrefs.unregisterOnSharedPreferenceChangeListener(this);
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
                        .map(this::parseDesktopIconKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mDesktopApps.clear();
                    mDesktopApps.addAll(apps);
                    mDesktops.clear();
                    for(DesktopIconKey desktopKey: mDesktopApps){
                        mDesktops.put(desktopKey.getPos(),desktopKey.getComponentKey());
                    }
                    if(mDesktops.size() > 0){
                        mLastPos = mDesktops.keyAt(mDesktops.size() -1);
                    }
                    refreshDesktopIcons();
                });
            });
        }else if (PINNED_TASKBAR_APPS_KEY.equals(key)){
            Executors.MODEL_EXECUTOR.submit(() -> {
                Set<ComponentKey> apps = prefs.getStringSet(key, Collections.emptySet())
                        .stream()
                        .map(this::parseComponentKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mTaskbarPinnedApps.clear();
                    mTaskbarPinnedApps.addAll(apps);
                });
            });
        }
    }

    private void updateDesktop(ItemInfo info, boolean isToAdd) {
        ComponentKey componentKey = new ComponentKey(info.getTargetComponent(), info.user);
        int pos = 0;
        if(isToAdd){
            mLastPos++;
            pos = mLastPos;
            if(pos >= (mRows * mColumns)){
                return;
            }
        } else {
            for(int i =0; i<mDesktops.size();i++){
                int key = mDesktops.keyAt(i);
                ComponentKey temp = mDesktops.get(key);
                if(componentKey.equals(temp)){
                    pos = key;
                    break;
                }
            }
        }
        DesktopIconKey desktopKey = new DesktopIconKey(componentKey,pos);
        if(isToAdd){
            mDesktopApps.add(desktopKey);
        }else {
            boolean remove = mDesktopApps.remove(desktopKey);
        }
        Set<DesktopIconKey> copy = new HashSet<>(mDesktopApps);
        Executors.MODEL_EXECUTOR.submit(() ->
                mDesktopPrefs.edit().putStringSet(PINNED_DESKTOP_APPS_KEY,
                    copy.stream().map(this::encodeDesktop).collect(Collectors.toSet()))
                    .apply());
    }

    private String encodeDesktop(DesktopIconKey key) {
        return key.getComponentKey().componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(mActivity).getSerialNumberForUser(key.getComponentKey().user)+"#"+key.getPos();
    }

    private ComponentKey parseComponentKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user= Process.myUserHandle();
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            return new ComponentKey(cn, user);
        } catch (Exception e) {
            return null;
        }
    }

    private DesktopIconKey parseDesktopIconKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user= Process.myUserHandle();
            int pos = 0;
            if(parts.length == 3){
                pos = Integer.parseInt(parts[2]);
            }
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            ComponentKey key = new ComponentKey(cn, user);
            return new DesktopIconKey(key,pos);
        } catch (Exception e) {
            return null;
        }
    }

    public void sortDesktopIcon(){
        List<AppInfo> appList = new ArrayList<>();
        for(DesktopIconKey iconKey : mDesktopApps) {
            if(iconKey.getComponentKey() !=null){
                AppInfo appInfo = mAllAppsList.getApp(iconKey.getComponentKey());
                if(appInfo != null) {
                    appList.add(appInfo);
                }
            }
        }
        appList.sort(AppInfo.TITLE_COMPARATOR);
        for(int i=0;i<appList.size();i++){
            AppInfo info = appList.get(i);
            //temp
            for(DesktopIconKey key : mDesktopApps) {
                if(key.getComponentKey().componentName.equals(info.getTargetComponent())){
                    key.setPos(i);
                    break;
                }
            }
        }
        Set<DesktopIconKey> copy = new HashSet<>(mDesktopApps);
        Executors.MODEL_EXECUTOR.submit(() ->
                mDesktopPrefs.edit().putStringSet(PINNED_DESKTOP_APPS_KEY,
                    copy.stream().map(this::encodeDesktop).collect(Collectors.toSet()))
                    .apply());
    }

    public void startArrangeMode(){
        iconArrangeMode = true;
        mIvArrange.setVisibility(View.VISIBLE);
    }

    private void arrangeDone() {
        iconArrangeMode = false;
        mIvArrange.setVisibility(View.GONE);
        if(startDragIndex == endDragIndex){
            return;
        }
        ComponentKey key = mDesktops.get(startDragIndex);
        DesktopIconKey oldKey = new DesktopIconKey(key,startDragIndex);
        DesktopIconKey newKey = new DesktopIconKey(key,endDragIndex);
        mDesktopApps.remove(oldKey);
        mDesktopApps.add(newKey);
        Set<DesktopIconKey> copy = new HashSet<>(mDesktopApps);
        Executors.MODEL_EXECUTOR.submit(() ->
                mDesktopPrefs.edit().putStringSet(PINNED_DESKTOP_APPS_KEY,
                    copy.stream().map(this::encodeDesktop).collect(Collectors.toSet()))
                    .apply());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        DeviceProfile grid = mActivity.getDeviceProfile();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child == mAppsView) {
                int horizontalPadding = (2 * grid.desiredWorkspaceHorizontalMarginPx)
                        + grid.cellLayoutPaddingPx.left + grid.cellLayoutPaddingPx.right;
                int verticalPadding =
                        grid.cellLayoutPaddingPx.top + grid.cellLayoutPaddingPx.bottom;

                int maxWidth =
                        grid.allAppsCellWidthPx * grid.numShownAllAppsColumns + horizontalPadding;
                int appsWidth = Math.min(width - getPaddingLeft() - getPaddingRight(), maxWidth);

                int maxHeight =
                        grid.allAppsCellHeightPx * grid.numShownAllAppsColumns + verticalPadding;
                int appsHeight = Math.min(height - getPaddingTop() - getPaddingBottom(), maxHeight);

                mAppsView.measure(
                        makeMeasureSpec(appsWidth, EXACTLY), makeMeasureSpec(appsHeight, EXACTLY));
            } else if (child == mAllAppsButton) {
                int appsButtonSpec = makeMeasureSpec(grid.iconSizePx, EXACTLY);
                mAllAppsButton.measure(appsButtonSpec, appsButtonSpec);
            } else if (child == mWorkspaceLayout) {
                measureChildWithMargins(mWorkspaceLayout, widthMeasureSpec, 0, heightMeasureSpec,
                        grid.iconSizePx + grid.edgeMarginPx);
            } else {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
        }
    }

    private class CloseAllAppsTouchController implements TouchController {

        @Override
        public boolean onControllerTouchEvent(MotionEvent ev) {
            return false;
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            if (!mActivity.isAppDrawerShown()) {
                return false;
            }

            if (AbstractFloatingView.getTopOpenView(mActivity) != null) {
                return false;
            }

            if (ev.getAction() == MotionEvent.ACTION_DOWN
                    && !isEventOverView(mActivity.getAppsView(), ev)) {
                mActivity.showAppDrawer(false);
                return true;
            }
            return false;
        }
    }

    public PinnedAppsAdapter getPinnedAppsAdapter() {
        return mPinnedAppsAdapter;
    }

    private boolean onIconLongClicked(View v) {
        if (!(v instanceof BubbleTextView)) {
            return false;
        }
        if (PopupContainerWithArrow.getOpen(mActivity) != null) {
            // There is already an items container open, so don't open this one.
            v.clearFocus();
            return false;
        }
        ItemInfo item = (ItemInfo) v.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return false;
        }
        PopupDataProvider popupDataProvider = mActivity.getPopupDataProvider();
        if (popupDataProvider == null) {
            return false;
        }
        final PopupContainerWithArrow container =
                (PopupContainerWithArrow) mActivity.getLayoutInflater().inflate(
                        R.layout.popup_container, mActivity.getDragLayer(), false);

        boolean pinnedDesktop = false;
        ComponentKey key = new ComponentKey(item.getTargetComponent(), item.user);
        for(DesktopIconKey desktopKey : mDesktopApps){
            if(desktopKey.getComponentKey().componentName != null &&
                    key.equals(desktopKey.getComponentKey())){
                pinnedDesktop = true;
                break;
            }
        }

        List<SystemShortcut> shortcutList = new ArrayList<>();
        shortcutList.add(new PinTaskBarShortcut(mActivity,item,mTaskbarPinnedApps.contains(key),v));
        shortcutList.add(new PinDesktopShortcut(mActivity,item,pinnedDesktop,v));
        shortcutList.add(APP_INFO.getShortcut(mActivity, item, v));
        ActivityInfo activityInfo = PackageManagerWrapper.getInstance().getActivityInfo(
                item.getTargetComponent(), item.user.myUserId());
        if(activityInfo !=null){
            if((activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0){
                shortcutList.add(new UninstallShortcut(mActivity,item,v));
            }
        }
        
        container.populateAndShow((BubbleTextView) v,
                0/*mActivity.getPopupDataProvider().getShortcutCountForItem(item)*/,
                Collections.emptyList(),
                shortcutList);
        v.getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    private boolean onIconMotionClicked(View v,MotionEvent e){
        int action = e.getAction();

        if(action == MotionEvent.ACTION_BUTTON_PRESS
                && e.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
            onIconLongClicked(v);
        }
        return false;
    }

    private void refreshDesktopIcons(){
        mWorkspaceLayout.removeAllViews();
        for(int i =0;i< mColumns * mRows;i++){
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f));
            params.width = 0;
            params.height = 0;

            FrameLayout iconContainer = new FrameLayout(mActivity.getBaseContext());
            iconContainer.setLayoutParams(params);
            iconContainer.setOnDragListener(new DesktopIconDragListener());
            int index = i;
            ComponentKey key = mDesktops.get(index);
            if(key != null){
                AppInfo info = mAllAppsList.getApp(key);
                if(info != null){
                    BubbleTextView icon = (BubbleTextView) mActivity.getLayoutInflater().inflate(R.layout.app_icon,iconContainer,false);
                    icon.setTextColor(Color.WHITE);
                    icon.applyFromApplicationInfo(info);
                    icon.setOnClickListener(mActivity.getItemOnClickListener());
                    icon.setOnLongClickListener(this::onIconLongClicked);
                    icon.setOnGenericMotionListener((view, motionEvent) -> {
                        int action = motionEvent.getAction();

                        if(action == MotionEvent.ACTION_BUTTON_PRESS
                                && motionEvent.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                            onIconLongClicked(view);
                        }
                        return false;
                    });
                    icon.setOnTouchListener(new DesktopIconTouchListener());
                    iconContainer.addView(icon);
                }
            }
            if(iconContainer.getChildCount() == 0){
                iconContainer.setOnGenericMotionListener((view, motionEvent)  ->{
                    int action = motionEvent.getAction();

                    if( !mActivity.isAppDrawerShown() && action == MotionEvent.ACTION_BUTTON_PRESS
                            && motionEvent.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                        int childIndex = mWorkspaceLayout.indexOfChild(view);
                        int desktopIconSize =  mActivity.getResources().getDimensionPixelSize(R.dimen.secondary_desktop_grid_width);
                        float x = ((childIndex+1)/mRows) * desktopIconSize;
                        float y = (((childIndex+1)%mRows) == 0 ? mRows * desktopIconSize : ((childIndex+1)%mRows) * desktopIconSize) +
                                    motionEvent.getAxisValue(MotionEvent.AXIS_Y);
                        SecondaryOptionsPopupView.showDefaultOptions(mActivity,x,y);
                    }
                    return false;
                });
            }
            mWorkspaceLayout.addView(iconContainer);
        }
    }

    private final class DesktopIconTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if(iconArrangeMode && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                startDragIndex = mWorkspaceLayout.indexOfChild((ViewGroup) view.getParent());

                ClipData data = ClipData.newPlainText("", "");
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                view.startDragAndDrop(data, shadowBuilder, view, 0);
                //view.setVisibility(View.INVISIBLE);
                return true;
            } else
                return false;
        }
    }

    private final class DesktopIconDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            switch(event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                default:
                    // do nothing
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    FrameLayout container = (FrameLayout) v;
                    if(container.getChildCount() == 0
                            || startDragIndex == mWorkspaceLayout.indexOfChild(container)) {
                        v.setAlpha(0.5f);
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    View view = (View) event.getLocalState();
                    if(view != null) view.setVisibility(View.VISIBLE);
                    // fall through
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setAlpha(1);
                    break;
                case DragEvent.ACTION_DROP:
                    FrameLayout container2 = (FrameLayout) v;
                    if(container2.getChildCount() == 0) {
                        View view2 = (View) event.getLocalState();
                        ViewGroup owner = (ViewGroup) view2.getParent();
                        owner.removeView(view2);
                        container2.addView(view2);
                        endDragIndex = mWorkspaceLayout.indexOfChild(container2);                    }
                    break;
            }
            return true;
        }
    }

    private class PinDesktopShortcut extends SystemShortcut<SecondaryDisplayLauncher> {

        private final boolean mIsPinned;

        PinDesktopShortcut(SecondaryDisplayLauncher target, ItemInfo info, boolean isPinned, View originalView) {
            super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                    isPinned ? R.string.remove_drop_target_label : R.string.action_add_to_workspace,
                    target, info, originalView);
            mIsPinned = isPinned;
        }

        @Override
        public void onClick(View view) {
            updateDesktop(mItemInfo, !mIsPinned);
            AbstractFloatingView.closeAllOpenViews(mActivity);
        }
    }

    private class UninstallShortcut extends SystemShortcut<SecondaryDisplayLauncher> {
        UninstallShortcut(SecondaryDisplayLauncher target, ItemInfo info, View originalView) {
            super(R.drawable.ic_detele_all,R.string.uninstall_drop_target_label,target, info, originalView);
        }

        @Override
        public void onClick(View view) {
            uninstallPkg(mItemInfo);
            AbstractFloatingView.closeAllOpenViews(mActivity);
        }
    }

    private void uninstallPkg(ItemInfo info){
        final Uri packageURI = Uri.parse("package:" + info.getTargetComponent().getPackageName());
        final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
        mActivity.startActivityForResult(uninstallIntent, REQUEST_UNINSTALL);
    }

    private void updateUninstall(){
        Iterator<DesktopIconKey> iterator = mDesktopApps.iterator();
        boolean removeUninstall = false;
        while(iterator.hasNext()){
            DesktopIconKey key = iterator.next();
            AppInfo info = mAllAppsList.getApp(key.getComponentKey());
            if(info == null){
                removeUninstall = true;
                iterator.remove();
            }
        }
        if(removeUninstall){
            Set<DesktopIconKey> copy = new HashSet<>(mDesktopApps);
            Executors.MODEL_EXECUTOR.submit(() ->
                    mDesktopPrefs.edit().putStringSet(PINNED_DESKTOP_APPS_KEY,
                        copy.stream().map(this::encodeDesktop).collect(Collectors.toSet()))
                        .apply());
        }
    }

    private void onAppsUpdated() {
        updateUninstall();
    }
}
