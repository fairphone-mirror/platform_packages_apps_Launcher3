package com.android.launcher3.secondarydisplay;


import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SETTINGS_BUTTON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGETSTRAY_BUTTON_TAP_OR_LONGPRESS;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import android.view.ViewGroup;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;
import com.android.launcher3.secondarydisplay.SecondaryDragLayer;
import com.android.launcher3.secondarydisplay.wallpaper.SecondaryWallpaperPicker;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup shown on long pressing an empty space in secondary launcher
 */
public class SecondaryOptionsPopupView extends ArrowPopup<SecondaryDisplayLauncher>
        implements OnClickListener, OnLongClickListener {

    private final ArrayMap<View, OptionItem> mItemMap = new ArrayMap<>();
    private RectF mTargetRect;
    private boolean mShouldAddArrow;

    public SecondaryOptionsPopupView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SecondaryOptionsPopupView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onClick(View view) {
        handleViewClick(view);
    }

    @Override
    public boolean onLongClick(View view) {
        return handleViewClick(view);
    }

    private boolean handleViewClick(View view) {
        OptionItem item = mItemMap.get(view);
        if (item == null) {
            return false;
        }
        if (item.eventId.getId() > 0) {
            mActivityContext.getStatsLogManager().logger().log(item.eventId);
        }
        if (item.clickListener.onLongClick(view)) {
            close(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (getPopupContainer().isEventOverView(this, ev)) {
            return false;
        }
        close(true);
        return true;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_OPTIONS_POPUP) != 0;
    }

    public void setShouldAddArrow(boolean shouldAddArrow) {
        mShouldAddArrow = shouldAddArrow;
    }

    @Override
    protected boolean shouldAddArrow() {
        return mShouldAddArrow;
    }

    @Override
    protected void getTargetObjectLocation(Rect outPos) {
        mTargetRect.roundOut(outPos);
    }

    public static SecondaryOptionsPopupView show(
            SecondaryDisplayLauncher launcher, RectF targetRect, List<OptionItem> items, boolean shouldAddArrow) {
        return show(launcher, targetRect, items, shouldAddArrow, 0 /* width */);
    }

    public static SecondaryOptionsPopupView show(
            SecondaryDisplayLauncher launcher, RectF targetRect, List<OptionItem> items, boolean shouldAddArrow,
            int width) {
        SecondaryOptionsPopupView popup = (SecondaryOptionsPopupView) launcher.getLayoutInflater()
                .inflate(R.layout.secondary_longpress_options_menu, launcher.getDragLayer(), false);
        popup.mTargetRect = targetRect;
        popup.setShouldAddArrow(shouldAddArrow);

        for (OptionItem item : items) {
            DeepShortcutView view =
                    (DeepShortcutView) popup.inflateAndAdd(R.layout.system_shortcut, popup);
            if (width > 0) {
                view.getLayoutParams().width = width;
            }
            view.getIconView().setBackgroundDrawable(item.icon);
            view.getBubbleText().setText(item.label);
            view.setOnClickListener(popup);
            view.setOnLongClickListener(popup);
            popup.mItemMap.put(view, item);
        }

        popup.show();
        return popup;
    }

    // @Override
    // protected List<View> getChildrenForColorExtraction() {
    //     int childCount = getChildCount();
    //     ArrayList<View> children = new ArrayList<>(childCount);
    //     for (int i = 0; i < childCount; ++i) {
    //         children.add(getChildAt(i));
    //     }
    //     return children;
    // }

    @VisibleForTesting
    public static ArrowPopup getOptionsPopup(SecondaryDisplayLauncher launcher) {
        return launcher.findViewById(R.id.popup_container);
    }

    public static void showDefaultOptions(SecondaryDisplayLauncher launcher, float x, float y) {
        float halfSize = launcher.getResources().getDimension(R.dimen.options_menu_thumb_size) / 2;
        if (x < 0 || y < 0) {
            x = launcher.getDragLayer().getWidth() / 2;
            y = launcher.getDragLayer().getHeight() / 2;
        }
        RectF target = new RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize);
        show(launcher, target, getOptions(launcher), false);
    }

    public void showTaskbarPopup(SecondaryDisplayLauncher launcher,List<SystemShortcut> systemShortcuts, float x, float y){
        float halfSize = launcher.getResources().getDimension(R.dimen.options_menu_thumb_size) / 2;
        if (x < 0 || y < 0) {
            x = launcher.getDragLayer().getWidth() / 2;
            y = launcher.getDragLayer().getHeight() / 2;
        }
        RectF target = new RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize);
        this.mTargetRect = target;
        this.setShouldAddArrow(false);
        if (!systemShortcuts.isEmpty()) {
            for (SystemShortcut shortcut : systemShortcuts) {
                initializeSystemShortcut(R.layout.system_shortcut, this, shortcut);
            }
        }
        this.show();
    }

    private void initializeSystemShortcut(int resId, ViewGroup container, SystemShortcut info) {
        View view = inflateAndAdd(resId, container);
        if (view instanceof DeepShortcutView) {
            // Expanded system shortcut, with both icon and text shown on white background.
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            info.setIconAndLabelFor(shortcutView.getIconView(), shortcutView.getBubbleText());
        } else if (view instanceof ImageView) {
            // Only the system shortcut icon shows on a gray background header.
            info.setIconAndContentDescriptionFor((ImageView) view);
            view.setTooltipText(view.getContentDescription());
        }
        view.setTag(info);
        view.setOnClickListener(info);
    }
    /**
     * Returns the list of supported actions
     */
    public static ArrayList<OptionItem> getOptions(SecondaryDisplayLauncher launcher) {
        ArrayList<OptionItem> options = new ArrayList<>();
        options.add(new OptionItem(launcher,
                R.string.sort_desktop_icon,R.drawable.ic_sort,IGNORE,SecondaryOptionsPopupView::startSortIcons));
        options.add(new OptionItem(launcher,
                R.string.arrange_icon,R.drawable.ic_move,IGNORE,SecondaryOptionsPopupView::startArrangeMode));
        int resString = styleWallpapersExists(launcher) ?
                R.string.styles_wallpaper_button_text : R.string.wallpaper_button_text;
        int resDrawable = styleWallpapersExists(launcher) ?
                R.drawable.ic_palette : R.drawable.ic_wallpaper;
        options.add(new OptionItem(launcher,
                resString,
                resDrawable,
                IGNORE,
                SecondaryOptionsPopupView::startWallpaperPicker));
        return options;
    }

    private static boolean onWidgetsClicked(View view) {
        return openWidgets(SecondaryDisplayLauncher.getLauncher(view.getContext())) != null;
    }

    /** Returns WidgetsFullSheet that was opened, or null if nothing was opened. */
    @Nullable
    public static WidgetsFullSheet openWidgets(SecondaryDisplayLauncher launcher) {
        if (launcher.getPackageManager().isSafeMode()) {
            Toast.makeText(launcher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            return null;
        } else {
            return null;
            //return WidgetsFullSheet.show(launcher, true /* animated */);
        }
    }

    private static boolean startSortIcons(View v){
        SecondaryDisplayLauncher launcher = SecondaryDisplayLauncher.getLauncher(v.getContext());
        SecondaryDragLayer dragLayer = (SecondaryDragLayer)launcher.getDragLayer();
        dragLayer.sortDesktopIcon();
        return true;
    }

    private static boolean startArrangeMode(View v) {
        SecondaryDisplayLauncher launcher = SecondaryDisplayLauncher.getLauncher(v.getContext());
        SecondaryDragLayer dragLayer = (SecondaryDragLayer)launcher.getDragLayer();
        dragLayer.startArrangeMode();
        return true;
    }

    private static boolean styleWallpapersExists(Context context) {
        return context.getPackageManager().resolveActivity(
                PackageManagerHelper.getStyleWallpapersIntent(context), 0) != null;
    }
    /**
     * Event handler for the wallpaper picker button that appears after a long press
     * on the home screen.
     */
    private static boolean startWallpaperPicker(View v) {
        SecondaryDisplayLauncher launcher = SecondaryDisplayLauncher.getLauncher(v.getContext());
        if (!Utilities.isWallpaperAllowed(launcher)) {
            Toast.makeText(launcher, R.string.msg_disabled_by_admin, Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent intent = new Intent(launcher.getBaseContext(),SecondaryWallpaperPicker.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launcher.startActivitySafely(v, intent, placeholderInfo(intent)) != null;
    }

    static WorkspaceItemInfo placeholderInfo(Intent intent) {
        WorkspaceItemInfo placeholderInfo = new WorkspaceItemInfo();
        placeholderInfo.intent = intent;
        placeholderInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
        placeholderInfo.container = LauncherSettings.Favorites.CONTAINER_SETTINGS;
        return placeholderInfo;
    }

    public static class OptionItem {

        // Used to create AccessibilityNodeInfo in AccessibilityActionsView.java.
        public final int labelRes;

        public final CharSequence label;
        public final Drawable icon;
        public final EventEnum eventId;
        public final OnLongClickListener clickListener;

        public OptionItem(Context context, int labelRes, int iconRes, EventEnum eventId,
                          OnLongClickListener clickListener) {
            this.labelRes = labelRes;
            this.label = context.getText(labelRes);
            this.icon = ContextCompat.getDrawable(context, iconRes);
            this.eventId = eventId;
            this.clickListener = clickListener;
        }

        public OptionItem(CharSequence label, Drawable icon, EventEnum eventId,
                          OnLongClickListener clickListener) {
            this.labelRes = 0;
            this.label = label;
            this.icon = icon;
            this.eventId = eventId;
            this.clickListener = clickListener;
        }
    }
}

