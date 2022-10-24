package dnsfilter.android.dnsserverconfig.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import dnsfilter.android.R;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSServerConfigEntryView {

    private final Dialog editEntryDialog;
    private final EditText editIpView;
    private final EditText editPortView;
    private final EditText editEndpointView;
    private final Spinner editProtocolSpinner;
    private final Button applyChangesButton;
    private final Button cancelChangesButton;
    private final Button deleteEntryButton;
    private final EditEventsListener listener;

    private final DNSConfigEntryValidator validator;

    DNSServerConfigEntryView(Context context, EditEventsListener listener) {
        this.listener = listener;
        editEntryDialog = new Dialog(context, R.style.Theme_dialog_TitleBar);
        editEntryDialog.setContentView(R.layout.dnsserverconfigentryedititem);
        editIpView = editEntryDialog.findViewById(R.id.editIpText);
        editPortView = editEntryDialog.findViewById(R.id.editPortText);
        editEndpointView = editEntryDialog.findViewById(R.id.editEndpointText);
        editProtocolSpinner = editEntryDialog.findViewById(R.id.editProtocolSpinner);
        applyChangesButton = editEntryDialog.findViewById(R.id.editApplyChanges);
        cancelChangesButton = editEntryDialog.findViewById(R.id.editCancelChanges);
        deleteEntryButton = editEntryDialog.findViewById(R.id.deleteEntryButton);

        ArrayAdapter<DNSType> spinnerAdapter = new ArrayAdapter<>(
                context,
                R.layout.spinneritem,
                DNSType.values()
        );
        editProtocolSpinner.setAdapter(spinnerAdapter);
        editEntryDialog.setTitle(R.string.editEntry);

        validator = new DNSConfigEntryValidator(context);
    }

    public void showEntry(DNSServerConfigEntry entry, boolean isNew) {
        editIpView.setText(entry.getIp());
        editPortView.setText(entry.getPort());
        editEndpointView.setText(entry.getEndpoint());
        editProtocolSpinner.setSelection(entry.getProtocol().ordinal());
        editProtocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                editPortView.setText(Integer.toString(DNSType.values()[position].defaultPort));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        editEntryDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                entry.setValidationResult(new DNSServerConfigEntryValidationResult());
                if (isNew && listener != null) {
                    listener.onNewCancelled(entry);
                }
            }
        });
        editEntryDialog.show();
        applyChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DNSServerConfigEntry testingValue = new DNSServerConfigEntry(
                        editIpView.getText().toString(),
                        editPortView.getText().toString(),
                        DNSType.values()[editProtocolSpinner.getSelectedItemPosition()],
                        editEndpointView.getText().toString(),
                        true
                );
                entry.setValidationResult(validator.validate(testingValue));
                if (entry.getValidationResult().hasError()) {
                    setupErrors(entry.getValidationResult());
                } else {
                    entry.setIp(editIpView.getText().toString());
                    entry.setPort(editPortView.getText().toString());
                    entry.setEndpoint(editEndpointView.getText().toString());
                    entry.setProtocol(DNSType.values()[editProtocolSpinner.getSelectedItemPosition()]);
                    if (listener != null) {
                        listener.onApplyChanges();
                    }
                    editEntryDialog.dismiss();
                }
            }
        });
        cancelChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editEntryDialog.cancel();
            }
        });
        deleteEntryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editEntryDialog.cancel();
                listener.onDeleteItem(entry);
            }
        });
        editEntryDialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        entry.setValidationResult(new DNSServerConfigEntryValidationResult());
                    }
                }
        );
    }

    private void setupErrors(DNSServerConfigEntryValidationResult validationResult) {
        if (validationResult.getIpError() != null && !validationResult.getIpError().isEmpty()) {
            editIpView.setError(validationResult.getIpError());
        } else {
            editIpView.setError(null);
        }
        if (validationResult.getPortError() != null && !validationResult.getPortError().isEmpty()) {
            editPortView.setError(validationResult.getPortError());
        } else {
            editPortView.setError(null);
        }
    }

    interface EditEventsListener {
        void onApplyChanges();

        void onNewCancelled(DNSServerConfigEntry entry);

        void onDeleteItem(DNSServerConfigEntry entry);
    }
}
