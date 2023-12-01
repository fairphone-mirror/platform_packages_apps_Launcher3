package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.os.Process;
import android.util.SparseArray;
import android.os.UserHandle;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.secondarydisplay.DesktopIconKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.pm.UserCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PinIconUtils {

    private Context mContext;
    private static final String PINNED_DESKTOP_APPS_KEY = "pinned_desktop_apps";
    private static final String PINNED_TASKBAR_APPS_KEY = "pinned_taskbar_apps";
    private final SharedPreferences mDesktopPrefs;
    private final Set<DesktopIconKey> mDesktopApps = new HashSet<>();
    private final SparseArray<ComponentKey> mDesktops = new SparseArray<>();
    private SharedPreferences mTaskbarPrefs;
    private final Set<ComponentKey> mTaskbarPinnedApps = new HashSet<>();
    private int mLastPos = -1;

    public PinIconUtils(Context context){
        mContext = context;
        mDesktopPrefs = mContext.getSharedPreferences(PINNED_DESKTOP_APPS_KEY,MODE_PRIVATE);
        mTaskbarPrefs = mContext.getSharedPreferences(PINNED_TASKBAR_APPS_KEY,MODE_PRIVATE);
        Executors.MODEL_EXECUTOR.submit(() -> {
            Set<ComponentKey> apps = mTaskbarPrefs.getStringSet(PINNED_TASKBAR_APPS_KEY, Collections.emptySet())
                    .stream()
                    .map(s->parseComponentKey(s))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Executors.MAIN_EXECUTOR.submit(() -> {
                mTaskbarPinnedApps.clear();
                mTaskbarPinnedApps.addAll(apps);
            });
        });
        Executors.MODEL_EXECUTOR.submit(() -> {
            Set<DesktopIconKey> desktopApps = mDesktopPrefs.getStringSet(PINNED_DESKTOP_APPS_KEY, Collections.emptySet())
                    .stream()
                    .map(s->parseDesktopIconKey(s))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Executors.MAIN_EXECUTOR.submit(() -> {
                mDesktopApps.clear();
                mDesktopApps.addAll(desktopApps);
                mDesktops.clear();
                for(DesktopIconKey desktopKey: mDesktopApps){
                    mDesktops.put(desktopKey.getPos(),desktopKey.getComponentKey());
                }
                if(mDesktops.size() > 0){
                    mLastPos = mDesktops.keyAt(mDesktops.size() -1);
                }
            });
        });
    }
    
    public static ComponentKey parseComponentKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user = Process.myUserHandle();
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            return new ComponentKey(cn, user);
        } catch (Exception e) {
            return null;
        }
    }

    public void updateTaskbar(ItemInfo info, boolean isPinned) {
        ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);
        if(isPinned){
            mTaskbarPinnedApps.remove(key);
        } else {
            mTaskbarPinnedApps.add(key);
        }
        if(mTaskbarPinnedApps.size() > 10) {
            mTaskbarPinnedApps.remove(key);
            return;
        }
        Set<ComponentKey> copy = new HashSet<>(mTaskbarPinnedApps);
        Executors.MODEL_EXECUTOR.submit(() ->
                mTaskbarPrefs.edit().putStringSet(PINNED_TASKBAR_APPS_KEY,
                    copy.stream().map(key1->encode(mContext, key1)).collect(Collectors.toSet()))
                    .apply());
    }

    public static String encode(Context context,ComponentKey key) {
        return key.componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(context).getSerialNumberForUser(key.user);
    }

    public static DesktopIconKey parseDesktopIconKey(String string) {
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

    public void updateDesktop(ItemInfo info, boolean isToAdd, int max) {
        ComponentKey componentKey = new ComponentKey(info.getTargetComponent(), info.user);
        int pos = 0;
        if(isToAdd){
            mLastPos++;
            pos = mLastPos;
            if(pos >= max){
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
                    copy.stream().map(key->encodeDesktop(mContext,key)).collect(Collectors.toSet()))
                    .apply());
    }

    public static String encodeDesktop(Context context,DesktopIconKey key) {
        return key.getComponentKey().componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(context).getSerialNumberForUser(key.getComponentKey().user)+"#"+key.getPos();
    }
}
