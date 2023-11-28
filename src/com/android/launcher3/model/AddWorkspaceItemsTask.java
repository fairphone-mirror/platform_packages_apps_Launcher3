/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;

import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInstaller.SessionInfo;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import android.os.SystemProperties;
import android.database.Cursor;
import android.provider.Settings;
import android.database.ContentObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.LauncherSettings.Favorites;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Task to add auto-created workspace items.
 */
public class AddWorkspaceItemsTask extends BaseModelUpdateTask {

    private static final String LOG = "AddWorkspaceItemsTask";

    @NonNull
    private final List<Pair<ItemInfo, Object>> mItemList;

    @NonNull
    private final WorkspaceItemSpaceFinder mItemSpaceFinder;

    //Google Folder AppList {PackageName,Container,ScreenId,Rank}
    private static final String[][] googleAppPackageList = new String[][]{{"com.google.android.apps.subscriptions.red","6","0","9"},{"com.google.android.apps.walletnfcrel","6","0","10"}
    ,{"com.google.android.apps.chromecast.app","6","0","11"}};
    private static final String OPERATOR_APP_LIST_KEY = "def_operator_applist";

    /**
     * @param itemList items to add on the workspace
     */
    public AddWorkspaceItemsTask(@NonNull final List<Pair<ItemInfo, Object>> itemList) {
        this(itemList, new WorkspaceItemSpaceFinder());
    }

    /**
     * @param itemList items to add on the workspace
     * @param itemSpaceFinder inject WorkspaceItemSpaceFinder dependency for testing
     */
    public AddWorkspaceItemsTask(@NonNull final List<Pair<ItemInfo, Object>> itemList,
            @NonNull final WorkspaceItemSpaceFinder itemSpaceFinder) {
        mItemList = itemList;
        mItemSpaceFinder = itemSpaceFinder;
    }

    @Override
    public void execute(@NonNull final LauncherAppState app, @NonNull final BgDataModel dataModel,
            @NonNull final AllAppsList apps) {
        if (mItemList.isEmpty()) {
            return;
        }

        final ArrayList<ItemInfo> addedItemsFinal = new ArrayList<>();
        final IntArray addedWorkspaceScreensFinal = new IntArray();

        synchronized (dataModel) {
            IntArray workspaceScreens = dataModel.collectWorkspaceScreens();

            List<ItemInfo> filteredItems = new ArrayList<>();
            for (Pair<ItemInfo, Object> entry : mItemList) {
                ItemInfo item = entry.first;
                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                        item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                    // Short-circuit this logic if the icon exists somewhere on the workspace
                    if (shortcutExists(dataModel, item.getIntent(), item.user)) {
                        continue;
                    }

                    // b/139663018 Short-circuit this logic if the icon is a system app
                    if (PackageManagerHelper.isSystemApp(app.getContext(),
                            Objects.requireNonNull(item.getIntent()))) {
                        continue;
                    }
                }

                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    if (item instanceof WorkspaceItemFactory) {
                        item = ((WorkspaceItemFactory) item).makeWorkspaceItem(app.getContext());
                    }
                }
                if (item != null) {
                    filteredItems.add(item);
                }
            }

            InstallSessionHelper packageInstaller =
                    InstallSessionHelper.INSTANCE.get(app.getContext());
            LauncherApps launcherApps = app.getContext().getSystemService(LauncherApps.class);

            for (ItemInfo item : filteredItems) {
                // Find appropriate space for the item.
                mItemSpaceFinder.setItemInfo(item);
                int[] coords = mItemSpaceFinder.findSpaceForItem(app, dataModel, workspaceScreens,
                        addedWorkspaceScreensFinal, item.spanX, item.spanY);
                int screenId = coords[0];
                ItemInfo itemInfo;
                if (item instanceof WorkspaceItemInfo || item instanceof FolderInfo ||
                        item instanceof LauncherAppWidgetInfo) {
                    itemInfo = item;
                } else if (item instanceof WorkspaceItemFactory) {
                    itemInfo = ((WorkspaceItemFactory) item).makeWorkspaceItem(app.getContext());
                } else {
                    throw new RuntimeException("Unexpected info type");
                }

                if (item instanceof WorkspaceItemInfo && ((WorkspaceItemInfo) item).isPromise()) {
                    WorkspaceItemInfo workspaceInfo = (WorkspaceItemInfo) item;
                    String packageName = item.getTargetComponent() != null
                            ? item.getTargetComponent().getPackageName() : null;
                    if (packageName == null) {
                        continue;
                    }
                    SessionInfo sessionInfo = packageInstaller.getActiveSessionInfo(item.user,
                            packageName);

                    if (!packageInstaller.verifySessionInfo(sessionInfo)) {
                        FileLog.d(LOG, "Item info failed session info verification. "
                                + "Skipping : " + workspaceInfo);
                        continue;
                    }

                    List<LauncherActivityInfo> activities = Objects.requireNonNull(launcherApps)
                            .getActivityList(packageName, item.user);
                    boolean hasActivity = activities != null && !activities.isEmpty();

                    if (sessionInfo == null) {
                        if (!hasActivity) {
                            // Session was cancelled, do not add.
                            continue;
                        }
                    } else {
                        workspaceInfo.setProgressLevel(
                                (int) (sessionInfo.getProgress() * 100),
                                PackageInstallInfo.STATUS_INSTALLING);
                    }

                    if (hasActivity) {
                        // App was installed while launcher was in the background,
                        // or app was already installed for another user.
                        itemInfo = new AppInfo(app.getContext(), activities.get(0), item.user)
                                .makeWorkspaceItem(app.getContext());

                        if (shortcutExists(dataModel, itemInfo.getIntent(), itemInfo.user)) {
                            // We need this additional check here since we treat all auto added
                            // workspace items as promise icons. At this point we now have the
                            // correct intent to compare against existing workspace icons.
                            // Icon already exists on the workspace and should not be auto-added.
                            continue;
                        }

                        WorkspaceItemInfo wii = (WorkspaceItemInfo) itemInfo;
                        wii.title = "";
                        wii.bitmap = app.getIconCache().getDefaultIcon(item.user);
                        app.getIconCache().getTitleAndIcon(wii,
                                ((WorkspaceItemInfo) itemInfo).usingLowResIcon());
                    }
                }

                boolean isGoogleApp = false;
                String operatorAppList = Settings.Secure.getString(app.getContext().getContentResolver(), OPERATOR_APP_LIST_KEY);
                String[] operatorAppArray = null;
                if (operatorAppList != null) {
                    operatorAppArray = operatorAppList.split(",");
                }
                String mccmnc = SystemProperties.get("persist.radio.sim.mcc.mnc");
                //check google apps
                if ("20404".equals(mccmnc) || "26202".equals(mccmnc) || "23415".equals(mccmnc)) {
                    for (int i = 0; i < googleAppPackageList.length; i++) {
                        if(itemInfo.getTargetPackage().contains(googleAppPackageList[i][0])) {
                            isGoogleApp = true;
                            itemInfo.container = Integer.parseInt(googleAppPackageList[i][1]);
                            itemInfo.screenId = Integer.parseInt(googleAppPackageList[i][2]);
                            itemInfo.rank = Integer.parseInt(googleAppPackageList[i][3]);
                            if (operatorAppArray != null) {
                                for(int j =0;j < operatorAppArray.length; j++) {
                                    if (googleAppPackageList[i][0].equals(operatorAppArray[j])) {
                                        isGoogleApp = false;
                                    }
                                }
                            }
                        }
                    }
                }

                if (isGoogleApp) {
                    boolean hasGoogleFolder = false;
                    try (Cursor c = app.getContext().getContentResolver().query(LauncherSettings.Favorites.CONTENT_URI,
                        new String[] {LauncherSettings.Favorites.CONTAINER},LauncherSettings.Favorites. CONTAINER + "= 6", null, null)) {
                        int num=0;
                        while (c.moveToNext()) {
                            num++;
                        }
                        if (num > 1) {
                            hasGoogleFolder = true;
                        }
                    } catch (Exception e) {
                        FileLog.d(LOG,  "Error querying for launcher widget info", e);
                    }

                    if (hasGoogleFolder) {
                        for (int i = 0; i < googleAppPackageList.length; i++) {
                            if(itemInfo.getTargetPackage().contains(googleAppPackageList[i][0])) {
                                boolean has = false;
                                if (operatorAppArray != null) {
                                    for (int j = 0;j < operatorAppArray.length; j++) {
                                        if (googleAppPackageList[i][0].equals(operatorAppArray[j])) {
                                            has = true;
                                        }
                                    }
                                }
                                if (!has) {
                                    operatorAppList = operatorAppList +","+googleAppPackageList[i][0];
                                    boolean isSaveSuccesses =Settings.Secure.putString(app.getContext().getContentResolver(), OPERATOR_APP_LIST_KEY,operatorAppList);
                                    FileLog.d(LOG, "Save the Google App PackageName isSaveSuccesses:"+isSaveSuccesses);
                                }
                            }
                        }
                        // Add the deep shortcut to the db
                        getModelWriter().addItemToDatabase(itemInfo,
                            LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT,itemInfo.screenId,3, 3);
                        app.getModel().forceReload();
                    }
                    return;
                } else {
                    // Add the shortcut to the db
                    getModelWriter().addItemToDatabase(itemInfo,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId,coords[1], coords[2]);
                    // Save the WorkspaceItemInfo for binding in the workspace
                    addedItemsFinal.add(itemInfo);

                    // log bitmap and label
                    FileLog.d(LOG, "Adding item info to workspace: " + itemInfo);
                }
            }
        }

        if (!addedItemsFinal.isEmpty()) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(@NonNull Callbacks callbacks) {
                    final ArrayList<ItemInfo> addAnimated = new ArrayList<>();
                    final ArrayList<ItemInfo> addNotAnimated = new ArrayList<>();
                    if (!addedItemsFinal.isEmpty()) {
                        ItemInfo info = addedItemsFinal.get(addedItemsFinal.size() - 1);
                        int lastScreenId = info.screenId;
                        for (ItemInfo i : addedItemsFinal) {
                            if (i.screenId == lastScreenId) {
                                addAnimated.add(i);
                            } else {
                                addNotAnimated.add(i);
                            }
                        }
                    }
                    callbacks.bindAppsAdded(addedWorkspaceScreensFinal,
                            addNotAnimated, addAnimated);
                }
            });
        }
    }

    /**
     * Returns true if the shortcuts already exists on the workspace. This must be called after
     * the workspace has been loaded. We identify a shortcut by its intent.
     */
    protected boolean shortcutExists(@NonNull final BgDataModel dataModel,
            @Nullable final Intent intent, @NonNull final UserHandle user) {
        final String compPkgName, intentWithPkg, intentWithoutPkg;
        if (intent == null) {
            // Skip items with null intents
            return true;
        }
        if (intent.getComponent() != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            compPkgName = intent.getComponent().getPackageName();
            if (intent.getPackage() != null) {
                intentWithPkg = intent.toUri(0);
                intentWithoutPkg = new Intent(intent).setPackage(null).toUri(0);
            } else {
                intentWithPkg = new Intent(intent).setPackage(compPkgName).toUri(0);
                intentWithoutPkg = intent.toUri(0);
            }
        } else {
            compPkgName = null;
            intentWithPkg = intent.toUri(0);
            intentWithoutPkg = intent.toUri(0);
        }

        boolean isLauncherAppTarget = PackageManagerHelper.isLauncherAppTarget(intent);
        synchronized (dataModel) {
            for (ItemInfo item : dataModel.itemsIdMap) {
                if (item instanceof WorkspaceItemInfo) {
                    WorkspaceItemInfo info = (WorkspaceItemInfo) item;
                    if (item.getIntent() != null && info.user.equals(user)) {
                        Intent copyIntent = new Intent(item.getIntent());
                        copyIntent.setSourceBounds(intent.getSourceBounds());
                        String s = copyIntent.toUri(0);
                        if (intentWithPkg.equals(s) || intentWithoutPkg.equals(s)) {
                            return true;
                        }

                        // checking for existing promise icon with same package name
                        if (isLauncherAppTarget
                                && info.isPromise()
                                && info.hasStatusFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON)
                                && info.getTargetComponent() != null
                                && compPkgName != null
                                && compPkgName.equals(info.getTargetComponent().getPackageName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
