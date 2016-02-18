package net.jgilje.pideo;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.View;

/**
 * Created by jgilje on 17.02.16.
 */
public class ContextMenuRecyclerView extends RecyclerView {
    private ContextMenuInfo mContextMenuInfo = new ContextMenuInfo();

    public ContextMenuRecyclerView(Context context) {
        super(context);
    }

    public ContextMenuRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ContextMenuRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        RecyclerViewAdapter adapter = (RecyclerViewAdapter) getAdapter();
        int position = getChildAdapterPosition(originalView);
        RecyclerViewAdapter.ListEntry entry = adapter.getEntry(position);
        mContextMenuInfo.position = position;
        mContextMenuInfo.hostname = entry.hostname;
        mContextMenuInfo.port = entry.port;
        return super.showContextMenuForChild(originalView);
    }

    @Override
    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    public static class ContextMenuInfo implements ContextMenu.ContextMenuInfo {
        public int position;
        public String hostname;
        public int port;
    }
}
