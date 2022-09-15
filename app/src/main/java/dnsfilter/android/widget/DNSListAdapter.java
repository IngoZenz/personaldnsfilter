package dnsfilter.android.widget;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import dnsfilter.android.R;

public class DNSListAdapter extends ArrayAdapter<DNSServerConfigEntry> implements DNSServerConfigEntryView.EditEventsListener {

    private EventsListener listener;
    private final DNSConfigEntryValidator validator = new DNSConfigEntryValidator();

    private final DNSServerConfigEntryView dnsServerConfigEntryView;

    public DNSListAdapter(Context context, List<DNSServerConfigEntry> objects, EventsListener listener) {
        super(context, 0, objects);
        this.listener = listener;

        dnsServerConfigEntryView = new DNSServerConfigEntryView(context, this);
    }

    @Override
    public int getCount() {
        return super.getCount() + 1;
    }

    public int getObjectsCount() {
        return super.getCount();
    }

    @Override
    public int getItemViewType(int position) {
        if (position > super.getCount() - 1) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (getItemViewType(position) == 1) {
            convertView = getAddButton(parent);
        } else {
            convertView = getItemView(position, convertView, parent);
        }

        return convertView;
    }

    private View getAddButton(ViewGroup parent) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistnewitem, parent, false);
        view.findViewById(R.id.addNewItemButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DNSServerConfigEntry newEntry = new DNSServerConfigEntry();
                add(newEntry);
                if (listener != null) {
                    listener.onItemAdded();
                }
                dnsServerConfigEntryView.showEntry(newEntry, true);
            }
        });
        return view;
    }

    private View getItemView(int position, View convertView, ViewGroup parent) {
        DNSServerConfigEntry entry = getItem(position);

        DNSServerConfigEntryViewHolder holder;

        if (convertView == null || !(convertView.getTag() instanceof DNSServerConfigEntryViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistitem, parent, false);

            holder = new DNSServerConfigEntryViewHolder();
            setupNewViewHolder(holder, convertView);
            convertView.setTag(holder);

        } else {
            holder = (DNSServerConfigEntryViewHolder) convertView.getTag();
        }

        holder.dnsServerConfigEntry = entry;

        holder.protocolView.setText(entry.getProtocol().toString());
        holder.ipView.setText(entry.getIp());
        holder.portView.setText(entry.getPort());
        View finalConvertView = convertView;
        holder.isActiveEntryCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    entry.setIsActive(isChecked);
                    finalConvertView.setEnabled(entry.getIsActive());
                }
        );
        holder.isActiveEntryCheckbox.setChecked(entry.getIsActive());

        convertView.setEnabled(entry.getIsActive());



        return convertView;
    }

    private void setupNewViewHolder(DNSServerConfigEntryViewHolder holder, View convertView) {
        findViews(holder, convertView);

        holder.deleteEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    remove(holder.dnsServerConfigEntry);
                }
            }
        });

        holder.editEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dnsServerConfigEntryView.showEntry(holder.dnsServerConfigEntry, false);
            }
        });
    }

    public boolean validate() {
        boolean result = true;
        for (int i = 0; i <= getObjectsCount() - 1; i++) {
            DNSServerConfigEntry item = getItem(i);
            item.setValidationResult(validator.validate(item, this.getContext()));
            if (item.getValidationResult().hasError()) {
                result = false;
            }
        }
        return result;
    }

    private void findViews(DNSServerConfigEntryViewHolder holder, View convertView) {
        holder.root = convertView.findViewById(R.id.container);
        holder.protocolView = convertView.findViewById(R.id.protocolText);
        holder.ipView = convertView.findViewById(R.id.ipText);
        holder.portView = convertView.findViewById(R.id.portText);
        holder.deleteEntryButton = convertView.findViewById(R.id.deleteEntryButton);
        holder.editEntryButton = convertView.findViewById(R.id.editEntryButton);
        holder.isActiveEntryCheckbox = convertView.findViewById(R.id.isActiveEntryCheckbox);
    }

    @Override
    public void onApplyChanges() {
        notifyDataSetChanged();
    }

    @Override
    public void onNewCancelled(DNSServerConfigEntry entry) {
        remove(entry);
    }

    static class DNSServerConfigEntryViewHolder {
        DNSServerConfigEntry dnsServerConfigEntry;
        TextView protocolView;
        TextView ipView;
        TextView portView;
        CheckBox isActiveEntryCheckbox;
        ImageButton deleteEntryButton;
        ImageButton editEntryButton;
        RelativeLayout root;
    }

    public interface EventsListener {
        void onItemAdded();

        void onTestEntry(DNSServerConfigEntry entry);
    }
}
