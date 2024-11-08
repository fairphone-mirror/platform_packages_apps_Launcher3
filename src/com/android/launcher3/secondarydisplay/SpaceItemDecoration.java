package com.android.launcher3.secondarydisplay;

import android.graphics.Rect;
import android.view.View;


import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SpaceItemDecoration extends RecyclerView.ItemDecoration {
    private int space;

    public SpaceItemDecoration(int space) {
        this.space = space;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            if (((LinearLayoutManager) layoutManager).getOrientation() == LinearLayoutManager.VERTICAL) {
                outRect.top = space;
                outRect.left = 0;
                outRect.right = 0;
            } else {
                if (parent.getChildLayoutPosition(view) != layoutManager.getItemCount() - 1) {
                    outRect.right = space;
                }
                outRect.top = 0;
                outRect.bottom = 0;
                outRect.left = 0;
            }
        }
    }
}
