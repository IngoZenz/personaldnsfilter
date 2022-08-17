package dnsfilter.android.widget;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import dnsfilter.android.R;

public class DNSServerConfigEntryView {
    private final Dialog editEntryDialog;
    private final EditText editIpView;
    private final EditText editPortView;
    private final EditText editEndpointView;
    private final Spinner editProtocolSpinner;
    private final Button applyChangesButton;
    private final Button cancelChangesButton;
    private final EditEventsListener listener;

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
        ArrayAdapter<DNSType> spinnerAdapter = new ArrayAdapter<>(
                context,
                R.layout.spinneritem,
                DNSType.values()
        );
        editProtocolSpinner.setAdapter(spinnerAdapter);
        editEntryDialog.setTitle("Edit entry");
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
        editEntryDialog.show();
        applyChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                entry.setIp(editIpView.getText().toString());
                entry.setPort(editPortView.getText().toString());
                entry.setEndpoint(editEndpointView.getText().toString());
                entry.setProtocol(DNSType.values()[editProtocolSpinner.getSelectedItemPosition()]);
                if (listener != null) {
                    listener.onApplyChanges();
                }
                editEntryDialog.dismiss();
            }
        });
        cancelChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNew && listener != null) {
                    listener.onNewCancelled(entry);
                }
                editEntryDialog.cancel();
            }
        });

    }

    interface EditEventsListener {
        void onApplyChanges();
        void onNewCancelled(DNSServerConfigEntry entry);
    }

}
