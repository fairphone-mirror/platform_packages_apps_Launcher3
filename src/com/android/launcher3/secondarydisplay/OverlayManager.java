package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.secondarydisplay.BaseOverlayView.TYPE_TASKBAR;

import com.android.launcher3.secondarydisplay.BaseOverlayView;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OverlayManager {

    private final List<BaseOverlayView> mOverlayList = new CopyOnWriteArrayList<>();

    private OverlayManager(){}
    private static class OverlayManagerHolder {
        private static final OverlayManager INSTANCE = new OverlayManager();
    }
    
    public static final OverlayManager getInstance(){
        return OverlayManagerHolder.INSTANCE;
    }

    protected void addOverlayView(BaseOverlayView view){
        mOverlayList.add(view);
    }

    protected void dissmissAll(){
        for(BaseOverlayView overlayView : mOverlayList){
            if(!overlayView.isOfViewType(TYPE_TASKBAR)){
                overlayView.dissmiss();
            }
        }
    }

    protected void release(){
        mOverlayList.clear();
    }
}
