package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_POPUP;
import static com.android.launcher3.Utilities.dpToPx;

import android.util.Log;
import android.content.Context;
import android.content.ComponentName;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.view.Display;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.net.Uri;
import android.graphics.PixelFormat;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.secondarydisplay.BaseOverlayView;

public class CalendarMonthView extends BaseOverlayView {

    private static final String WINDOW_TITLE = "CalendarMonth";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private TextView mTvMore;
    private Context mContext;

    public CalendarMonthView(Context context) {
        super(context, Themes.getActivityThemeRes(context));
        mContext = context;
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater.inflate(R.layout.calendar_month, null, false);
        mLayout.setOnClickListener(view ->{
            dissmiss();
        });
        mTvMore = (TextView) mLayout.findViewById(R.id.tv_more);
        mTvMore.setOnClickListener(v->{
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.google.android.calendar","com.android.calendar.AllInOneActivity"));
            mContext.startActivity(intent);
            dissmiss();
        });
    }

    @Override
    protected boolean isOfViewType(int type) {
        return (type & TYPE_POPUP) != 0;
    }

    @Override
    public void show(){
        int height = mContext.getDisplay().getHeight() - dpToPx(32);
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
        mWindowLayoutParams.gravity = Gravity.TOP;
        mWindowLayoutParams.windowAnimations = R.style.bottom_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
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
    }
}

