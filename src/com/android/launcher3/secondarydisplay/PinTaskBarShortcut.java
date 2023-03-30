package com.android.launcher3.secondarydisplay;

import android.content.SharedPreferences;
import android.view.View;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.R;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.AbstractFloatingView;
import static android.content.Context.MODE_PRIVATE;

import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PinTaskBarShortcut extends SystemShortcut<SecondaryDisplayLauncher> {

    private final boolean mIsPinned;
    private final SecondaryDisplayLauncher mLauncher;
    private static final String PINNED_TASKBAR_APPS_KEY = "pinned_taskbar_apps";
    private SharedPreferences mTaskbarPrefs;
    private final Set<ComponentKey> mTaskbarPinnedApps = new HashSet<>();

    PinTaskBarShortcut(SecondaryDisplayLauncher target, ItemInfo info, boolean isPinned, View originalView) {
        super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                isPinned ? R.string.remove_from_taskbar_lable : R.string.add_to_taskbar_label,
                target, info, originalView);
        mIsPinned = isPinned;
        mLauncher = target;
        mTaskbarPrefs = target.getSharedPreferences(PINNED_TASKBAR_APPS_KEY,MODE_PRIVATE);
        Executors.MODEL_EXECUTOR.submit(() -> {
            Set<ComponentKey> apps = mTaskbarPrefs.getStringSet(PINNED_TASKBAR_APPS_KEY, Collections.emptySet())
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

    @Override
    public void onClick(View view) {
        if (mIsPinned) {
            updateTaskbar(mItemInfo,mTaskbarPinnedApps::remove);
        } else {
            updateTaskbar(mItemInfo,mTaskbarPinnedApps::add);
        }
        AbstractFloatingView.closeAllOpenViews(mLauncher);
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

    private void updateTaskbar(ItemInfo info, Function<ComponentKey, Boolean> op) {
        ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);
        if (op.apply(key)) {
            if(mTaskbarPinnedApps.size() > 10) {
                mTaskbarPinnedApps.remove(key);
                return;
            }
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
}