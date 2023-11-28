/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;

import android.util.LongSparseArray;
import android.os.SystemProperties;
import android.provider.Settings;
import android.database.ContentObserver;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.logging.FileLog;

import java.util.ArrayList;

/**
 * Utility class to help find space for new workspace items
 */
public class WorkspaceItemSpaceFinder {

    private static final String LOG = "WorkspaceItemSpaceFinder";

    //Operator AppList Configuration {PackageName,Screen,X,Y}
    //Naming convention operatorAppPackageList + mcc + mcn
    private static final String[][] operatorAppPackageList20404 = new String[][]{{"com.vodafone.android","0","1","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    private static final String[][] operatorAppPackageList26202  = new String[][]{{"com.appseleration.android.selfcare","0","1","4"},{"com.vodafone.android.app.rbt","0","2","4"},{"au.com.vodafone.dreamlabapp","0","3","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    private static final String[][] operatorAppPackageList23415 = new String[][]{{"com.myvodafoneapp","0","1","4"},{"au.com.vodafone.dreamlabapp","0","2","4"},{"com.google.android.apps.googleassistant","0","3","4"}};
    private ItemInfo operatorItemInfo;
    private GridOccupancy occupied;
    private static final String OPERATOR_APP_LIST_KEY = "def_operator_applist";

    /**
     * Find a position on the screen for the given size or adds a new screen.
     *
     * @return screenId and the coordinates for the item in an int array of size 3.
     */
    public int[] findSpaceForItem(LauncherAppState app, BgDataModel dataModel,
            IntArray workspaceScreens, IntArray addedWorkspaceScreensFinal, int spanX, int spanY) {
        LongSparseArray<ArrayList<ItemInfo>> screenItems = new LongSparseArray<>();

        // Use sBgItemsIdMap as all the items are already loaded.
        synchronized (dataModel) {
            for (ItemInfo info : dataModel.itemsIdMap) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                    if (items == null) {
                        items = new ArrayList<>();
                        screenItems.put(info.screenId, items);
                    }
                    items.add(info);
                }
            }
        }

        // Find appropriate space for the item.
        int screenId = 0;
        int[] coordinates = new int[2];
        boolean found = false;

        int screenCount = workspaceScreens.size();
        // First check the preferred screen.
        IntSet screensToExclude = new IntSet();
        if (FeatureFlags.QSB_ON_FIRST_SCREEN) {
            screensToExclude.add(FIRST_SCREEN_ID);
        }

        for (int screen = 0; screen < screenCount; screen++) {
            screenId = workspaceScreens.get(screen);
            if (!screensToExclude.contains(screenId) && findNextAvailableIconSpaceInScreen(
                    app, screenItems.get(screenId), coordinates, spanX, spanY)) {
                // We found a space for it
                found = true;
                break;
            }
        }

        if (!found) {
            // Still no position found. Add a new screen to the end.
            screenId = LauncherSettings.Settings.call(app.getContext().getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                    .getInt(LauncherSettings.Settings.EXTRA_VALUE);

            // Save the screen id for binding in the workspace
            workspaceScreens.add(screenId);
            addedWorkspaceScreensFinal.add(screenId);

            // If we still can't find an empty space, then God help us all!!!
            if (!findNextAvailableIconSpaceInScreen(
                    app, screenItems.get(screenId), coordinates, spanX, spanY)) {
                throw new RuntimeException("Can't find space to add the item");
            }
        }

        boolean isOperatorApp = false;
        boolean isGoogleApp = false;
        int initialCellX = 0;
        int initialCellY = 0;
        String operatorAppList = Settings.Secure.getString(app.getContext().getContentResolver(), OPERATOR_APP_LIST_KEY);
        String[] operatorAppArray = null;
        if (operatorAppList != null) {
            operatorAppArray = operatorAppList.split(",");
        }
        String mccmnc = SystemProperties.get("persist.radio.sim.mcc.mnc");
        //mcc:204 mcn:04
        if("20404".equals(mccmnc) && operatorItemInfo != null){
            for (int i = 0; i < operatorAppPackageList20404.length; i++) {
                if(operatorItemInfo.getTargetPackage().equals(operatorAppPackageList20404[i][0])) {
                    isOperatorApp = true;
                    initialCellX = Integer.parseInt(operatorAppPackageList20404[i][2]);
                    initialCellY = Integer.parseInt(operatorAppPackageList20404[i][3]);
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (operatorAppPackageList20404[i][0].equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }
                }
            }
        }

        //mcc:206 mcn:02
        if("26202".equals(mccmnc) && operatorItemInfo != null){
            for (int i = 0; i < operatorAppPackageList26202.length; i++) {
                if(operatorItemInfo.getTargetPackage().equals(operatorAppPackageList26202[i][0])) {
                    isOperatorApp = true;
                    initialCellX = Integer.parseInt(operatorAppPackageList26202[i][2]);
                    initialCellY = Integer.parseInt(operatorAppPackageList26202[i][3]);
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (operatorAppPackageList26202[i][0].equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }
                }
            }
        }

        //mcc:234 mcn:15
        if("23415".equals(mccmnc) && operatorItemInfo != null){
            for (int i = 0; i < operatorAppPackageList23415.length; i++) {
                if(operatorItemInfo.getTargetPackage().equals(operatorAppPackageList23415[i][0])) {
                    isOperatorApp = true;
                    initialCellX = Integer.parseInt(operatorAppPackageList23415[i][2]);
                    initialCellY = Integer.parseInt(operatorAppPackageList23415[i][3]);
                    if (operatorAppArray != null) {
                        for(int j =0;j < operatorAppArray.length; j++) {
                            if (operatorAppPackageList23415[i][0].equals(operatorAppArray[j])) {
                                isOperatorApp = false;
                            }
                        }
                    }
                }
            }
        }

        if (isOperatorApp) {
            boolean has = false;
            if (operatorAppArray != null) {
                for (int i = 0;i < operatorAppArray.length; i++) {
                    if (operatorItemInfo.getTargetPackage().equals(operatorAppArray[i])) {
                        has = true;
                    }
                }
            }
            if (!has) {
                operatorAppList = operatorAppList +","+operatorItemInfo.getTargetPackage();
                boolean isSaveSuccesses = Settings.Secure.putString(app.getContext().getContentResolver(), OPERATOR_APP_LIST_KEY,operatorAppList);
                FileLog.d(LOG, "Save the Operator App PackageName isSaveSuccesses:"+isSaveSuccesses);
            }

            for (int screenIdForAPP =0;screenIdForAPP < screenItems.size() ; screenIdForAPP++) {
                if (screenIdForAPP == 0) {
                    if (checkIsRegionVacant(app,screenItems.get(screenIdForAPP),initialCellX,initialCellY,1,1)) {
                        //There is space available, set it up directly
                        return new int[]{screenIdForAPP, initialCellX, initialCellY};
                    } else {
                        int[] coordinatesForApp = new int[2];
                        InvariantDeviceProfile profile = app.getInvariantDeviceProfile();
                        if (findVacantCellForApp(coordinatesForApp,occupied.cells,profile.numColumns,profile.numRows,1,1,initialCellX,initialCellY)) {
                            return new int[]{screenIdForAPP, coordinatesForApp[0], coordinatesForApp[1]};
                        }
                    }
                }
            }
        }
        return new int[]{screenId, coordinates[0], coordinates[1]};
    }

    private boolean findNextAvailableIconSpaceInScreen(
            LauncherAppState app, ArrayList<ItemInfo> occupiedPos,
            int[] xy, int spanX, int spanY) {
        InvariantDeviceProfile profile = app.getInvariantDeviceProfile();

        GridOccupancy occupied = new GridOccupancy(profile.numColumns, profile.numRows);
        if (occupiedPos != null) {
            for (ItemInfo r : occupiedPos) {
                occupied.markCells(r, true);
            }
        }
        return occupied.findVacantCell(xy, spanX, spanY);
    }

    public void setItemInfo(ItemInfo item){
        operatorItemInfo = item;
    }

    private boolean checkIsRegionVacant(LauncherAppState app,ArrayList<ItemInfo> occupiedPos,int x, int y, int spanX, int spanY){
            InvariantDeviceProfile profile = app.getInvariantDeviceProfile();
            occupied = new GridOccupancy(profile.numColumns, profile.numRows);
            if (occupiedPos != null) {
                for (ItemInfo r : occupiedPos) {
                    occupied.markCells(r, true);
                }
            }
            return occupied.isRegionVacant(x,y,spanX,spanY);
    }

    private boolean findVacantCellForApp(int[] vacantOut, boolean[][] cells, int countX, int countY,
            int spanX, int spanY,int initialX,int initialY) {
            for (int y = initialY; (y + spanY) <= countY; y++) {
                for (int x = initialX; (x + spanX) <= countX; x++) {
                    boolean available = !cells[x][y];
                    out:
                    for (int i = x; i < x + spanX; i++) {
                        for (int j = y; j < y + spanY; j++) {
                            available = available && !cells[i][j];
                            if (!available) break out;
                        }
                    }
                    if (available) {
                        vacantOut[0] = x;
                        vacantOut[1] = y;
                        return true;
                    }
                }
            }
            return false;
    }

}
