package dnsfilter.android.widget;

import android.animation.LayoutTransition;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dnsfilter.android.R;

public class DNSListAdapter extends ArrayAdapter<DNSServerConfigEntry> {

    Float shift;
    EventsListener listener;
    LayoutTransition layoutTransition = new LayoutTransition();
    List<DNSServerConfigEntry> expandedEntries = new ArrayList<>();

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
        this.shift = 44 * ((float) context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
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
        view.findViewById(R.id.addNewItemButton).setOnClickListener(addButtonListener);
        return view;
    }

    private View getItemView(int position, View convertView, ViewGroup parent) {
        DNSServerConfigEntry entry = getItem(position);

        DNSServerConfigEntryViewHolder holder;

        if (convertView == null || !(convertView.getTag() instanceof DNSServerConfigEntryViewHolder)) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.dnsserverconfigentrylistitem, parent, false);

            holder = new DNSServerConfigEntryViewHolder();

            holder.root = convertView.findViewById(R.id.container);
            holder.root.setLayoutTransition(layoutTransition);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            }
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
            holder.testEntryButton = convertView.findViewById(R.id.testEntryButton);
            holder.deleteEntryButton = convertView.findViewById(R.id.deleteEntryButton);
            holder.moreOptionsButton = convertView.findViewById(R.id.moreOptionsButton);
            holder.moreOptionsButton.setOnClickListener(v -> {
                holder.isExpandedMenu = !holder.isExpandedMenu;
                ViewGroup.LayoutParams paramsButtonOne = holder.testEntryButton.getLayoutParams();
                ViewGroup.LayoutParams paramsButtonTwo = holder.deleteEntryButton.getLayoutParams();
                if (holder.isExpandedMenu) {
                    paramsButtonOne.width = Math.round(shift);
                    paramsButtonTwo.width = Math.round(shift);
                    expandedEntries.add(holder.dnsServerConfigEntry);
                } else {
                    paramsButtonOne.width = 0;
                    paramsButtonTwo.width = 0;
                    expandedEntries.remove(holder.dnsServerConfigEntry);
                }

                holder.testEntryButton.setLayoutParams(paramsButtonOne);
                holder.deleteEntryButton.setLayoutParams(paramsButtonTwo);
            });
            holder.isActiveEntryCheckbox = convertView.findViewById(R.id.isActiveEntryCheckbox);
        } else {
            holder = (DNSServerConfigEntryViewHolder) convertView.getTag();
        }

        if (entry.getProtocol() != null) {
            holder.protocolSpinner.setSelection(entry.getProtocol().ordinal());
        }

        holder.dnsServerConfigEntry = entry;
        if (expandedEntries.contains(entry)) {
            holder.isExpandedMenu = true;
            ViewGroup.LayoutParams paramsButtonOne = holder.testEntryButton.getLayoutParams();
            ViewGroup.LayoutParams paramsButtonTwo = holder.deleteEntryButton.getLayoutParams();
            paramsButtonOne.width = Math.round(shift);
            paramsButtonTwo.width = Math.round(shift);
            holder.testEntryButton.setLayoutParams(paramsButtonOne);
            holder.deleteEntryButton.setLayoutParams(paramsButtonTwo);
        }
        holder.ipView.setText(entry.getIp());
        holder.portView.setText(entry.getPort());
        holder.endpointView.setText(entry.getEndpoint());
        holder.deleteEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expandedEntries.remove(holder.dnsServerConfigEntry);
                remove(holder.dnsServerConfigEntry);
            }
        });
        holder.testEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
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
        ImageButton moreOptionsButton;
        CheckBox isActiveEntryCheckbox;
        ImageButton testEntryButton;
        ImageButton deleteEntryButton;
        boolean isExpandedMenu = false;
        RelativeLayout root;
    }

    public interface EventsListener {
        void onItemAdded();

        void onTestEntry();
    }
}
