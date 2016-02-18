package net.jgilje.pideo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jgilje on 17.02.16.
 */
public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
    private final TypedValue mTypedValue = new TypedValue();
    private final Context mContext;
    private int mBackground;
    private List<ListEntry> entries = new ArrayList<>();

    public static class ListEntry {
        String displayName;
        String hostname;
        int port;
        boolean autodetected;

        public ListEntry(String displayName, String hostname, int port, boolean autodetected) {
            this.displayName = displayName;
            this.hostname = hostname;
            this.port = port;
            this.autodetected = autodetected;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ImageView mImageView;
        public final TextView mAddressTextView;
        public final TextView mDescriptionTextView;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mImageView = (ImageView) view.findViewById(R.id.list_item_icon);
            mAddressTextView = (TextView) view.findViewById(R.id.list_item_address);
            mDescriptionTextView = (TextView) view.findViewById(R.id.list_item_description);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mAddressTextView.getText();
        }
    }

    public RecyclerViewAdapter(Context context) {
        context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
        mBackground = mTypedValue.resourceId;
        mContext = context;
    }

    public ListEntry getEntry(int position) {
        return entries.get(position);
    }

    public void addEntry(String displayName, String canonicalHostName, int port) {
        int pos = entries.size();
        entries.add(new ListEntry(displayName, canonicalHostName, port, true));
        notifyItemInserted(pos);
    }

    public void addEntry(String canonicalHostName, int port) {
        int pos = entries.size();
        entries.add(new ListEntry(canonicalHostName, canonicalHostName, port, false));
        notifyItemInserted(pos);
    }

    public void removeEntry(String canonicalHostName, int port, boolean autodetected) {
        for (int i = 0; i < entries.size(); i++) {
            ListEntry entry = entries.get(i);
            if (entry.hostname.equals(canonicalHostName) &&
                    entry.port == port &&
                    entry.autodetected == autodetected) {
                entries.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void removeEntry(int position) {
        entries.remove(position);
        notifyItemRemoved(position);
    }

    public void clearEntries() {
        int items = entries.size();
        entries.clear();
        notifyItemRangeRemoved(0, items);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        view.setBackgroundResource(mBackground);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                RecyclerView rv = (RecyclerView) v.getParent();
                int i = rv.getChildAdapterPosition(v);
                ListEntry entry = entries.get(i);

                // only manual entries get context-menu, to remove the entry
                if (! entry.autodetected) {
                    v.showContextMenu();
                }

                return true;
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecyclerView rv = (RecyclerView) v.getParent();
                int i = rv.getChildAdapterPosition(v);
                ListEntry entry = entries.get(i);

                Context context = v.getContext();
                Intent intent = new Intent(context, VideoActivity.class);
                intent.putExtra(VideoActivity.SERVERHOST_EXTRA, entry.hostname);
                intent.putExtra(VideoActivity.SERVERPORT_EXTRA, entry.port);
                context.startActivity(intent);
            }
        });

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final ListEntry entry = entries.get(position);
        holder.mAddressTextView.setText(entry.displayName);
        if (entry.autodetected) {
            holder.mDescriptionTextView.setVisibility(View.GONE);
        } else {
            holder.mDescriptionTextView.setVisibility(View.VISIBLE);
            holder.mDescriptionTextView.setText(mContext.getResources().getString(R.string.manually_added));
        }
        /*
        holder.mDescriptionTextView.setText(entry.autodetected ?
                "" : );
                */
        holder.mImageView.setImageResource(R.drawable.ic_videocam_black_24dp);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
