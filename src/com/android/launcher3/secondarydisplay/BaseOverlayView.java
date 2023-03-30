package com.android.launcher3.secondarydisplay;

import android.view.ContextThemeWrapper;
import android.content.Context;
import android.view.WindowManager;

import com.android.launcher3.secondarydisplay.OverlayManager;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public abstract class BaseOverlayView extends ContextThemeWrapper{
    @IntDef(flag = true, value = {
        TYPE_POPUP,
        TYPE_ALL_APPS,
        TYPE_TASKBAR,
        TYPE_SLIDEBAR,
        TYPE_RECENTS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OverlayViewType {}
    public static final int TYPE_POPUP = 1 << 1;
    public static final int TYPE_ALL_APPS = 1 << 2;
    public static final int TYPE_TASKBAR = 1 << 3;
    public static final int TYPE_SLIDEBAR = 1 << 4;
    public static final int TYPE_RECENTS = 1 << 5;

    private boolean isShow = false;
    protected final WindowManager mWindowManager;
    protected WindowManager.LayoutParams mWindowLayoutParams;
    protected Context mContext;

    public BaseOverlayView(Context context, int themeResId) {
        super(context, themeResId);
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        OverlayManager.getInstance().addOverlayView(this);
    }

    public abstract void show();

    public abstract void dissmiss();

    public abstract void onDestroy();

    protected abstract boolean isOfViewType(@OverlayViewType int type);
    
    public boolean isShow(){
        return this.isShow;
    }

    public void setShow(boolean isShow){
        this.isShow = isShow;
    }
}
