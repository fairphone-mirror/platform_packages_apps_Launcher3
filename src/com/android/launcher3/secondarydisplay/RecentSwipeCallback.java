package com.android.launcher3.secondarydisplay;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.secondarydisplay.SecondaryRecentAdapter;

public class RecentSwipeCallback extends ItemTouchHelper.Callback {
    private SecondaryRecentAdapter mAdapter;
    public RecentSwipeCallback(SecondaryRecentAdapter adapter){
        this.mAdapter = adapter;
    }
    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = 0;
        int swipeFlags = 0;
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            if (((LinearLayoutManager) recyclerView.getLayoutManager()).getOrientation() == LinearLayoutManager.HORIZONTAL) {
                swipeFlags = ItemTouchHelper.DOWN | ItemTouchHelper.UP;
                dragFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(dragFlags,swipeFlags);
            } else {
                swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                dragFlags = ItemTouchHelper.DOWN | ItemTouchHelper.UP;
                return makeMovementFlags(dragFlags,swipeFlags);
            }
        } else {
            return 0;
        }
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        mAdapter.removeTask(position);
    }
}
