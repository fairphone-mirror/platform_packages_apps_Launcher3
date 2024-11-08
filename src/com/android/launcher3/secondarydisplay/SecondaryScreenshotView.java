package com.android.launcher3.secondarydisplay;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_POPUP;
import static com.android.launcher3.Utilities.dpToPx;

import android.util.Log;
import com.android.launcher3.secondarydisplay.BaseOverlayView;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.view.Display;
import android.graphics.BitmapFactory;
import android.content.Intent;
import android.net.Uri;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public class SecondaryScreenshotView extends BaseOverlayView {

    private static final String WINDOW_TITLE = "SecondaryScreenshot";
    private final LayoutInflater mLayoutInflater;
    private View mLayout;
    private ImageView mIvScreenshot;
    private Context mContext;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Uri mUri;

    public SecondaryScreenshotView(Context context) {
        super(context, Themes.getActivityThemeRes(context));
        mContext = context;
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
        mLayout = mLayoutInflater.inflate(R.layout.secondary_screenshot, null, false);
        mIvScreenshot = (ImageView) mLayout.findViewById(R.id.iv_screenshot);
        mLayout.setOnClickListener(view ->{
            dissmiss();
        });
        mIvScreenshot.setOnClickListener(view ->{
            if(mUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW,mUri);
                mContext.startActivity(intent);
            }
            dissmiss();
        });

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
        mWindowLayoutParams.gravity = Gravity.RIGHT|Gravity.TOP;
        mWindowLayoutParams.windowAnimations = R.style.bottom_anim;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        setShow(true);
        mWindowManager.addView(mLayout, mWindowLayoutParams);
        mHandler.postDelayed(()->{
            dissmiss();
        },5000);
    }

    public void showScreenshot(Uri uri){
        if(isShow() || uri == null){
            return;
        }
        try{
            Bitmap bitmap = BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(uri));
            mIvScreenshot.setImageBitmap(bitmap);
            mUri = uri;
        } catch (Exception e){
            return;
        }
        show();
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
