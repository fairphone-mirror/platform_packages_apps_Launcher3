package com.android.launcher3.secondarydisplay;

import android.view.View;
import com.android.launcher3.model.data.ItemInfo;


public interface SecondaryTaskBarListner {

    void onStartButtonClick();

    void onStatusBarClick();

    void onIconClick(View v, ItemInfo info,int taskId);
    
    void onRecentClick();

    void onHomeClick();

    void onCalendarClick();
}
