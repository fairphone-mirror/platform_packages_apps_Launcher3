package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;
import static com.android.launcher3.Utilities.dpToPx;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import com.android.launcher3.secondarydisplay.PinIconUtils;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.pm.UserCache;

import android.content.ComponentName;
import android.os.Process;
import android.os.UserHandle;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class OverlayShortcut<T extends BaseOverlayView> extends ItemInfo
        implements View.OnClickListener {
    private final int mIconResId;
    private final int mLabelResId;
    protected final T mTarget;
    protected final ItemInfo mItemInfo;
    private boolean isEnabled = true;

    public OverlayShortcut(int iconResId, int labelResId, T target, ItemInfo itemInfo) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mTarget = target;
        mItemInfo = itemInfo;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setIconAndLabelFor(View iconView, TextView labelView) {
        iconView.setBackgroundResource(mIconResId);
        labelView.setText(mLabelResId);
    }

    public interface Factory<T extends BaseOverlayView> {

        @Nullable OverlayShortcut<T> getShortcut(T BaseOverlayView, ItemInfo itemInfo);
    }

    public static final Factory<BaseOverlayView> APP_INFO = AppInfo::new;

    public static class AppInfo extends OverlayShortcut {

        public AppInfo(BaseOverlayView target, ItemInfo itemInfo) {
            super(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label, target,
                    itemInfo);
        }

        @Override
        public void onClick(View view) {
            mTarget.dissmiss();
            new PackageManagerHelper(mTarget).startDetailsActivityForInfo(
                    mItemInfo, null, ActivityOptions.makeBasic().toBundle());
        }
    }

    public static final Factory<BaseOverlayView> UNINSTALL = UninstallShortcut::new;

    public static class UninstallShortcut extends OverlayShortcut{
        UninstallShortcut(BaseOverlayView target, ItemInfo info) {
            super(R.drawable.ic_detele_all,R.string.uninstall_drop_target_label,target, info);
        }

        @Override
        public void onClick(View view) {
            mTarget.dissmiss();
            final Uri packageURI = Uri.parse("package:" + mItemInfo.getTargetComponent().getPackageName());
            final Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
            uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
            mTarget.startActivity(uninstallIntent);
        }
    }

    public static class PinTaskBarShortcut extends OverlayShortcut {

        private final boolean mIsPinned;
        private PinIconUtils mPinIcon;
    
        PinTaskBarShortcut(BaseOverlayView target, ItemInfo info, boolean isPinned) {
            super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                    isPinned ? R.string.remove_from_taskbar_lable : R.string.add_to_taskbar_label,
                    target, info);
            mIsPinned = isPinned;
            mPinIcon = new PinIconUtils(target);
        }
    
        @Override
        public void onClick(View view) {
            mTarget.dissmiss();
            mPinIcon.updateTaskbar(mItemInfo, mIsPinned);
        }
    }

    public static class PinDesktopShortcut extends OverlayShortcut {
        private final boolean mIsPinned;
        private PinIconUtils mPinIcon;
        private int max;

        PinDesktopShortcut(BaseOverlayView target, ItemInfo info, boolean isPinned) {
            super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                    isPinned ? R.string.remove_drop_target_label : R.string.action_add_to_workspace,
                    target, info);
            mIsPinned = isPinned;
            mPinIcon = new PinIconUtils(target);
            int screenWidth = target.getDisplay().getWidth();
            int screenHeight = target.getDisplay().getHeight();
            int desktopIconSize = target.getResources().getDimensionPixelSize(R.dimen.secondary_desktop_grid_width);
            max = (screenWidth  / desktopIconSize) *((screenHeight - dpToPx(100)) / desktopIconSize);
        }

        @Override
        public void onClick(View view) {
            mTarget.dissmiss();
            mPinIcon.updateDesktop(mItemInfo,!mIsPinned,max);
        }
    }
}
