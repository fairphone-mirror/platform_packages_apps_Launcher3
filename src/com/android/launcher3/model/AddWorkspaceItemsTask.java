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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.UserHandle;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.GridOccupancy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task to add auto-created workspace items.
 */
public class AddWorkspaceItemsTask extends BaseModelUpdateTask {

    static final String TAG = "AddWorkspaceItemsTask";

    private final List<Pair<ItemInfo, Object>> mItemList;

    /**
     * @param itemList items to add on the workspace
     */
    public AddWorkspaceItemsTask(List<Pair<ItemInfo, Object>> itemList) {
        mItemList = itemList;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
        if (mItemList.isEmpty()) {
            return;
        }
        Context context = app.getContext();

        final ArrayList<ItemInfo> addedItemsFinal = new ArrayList<>();
        final ArrayList<Long> addedWorkspaceScreensFinal = new ArrayList<>();

        // Generic shortcut preferred placements
        final Map<ComponentName, Pair<Long, int[]>> shortcutPreferredPlacements
                = getShortcutPreferredPlacements(context, R.array.shortcut_preferred_placements);

        // Get the list of workspace screens.  We need to append to this list and
        // can not use sBgWorkspaceScreens because loadWorkspace() may not have been
        // called.
        ArrayList<Long> workspaceScreens = LauncherModel.loadWorkspaceScreensDb(context);
        synchronized(dataModel) {

            List<ItemInfo> filteredItems = new ArrayList<>();
            for (Pair<ItemInfo, Object> entry : mItemList) {
                ItemInfo item = entry.first;
                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                        item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                    // Short-circuit this logic if the icon exists somewhere on the workspace
                    if (shortcutExists(dataModel, item.getIntent(), item.user)) {
                        continue;
                    }
                }

                if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    if (item instanceof AppInfo) {
                        item = ((AppInfo) item).makeShortcut();
                    }
                }
                if (item != null) {
                    filteredItems.add(item);
                }
            }

            for (ItemInfo item : filteredItems) {
                // Find appropriate space for the item.
                Pair<Long, int[]> coords = findSpaceForItem(app, dataModel,
                        shortcutPreferredPlacements.get(item.getIntent().getComponent()),
                        workspaceScreens, addedWorkspaceScreensFinal, item.spanX, item.spanY);
                long screenId = coords.first;
                int[] cordinates = coords.second;

                ItemInfo itemInfo;
                if (item instanceof ShortcutInfo || item instanceof FolderInfo ||
                        item instanceof LauncherAppWidgetInfo) {
                    itemInfo = item;
                } else if (item instanceof AppInfo) {
                    itemInfo = ((AppInfo) item).makeShortcut();
                } else {
                    throw new RuntimeException("Unexpected info type");
                }

                // Add the shortcut to the db
                getModelWriter().addItemToDatabase(itemInfo,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId,
                        cordinates[0], cordinates[1]);

                // Save the ShortcutInfo for binding in the workspace
                addedItemsFinal.add(itemInfo);
            }
        }

        // Update the workspace screens
        updateScreens(context, workspaceScreens);

        if (!addedItemsFinal.isEmpty()) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    final ArrayList<ItemInfo> addAnimated = new ArrayList<>();
                    final ArrayList<ItemInfo> addNotAnimated = new ArrayList<>();
                    if (!addedItemsFinal.isEmpty()) {
                        ItemInfo info = addedItemsFinal.get(addedItemsFinal.size() - 1);
                        long lastScreenId = info.screenId;
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

    protected void updateScreens(Context context, ArrayList<Long> workspaceScreens) {
        LauncherModel.updateWorkspaceScreenOrder(context, workspaceScreens);
    }

    /**
     * Returns true if the shortcuts already exists on the workspace. This must be called after
     * the workspace has been loaded. We identify a shortcut by its intent.
     */
    protected boolean shortcutExists(BgDataModel dataModel, Intent intent, UserHandle user) {
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

        boolean isLauncherAppTarget = Utilities.isLauncherAppTarget(intent);
        synchronized (dataModel) {
            for (ItemInfo item : dataModel.itemsIdMap) {
                if (item instanceof ShortcutInfo) {
                    ShortcutInfo info = (ShortcutInfo) item;
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
                                && info.hasStatusFlag(ShortcutInfo.FLAG_AUTOINSTALL_ICON)
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

    /**
     * Find a position on the screen for the given size or adds a new screen.
     * @return screenId and the coordinates for the item.
     */
    protected Pair<Long, int[]> findSpaceForItem(
            LauncherAppState app, BgDataModel dataModel,
            ArrayList<Long> workspaceScreens,
            ArrayList<Long> addedWorkspaceScreensFinal,
            int spanX, int spanY) {
        return findSpaceForItem(app, dataModel, null, workspaceScreens,
                addedWorkspaceScreensFinal, spanX, spanY);
    }

    /**
     * Find a position on the screen for the given size or adds a new screen.
     * @return screenId and the coordinates for the item.
     */
    protected Pair<Long, int[]> findSpaceForItem(
            LauncherAppState app, BgDataModel dataModel,
            Pair<Long, int[]> shortcutPreferredPlacement,
            ArrayList<Long> workspaceScreens,
            ArrayList<Long> addedWorkspaceScreensFinal,
            int spanX, int spanY) {
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
        long screenId = 0;
        int[] cordinates = new int[2];
        boolean found = false;

        int screenCount = workspaceScreens.size();

        if (shortcutPreferredPlacement != null) {
            // First, check the preferred placement.
            screenId = shortcutPreferredPlacement.first;
            cordinates = shortcutPreferredPlacement.second;
            if (screenId < screenCount) {
                found = isAvailableIconSpaceInScreen(
                        app, screenItems.get(screenId), cordinates, spanX, spanY);
            }
        }

        if (!found) {
            // First check the preferred screen.
            int preferredScreenIndex = workspaceScreens.isEmpty() ? 0 : 1;
            if (preferredScreenIndex < screenCount) {
                screenId = workspaceScreens.get(preferredScreenIndex);
                found = findNextAvailableIconSpaceInScreen(
                        app, screenItems.get(screenId), cordinates, spanX, spanY);
            }
        }

        if (!found) {
            // Search on any of the screens starting from the first screen.
            for (int screen = 1; screen < screenCount; screen++) {
                screenId = workspaceScreens.get(screen);
                if (findNextAvailableIconSpaceInScreen(
                        app, screenItems.get(screenId), cordinates, spanX, spanY)) {
                    // We found a space for it
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            // Still no position found. Add a new screen to the end.
            screenId = LauncherSettings.Settings.call(app.getContext().getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                    .getLong(LauncherSettings.Settings.EXTRA_VALUE);

            // Save the screen id for binding in the workspace
            workspaceScreens.add(screenId);
            addedWorkspaceScreensFinal.add(screenId);

            // If we still can't find an empty space, then God help us all!!!
            if (!findNextAvailableIconSpaceInScreen(
                    app, screenItems.get(screenId), cordinates, spanX, spanY)) {
                throw new RuntimeException("Can't find space to add the item");
            }
        }
        return Pair.create(screenId, cordinates);
    }

    /**
     * Validate that a specific icon space is available.
     * <p>
     * Heavily inspired by #findNextAvailableIconSpaceInScreen(ArrayList<>,
     * int[], int, int) - re-uses factored out code in
     * itemInfosToGridOccupancy().
     *
     * @return true if the specific icon space is available.
     */
    private static boolean isAvailableIconSpaceInScreen(
            LauncherAppState app, ArrayList<ItemInfo> occupiedPos,
            int[] xy, int spanX, int spanY) {
        GridOccupancy occupied = itemInfosToGridOccupancy(app, occupiedPos);
        return occupied.isRegionVacant(xy[0], xy[1], spanX, spanY);
    }

    /*
     * Generate GridOccupancy from array of ItemInfo
     *
     * Factored out from #findNextAvailableIconSpaceInScreen(ArrayList<>, int[], int, int)
     * to be re-used by #isAvailableIconSpaceInScreen(ArrayList<>, int[], int, int)
     *
     * @return GridOccupancy with coordinates from items marked as occupied.
     */
    private static GridOccupancy itemInfosToGridOccupancy(
            LauncherAppState app, ArrayList<ItemInfo> occupiedPos) {
        InvariantDeviceProfile profile = app.getInvariantDeviceProfile();

        GridOccupancy occupied = new GridOccupancy(profile.numColumns, profile.numRows);
        if (occupiedPos != null) {
            for (ItemInfo r : occupiedPos) {
                occupied.markCells(r, true);
            }
        }
        return occupied;
    }

    private static boolean findNextAvailableIconSpaceInScreen(
            LauncherAppState app, ArrayList<ItemInfo> occupiedPos,
            int[] xy, int spanX, int spanY) {
        GridOccupancy occupied = itemInfosToGridOccupancy(app, occupiedPos);
        return occupied.findVacantCell(xy, spanX, spanY);
    }

    /**
     * Get the shortcut preferred placements from a resource array.
     * <p>
     * An item (a String) should contain the following information separated by commas:
     * <ul>
     *     <li>the {@link ComponentName}</li>
     *     <li>the screen id (e.g. 0 for the main screen</li>
     *     <li>the abscissa (aka cell X)</li>
     *     <li>the ordinate (aka cell Y)</li>
     * </ul>
     * @param context the context to read the resources from.
     * @param array the string array resource to inflate from.
     * @return a map of valid placements found in the resource array.
     * @throws Resources.NotFoundException if the string array cannot be loaded from the context resources.
     */
    private static Map<ComponentName, Pair<Long, int[]>> getShortcutPreferredPlacements(Context context, int array)
            throws Resources.NotFoundException {
        final String[] rawPlacements = context.getResources().getStringArray(array);
        final Map<ComponentName, Pair<Long, int[]>> placements = new HashMap<>(rawPlacements.length);

        for (String rawPlacement : rawPlacements) {
            final String[] items = rawPlacement.split(",");

            if (items.length != 4) {
                Log.e(TAG, "Invalid preferred placement : " + rawPlacement
                        + ", 4 comma-separated items were expected");
                continue;
            }

            try {
                final ComponentName component = ComponentName.unflattenFromString(items[0]);
                final long screenId = Long.valueOf(items[1]);
                final int cellX = Integer.parseInt(items[2]);
                final int cellY = Integer.parseInt(items[3]);
                placements.put(component, new Pair<>(screenId, new int[] {cellX, cellY}));
            } catch (NumberFormatException ex) {
                Log.e(TAG, "Invalid preferred placement: " + rawPlacement, ex);
            }
        }

        return placements;
    }
}
