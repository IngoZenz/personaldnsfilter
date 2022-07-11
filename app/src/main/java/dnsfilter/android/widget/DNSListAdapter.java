package dnsfilter.android.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.List;

import dnsfilter.android.R;

public class DNSListAdapter extends ArrayAdapter<DNSServerConfigEntry> {

    EventsListener listener;

    private final ArrayAdapter<DNSType> spinnerAdapter = new ArrayAdapter<>(
            getContext(),
            R.layout.spinneritem,
            DNSType.values()
    );

    private final View.OnClickListener addButtonListener = v -> {
        add(new DNSServerConfigEntry());
        if (listener != null) {
            listener.onItemAdded();
        }
    };

    public DNSListAdapter(Context context, List<DNSServerConfigEntry> objects, EventsListener listener) {
        super(context, 0, objects);
        this.listener = listener;
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
            convertView = getItem(position, convertView, parent);
        }

        return convertView;
    }

    private View getAddButton(ViewGroup parent) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistnewitem, parent, false);
        view.findViewById(R.id.addNewItemButton).setOnClickListener(addButtonListener);
        return view;
    }

    private View getItem(int position, View convertView, ViewGroup parent) {
        DNSServerConfigEntry entry = getItem(position);

        DNSServerConfigEntryViewHolder holder;

        if (convertView == null || !(convertView.getTag() instanceof DNSServerConfigEntryViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistitem, parent, false);

            holder = new DNSServerConfigEntryViewHolder();

            holder.protocolSpinner = convertView.findViewById(R.id.dnsProtocolSpinner);
            holder.protocolSpinner.setAdapter(spinnerAdapter);
            holder.protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    holder.dnsServerConfigEntry.setProtocol(DNSType.values()[position]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            holder.ipView = convertView.findViewById(R.id.ipEditText);
            holder.ipView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    holder.dnsServerConfigEntry.setIp(s.toString());
                }
            });
            holder.portView = convertView.findViewById(R.id.portEditText);
            holder.portView.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            holder.dnsServerConfigEntry.setPort(s.toString());
                        }
                    }
            );
            holder.endpointView = convertView.findViewById(R.id.endpointEditText);
            holder.endpointView.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            holder.dnsServerConfigEntry.setEndpoint(s.toString());
                        }
                    }
            );
            holder.deleteEntryButton = convertView.findViewById(R.id.deleteEntryButton);
            holder.isActiveEntryCheckbox = convertView.findViewById(R.id.isActiveEntryCheckbox);

        } else {
            holder = (DNSServerConfigEntryViewHolder) convertView.getTag();
        }

        if (entry.getProtocol() != null) {
            holder.protocolSpinner.setSelection(entry.getProtocol().ordinal());
        }

        holder.dnsServerConfigEntry = entry;
        holder.ipView.setText(entry.getIp());
        holder.portView.setText(entry.getPort());
        holder.endpointView.setText(entry.getEndpoint());
        holder.deleteEntryButton.setOnClickListener(v -> remove(getItem(position)));
        holder.isActiveEntryCheckbox.setChecked(entry.getIsActive());

        convertView.setEnabled(entry.getIsActive());

        View finalConvertView = convertView;
        holder.isActiveEntryCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    entry.setIsActive(isChecked);
                    finalConvertView.setEnabled(entry.getIsActive());
                }
        );
        return convertView;
    }

    static class DNSServerConfigEntryViewHolder {
        DNSServerConfigEntry dnsServerConfigEntry;
        Spinner protocolSpinner;
        EditText ipView;
        EditText portView;
        EditText endpointView;
        View deleteEntryButton;
        CheckBox isActiveEntryCheckbox;
    }

    public interface EventsListener {
        void onItemAdded();
    }
}
