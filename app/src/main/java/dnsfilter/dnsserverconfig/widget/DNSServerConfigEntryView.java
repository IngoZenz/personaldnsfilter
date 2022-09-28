package dnsfilter.dnsserverconfig.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dnsfilter.DNSServer;
import dnsfilter.android.R;

public class DNSServerConfigEntryView {

    private static final int DEFAULT_DNS_TIMEOUT = 15000;

    private final Dialog editEntryDialog;
    private final EditText editIpView;
    private final EditText editPortView;
    private final EditText editEndpointView;
    private final Spinner editProtocolSpinner;
    private final Button applyChangesButton;
    private final Button cancelChangesButton;
    private final EditEventsListener listener;

    private final Button testChangesButton;
    private final Button testEntryProgressBar;
    private final ImageButton testEntryResultSuccess;
    private final ImageButton testEntryResultFailure;

    private final Dialog testResultDialog;
    private final ImageView testResultImage;
    private final TextView testResultText;

    private final Animation progressBarAnim;

    private final ExecutorService testTasksPool = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
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
        testChangesButton = editEntryDialog.findViewById(R.id.testEntryButton);
        testEntryProgressBar = editEntryDialog.findViewById(R.id.testEntryProgressBar);
        testEntryResultSuccess = editEntryDialog.findViewById(R.id.testEntryButtonResultSuccess);
        testEntryResultFailure = editEntryDialog.findViewById(R.id.testEntryButtonResultFailure);

        ArrayAdapter<DNSType> spinnerAdapter = new ArrayAdapter<>(
                context,
                R.layout.spinneritem,
                DNSType.values()
        );
        editProtocolSpinner.setAdapter(spinnerAdapter);
        editEntryDialog.setTitle(R.string.editEntry);
        progressBarAnim = AnimationUtils.loadAnimation(context, R.anim.progress_rotation);
        progressBarAnim.setRepeatCount(Animation.INFINITE);

        testResultDialog = new Dialog(context);
        testResultDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dnsentrytestresult, null);
        testResultDialog.setContentView(dialogView);
        testResultImage = dialogView.findViewById(R.id.resultIconImageView);
        testResultText = dialogView.findViewById(R.id.resultTextView);
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
                entry.setTestResult(new DNSServerConfigTestResult());
                if (isNew && listener != null) {
                    listener.onNewCancelled(entry);
                }
            }
        });
        setupTestButtons(entry.getTestResult().getTestState());
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
        editEntryDialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        entry.setValidationResult(new DNSServerConfigEntryValidationResult());
                        entry.setTestResult(new DNSServerConfigTestResult());
                    }
                }
        );
        testChangesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                entry.getTestResult().setTestState(DNSServerConfigEntryTestState.STARTED);
                setupTestButtons(entry.getTestResult().getTestState());
                DNSServerConfigEntry testingValue = new DNSServerConfigEntry(
                        editIpView.getText().toString(),
                        editPortView.getText().toString(),
                        DNSType.values()[editProtocolSpinner.getSelectedItemPosition()],
                        editEndpointView.getText().toString(),
                        true
                );
                entry.setValidationResult(validator.validate(testingValue));
                if (entry.getValidationResult().hasError()) {
                    entry.getTestResult().setTestState(DNSServerConfigEntryTestState.NOT_STARTED);
                    setupTestButtons(entry.getTestResult().getTestState());
                    setupErrors(entry.getValidationResult());
                } else {
                    Runnable testTask = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                long result = DNSServer.getInstance()
                                        .createDNSServer(testingValue.toString(), DEFAULT_DNS_TIMEOUT)
                                        .testDNS(5);
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.SUCCESS, result));
                                        setupTestButtons(entry.getTestResult().getTestState());
                                    }
                                });
                            } catch (IOException e) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.FAIL, e.getMessage()));
                                        setupTestButtons(entry.getTestResult().getTestState());
                                    }
                                });
                            }
                        }
                    };
                    testTasksPool.execute(testTask);
                }
            }
        });
        testEntryResultFailure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testResultImage.setImageResource(R.drawable.ic_exclamation_circle);
                testResultText.setText(v.getContext().getString(R.string.testDNSResultFailure, entry.getTestResult().getMessage()));
                testResultDialog.show();
                entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                setupTestButtons(entry.getTestResult().getTestState());
            }
        });
        testEntryResultSuccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testResultImage.setImageResource(R.drawable.ic_check_circle);
                testResultText.setText(v.getContext().getString(R.string.testDNSResultSuccess, entry.getTestResult().getPerf()));
                testResultDialog.show();
                entry.setTestResult(new DNSServerConfigTestResult(DNSServerConfigEntryTestState.NOT_STARTED));
                setupTestButtons(entry.getTestResult().getTestState());
            }
        });
        setupErrors(entry.getValidationResult());
    }

    private void setupTestButtons(DNSServerConfigEntryTestState testState) {
        if (testState == null) {
            testChangesButton.setVisibility(View.VISIBLE);
            testEntryProgressBar.clearAnimation();
            testEntryProgressBar.setVisibility(View.INVISIBLE);
            testEntryResultSuccess.setVisibility(View.INVISIBLE);
            testEntryResultFailure.setVisibility(View.INVISIBLE);
            return;
        }
        switch (testState) {
            case STARTED:
                testChangesButton.setVisibility(View.INVISIBLE);
                testEntryProgressBar.startAnimation(progressBarAnim);
                testEntryProgressBar.setVisibility(View.VISIBLE);
                testEntryResultSuccess.setVisibility(View.INVISIBLE);
                testEntryResultFailure.setVisibility(View.INVISIBLE);
                break;
            case FAIL:
                testChangesButton.setVisibility(View.INVISIBLE);
                testEntryProgressBar.clearAnimation();
                testEntryProgressBar.setVisibility(View.INVISIBLE);
                testEntryResultSuccess.setVisibility(View.INVISIBLE);
                testEntryResultFailure.setVisibility(View.VISIBLE);
                break;
            case SUCCESS:
                testChangesButton.setVisibility(View.INVISIBLE);
                testEntryProgressBar.clearAnimation();
                testEntryProgressBar.setVisibility(View.INVISIBLE);
                testEntryResultSuccess.setVisibility(View.VISIBLE);
                testEntryResultFailure.setVisibility(View.INVISIBLE);
                break;
            default:
                testChangesButton.setVisibility(View.VISIBLE);
                testEntryProgressBar.clearAnimation();
                testEntryProgressBar.setVisibility(View.INVISIBLE);
                testEntryResultSuccess.setVisibility(View.INVISIBLE);
                testEntryResultFailure.setVisibility(View.INVISIBLE);
                break;
        }
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
    }
}
