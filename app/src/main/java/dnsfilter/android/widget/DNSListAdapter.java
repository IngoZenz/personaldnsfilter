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

public class DNSListAdapter extends ArrayAdapter<DNSRecord> {

    EventsListener listener = null;

    private final ArrayAdapter<DNSType> spinnerAdapter = new ArrayAdapter<>(
            getContext(),
            R.layout.spinneritem,
            DNSType.values()
    );

    private final View.OnClickListener addButtonListener = v -> {
        add(new DNSRecord());
        if (listener != null) {
            listener.onItemAdded();
        }
    };

    public DNSListAdapter(Context context, List<DNSRecord> objects, EventsListener listener) {
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
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dnsrecordlistnewitem, parent, false);
        view.findViewById(R.id.addNewItemButton).setOnClickListener(addButtonListener);
        return view;
    }

    private View getItem(int position, View convertView, ViewGroup parent) {
        DNSRecord record = getItem(position);

        DNSRecordViewHolder holder;

        if (convertView == null || !(convertView.getTag() instanceof DNSRecordViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsrecordlistitem, parent, false);

            holder = new DNSRecordViewHolder();

            holder.protocolSpinner = convertView.findViewById(R.id.dnsProtocolSpinner);
            holder.protocolSpinner.setAdapter(spinnerAdapter);
            holder.protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    holder.dnsRecord.setProtocol(DNSType.values()[position]);
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
                    holder.dnsRecord.setIp(s.toString());
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
                            holder.dnsRecord.setPort(s.toString());
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
                            holder.dnsRecord.setEndpoint(s.toString());
                        }
                    }
            );
            holder.deleteRecordButton = convertView.findViewById(R.id.deleteRecordButton);
            holder.isActiveRecordCheckbox = convertView.findViewById(R.id.isActiveRecordCheckbox);

        } else {
            holder = (DNSRecordViewHolder) convertView.getTag();
        }

        if (record.getProtocol() != null) {
            holder.protocolSpinner.setSelection(record.getProtocol().ordinal());
        }

        holder.dnsRecord = record;
        holder.ipView.setText(record.getIp());
        holder.portView.setText(record.getPort());
        holder.endpointView.setText(record.getEndpoint());
        holder.deleteRecordButton.setOnClickListener(v -> remove(getItem(position)));
        holder.isActiveRecordCheckbox.setChecked(record.getIsActive());

        convertView.setEnabled(record.getIsActive());

        View finalConvertView = convertView;
        holder.isActiveRecordCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    record.setIsActive(isChecked);
                    finalConvertView.setEnabled(record.getIsActive());
                }
        );
        return convertView;
    }

    static class DNSRecordViewHolder {
        DNSRecord dnsRecord;
        Spinner protocolSpinner;
        EditText ipView;
        EditText portView;
        EditText endpointView;
        View deleteRecordButton;
        CheckBox isActiveRecordCheckbox;
    }

    public interface EventsListener {
        void onItemAdded();
    }
}
