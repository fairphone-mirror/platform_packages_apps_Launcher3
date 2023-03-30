package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_POPUP;
import static com.android.launcher3.Utilities.dpToPx;

import android.util.Log;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.graphics.Bitmap;
import android.view.Display;
import android.graphics.Rect;
import android.widget.LinearLayout;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.net.Uri;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import com.android.launcher3.secondarydisplay.OverlayShortcut;
import com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class SecondaryPopupContainerView extends BaseOverlayView  {

    private static final String WINDOW_TITLE = "SecondaryPopupContainer";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private LinearLayout mContainer;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private SecondaryDisplayLauncher mLauncher;

    public SecondaryPopupContainerView(Context context) {
        super(context, Themes.getActivityThemeRes(context));
        mLauncher = SecondaryDisplayLauncher.fromContext(context);
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater.inflate(R.layout.secondary_popup_container, null, false);
        mContainer = (LinearLayout) mLayout.findViewById(R.id.popup_container);
        mLayout.setOnClickListener(view ->{
            dissmiss();
        });

    }

    private View initOverlayShortcut(int resId,OverlayShortcut info) {
        View view = mLayoutInflater.inflate(resId, null, false);
        if (view instanceof DeepShortcutView) {
            final DeepShortcutView shortcutView = (DeepShortcutView) view;
            info.setIconAndLabelFor(shortcutView.getIconView(), shortcutView.getBubbleText());
        } 
        view.setTag(info);
        view.setOnClickListener(info);
        return view;
    }

    public void showPopupContainter(List<OverlayShortcut> shortcuts,View view){
        mContainer.removeAllViews();
        for(OverlayShortcut shortcut : shortcuts) {
            View v = initOverlayShortcut(R.layout.system_shortcut, shortcut);
            mContainer.addView(v);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) v.getLayoutParams();
            params.topMargin = dpToPx(2);
        }
        Rect rect = getViewBounds(view);
        mContainer.setX(rect.right);
        mContainer.setY(rect.top);
        show();
    }

    public void showPopupContainter(List<OverlayShortcut> shortcuts, int x, int y){
        mContainer.removeAllViews();
        for(OverlayShortcut shortcut : shortcuts) {
            View v = initOverlayShortcut(R.layout.system_shortcut, shortcut);
            mContainer.addView(v);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) v.getLayoutParams();
            params.topMargin = dpToPx(2);
        }
        mContainer.setX(x);
        mContainer.setY(y);
        show();
    }

    private Rect getViewBounds(View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new Rect(pos[0], pos[1]- 2 * v.getHeight(), pos[0] + v.getWidth(), pos[1] );
    }

    @Override
    public void show(){
        if(isShow()){
            return;
        }
        int height = mContext.getDisplay().getHeight() - dpToPx(24);
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
        mWindowLayoutParams.windowAnimations = R.style.bottom_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_POPUP) != 0;
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
    public void onDestroy() {
        mContext = null;
        mHandler.removeCallbacksAndMessages(null);
    }
}
