package dnsfilter.android.widget;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import dnsfilter.android.R;

public class DNSListAdapter extends ArrayAdapter<DNSServerConfigEntry> {

    private final Float shift;
    private EventsListener listener;
    private final Animation progressBarAnim;
    private final Dialog testResultDialog;
    private final ImageView testResultImage;
    private final TextView testResultText;
    private final DNSConfigEntryValidator validator = new DNSConfigEntryValidator();

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

        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(context, android.R.style.Theme_Holo));
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dnsserverconfigtestresult, null);
        builder.setView(dialogView);

        progressBarAnim = AnimationUtils.loadAnimation(context, R.anim.progress_rotation);
        progressBarAnim.setRepeatCount(Animation.INFINITE);

        this.testResultDialog = builder.create();
        this.testResultImage = dialogView.findViewById(R.id.resultIconImageView);
        this.testResultText = dialogView.findViewById(R.id.resultTextView);
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
            setupNewViewHolder(holder, convertView);
            convertView.setTag(holder);

        } else {
            holder = (DNSServerConfigEntryViewHolder) convertView.getTag();
        }

        if (entry.getProtocol() != null) {
            holder.protocolSpinner.setSelection(entry.getProtocol().ordinal());
        }

        holder.dnsServerConfigEntry = entry;
        updateOptionMenu(holder.optionMenuButtons, entry.isExpanded());

        switch (entry.getTestResult().getTestState()) {
            case FAIL:
                if (entry.isExpanded()) {
                    holder.testEntryResultButton.setImageResource(R.drawable.ic_exclamation_circle);
                    holder.testEntryResultButton.setVisibility(View.VISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                    holder.testEntryButton.setVisibility(View.INVISIBLE);
                } else {
                    holder.testEntryResultButton.setVisibility(View.INVISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                    holder.testEntryButton.setVisibility(View.VISIBLE);
                    holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult());
                }
                break;
            case STARTED:
                if (entry.isExpanded()) {
                    holder.testEntryResultButton.setVisibility(View.INVISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.VISIBLE);
                    holder.testEntryButton.setVisibility(View.INVISIBLE);
                } else {
                    holder.testEntryResultButton.setVisibility(View.INVISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                    holder.testEntryButton.setVisibility(View.VISIBLE);
                    holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult());
                }
                break;
            case SUCCESS:
                if (entry.isExpanded()) {
                    holder.testEntryResultButton.setImageResource(R.drawable.ic_check_circle);
                    holder.testEntryResultButton.setVisibility(View.VISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                    holder.testEntryButton.setVisibility(View.INVISIBLE);
                } else {
                    holder.testEntryResultButton.setVisibility(View.INVISIBLE);
                    holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                    holder.testEntryButton.setVisibility(View.VISIBLE);
                    holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult());
                }
                break;
            case NOT_STARTED:
                holder.testEntryResultButton.setVisibility(View.INVISIBLE);
                holder.testEntryProgressBar.setVisibility(View.INVISIBLE);
                holder.testEntryButton.setVisibility(View.VISIBLE);
                break;
        }

        holder.ipView.setText(entry.getIp());
        holder.ipView.setError(entry.getValidationResult().getIpError());
        holder.portView.setText(entry.getPort());
        holder.portView.setError(entry.getValidationResult().getPortError());
        holder.endpointView.setText(entry.getEndpoint());
        holder.isActiveEntryCheckbox.setChecked(entry.getIsActive());

        convertView.setEnabled(entry.getIsActive());

        View finalConvertView = convertView;
        holder.isActiveEntryCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    entry.setIsActive(isChecked);
                    finalConvertView.setEnabled(entry.getIsActive());
                }
        );

        if (holder.testEntryProgressBar.getVisibility() == View.VISIBLE) {
            holder.testEntryProgressBar.startAnimation(progressBarAnim);
        } else {
            holder.testEntryProgressBar.clearAnimation();
        }
        return convertView;
    }

    private void setupNewViewHolder(DNSServerConfigEntryViewHolder holder, View convertView) {
        findViews(holder, convertView);

        holder.protocolSpinner.setAdapter(spinnerAdapter);
        holder.protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (holder.dnsServerConfigEntry.getProtocol() != DNSType.values()[position]) {
                    holder.dnsServerConfigEntry.setProtocol(DNSType.values()[position]);
                    holder.dnsServerConfigEntry.setPort(Integer.toString(DNSType.values()[position].defaultPort));
                    holder.portView.setText(holder.dnsServerConfigEntry.getPort());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

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

        holder.testEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.STARTED));
                    notifyDataSetChanged();
                    DNSServerConfigEntryValidationResult validationResult = validator.validate(
                            holder.dnsServerConfigEntry,
                            getContext()
                    );
                    holder.dnsServerConfigEntry.setValidationResult(validationResult);
                    if (validationResult.hasError()) {
                        holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                        notifyDataSetChanged();
                        return;
                    }
                    listener.onTestEntry(holder.dnsServerConfigEntry);
                }
            }
        });
        holder.deleteEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    remove(holder.dnsServerConfigEntry);
                    holder.ipView.setError(null);
                    holder.portView.setError(null);
                }
            }
        });
        holder.moreOptionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder.dnsServerConfigEntry.setExpanded(!holder.dnsServerConfigEntry.isExpanded());
                if (!holder.dnsServerConfigEntry.isExpanded()) {
                    holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                }
                notifyDataSetChanged();
            }
        });

        holder.testEntryResultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder.dnsServerConfigEntry.getTestResult().getTestState() == DNSServerConfigEntryTestState.SUCCESS) {
                    testResultImage.setImageResource(R.drawable.ic_check_circle);
                    testResultText.setText(v.getContext().getString(R.string.testDNSResultSuccess, holder.dnsServerConfigEntry.getTestResult().getPerf()));
                    testResultDialog.show();
                } else if (holder.dnsServerConfigEntry.getTestResult().getTestState() == DNSServerConfigEntryTestState.FAIL) {
                    testResultImage.setImageResource(R.drawable.ic_exclamation_circle);
                    testResultText.setText(v.getContext().getString(R.string.testDNSResultFailure, holder.dnsServerConfigEntry.getTestResult().getMessage()));
                    testResultDialog.show();
                }
                holder.dnsServerConfigEntry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                notifyDataSetChanged();
            }
        });
    }

    private void updateOptionMenu(View[] buttonsToHide, boolean isExpandedMenu) {

        ViewGroup.LayoutParams[] layoutParams = new ViewGroup.LayoutParams[buttonsToHide.length];
        for (int i = 0; i <= buttonsToHide.length - 1; i++) {
            layoutParams[i] = buttonsToHide[i].getLayoutParams();
            if (isExpandedMenu) {
                layoutParams[i].width = Math.round(shift);
            } else {
                layoutParams[i].width = 0;
            }
        }

        for (int i = 0; i <= buttonsToHide.length - 1; i++) {
            buttonsToHide[i].setLayoutParams(layoutParams[i]);
        }
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
        holder.protocolSpinner = convertView.findViewById(R.id.dnsProtocolSpinner);
        holder.ipView = convertView.findViewById(R.id.ipEditText);
        holder.portView = convertView.findViewById(R.id.portEditText);
        holder.endpointView = convertView.findViewById(R.id.endpointEditText);
        holder.testEntryButton = convertView.findViewById(R.id.testEntryButton);
        holder.deleteEntryButton = convertView.findViewById(R.id.deleteEntryButton);
        holder.moreOptionsButton = convertView.findViewById(R.id.moreOptionsButton);
        holder.isActiveEntryCheckbox = convertView.findViewById(R.id.isActiveEntryCheckbox);
        holder.testEntryProgressBar = convertView.findViewById(R.id.testEntryProgressBar);
        holder.testEntryResultButton = convertView.findViewById(R.id.testEntryResultButton);
        holder.optionMenuButtons = new View[]{holder.testEntryButton, holder.deleteEntryButton};
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
        ImageView testEntryProgressBar;
        ImageButton testEntryResultButton;
        ImageButton deleteEntryButton;
        RelativeLayout root;
        View[] optionMenuButtons;
    }

    public interface EventsListener {
        void onItemAdded();

        void onTestEntry(DNSServerConfigEntry entry);
    }
}
