package dnsfilter.android.dnsserverconfig;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import dnsfilter.android.PaddedCheckBox;
import dnsfilter.android.R;

public class DNSServerConfigActivity extends Activity implements DNSServerConfigView {

    private DNSServerConfigPresenter presenter;

    private PaddedCheckBox manualDNSCheck;

    private PaddedCheckBox manualDNSRawModeCheckbox;
    private ListView manualDNSList;
    private EditText manualDNSEditText;

    private PaddedCheckBox showCommentedLinesCheckbox;

    private Button restoreDefaultConfigurationButton;
    private ImageButton applyNewConfigurationButton;

    public static final Integer ACTIVITY_RESULT_CODE = 325;
    static TextView titleView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 35) { // on API >=35 activity intersects with system and Navigation bar)
            requestWindowFeature(Window.FEATURE_NO_TITLE);

            View rootView = findViewById(android.R.id.content);

            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        setContentView(R.layout.activitydnsserverconfig);

        if (Build.VERSION.SDK_INT >= 35) { //add custom title as activity title is removed above
            // 1. get current content
            ViewGroup content = findViewById(android.R.id.content);
            if (content == null) return;

            // 2. current root
            if (content.getChildCount() == 0) return;
            View oldRoot = content.getChildAt(0);
            content.removeAllViews();

            // 3. New Container creation (vertikal)
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            // 4. add custom title on API >=35 as build in title is removed
            titleView = new TextView(this);;
            titleView.setTextSize(16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setMaxLines(1);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setPadding(32, 32, 32, 32);
            titleView.setBackgroundColor(Color.parseColor("#00BCD4"));
            titleView.setTextColor(Color.parseColor("#FFFFFF"));

            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            // 5. add old UI below
            LinearLayout.LayoutParams oldLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            );

            wrapper.addView(titleView, titleLp);
            wrapper.addView(oldRoot, oldLp);

            // 6. add the wrapper
            content.addView(wrapper);
        }

        setupActionBar();
        findViews();

        presenter = new DNSServerConfigPresenterImpl(this, this, savedInstanceState);

        configureManualDNSValue();
        configureDNSList();
        configureRawMode();
        configureRestoreDefaultsButton();
        configureApplyNewConfigurationButton();
        configureShowCommentedLines();
        presenter.onChangedShowCommentedLinesCheckbox(showCommentedLinesCheckbox.isChecked());
    }

    private void setupActionBar() {
        if (Build.VERSION.SDK_INT >= 21) {
            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(this.getResources().getColor(R.color.colorPrimary));
            getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimary));
        }
        if (Build.VERSION.SDK_INT >= 35) {
            if (titleView != null) {
                titleView.setText(R.string.dnsCfgConfigDialogTitle);
            }
        } else {
            ActionBar bar = getActionBar();
            if (bar != null) {
                bar.setTitle(R.string.dnsCfgConfigDialogTitle);
            }
        }
    }

    private void findViews() {
        manualDNSList = findViewById(R.id.manualDNSList);
        manualDNSRawModeCheckbox = findViewById(R.id.manualDNSRawModeCheckbox);
        showCommentedLinesCheckbox = findViewById(R.id.showCommentedLinesCheckbox);
        manualDNSEditText = findViewById(R.id.manualDNSEditText);
        manualDNSCheck = findViewById(R.id.manualDNSCheck);
        restoreDefaultConfigurationButton = findViewById(R.id.restoreDefaultBtn);
        applyNewConfigurationButton = findViewById(R.id.applyNewConfigurationButton);
    }

    private void configureApplyNewConfigurationButton() {
        applyNewConfigurationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.applyNewConfiguration(manualDNSRawModeCheckbox.isChecked(), manualDNSEditText.getText().toString());
            }
        });
    }

    private void configureManualDNSValue() {
        setManualDNSServers(presenter.getIsManualDNSServers());
        manualDNSCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onChangedManualDNSServers(manualDNSCheck.isChecked());
            }
        });
    }

    private void configureDNSList() {
        manualDNSList.setAdapter(presenter.getListAdapter());
    }

    private void configureRawMode() {
        manualDNSRawModeCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onChangedEditModeValue(manualDNSRawModeCheckbox.isChecked(), manualDNSEditText.getText().toString());
            }
        });
    }

    private void configureShowCommentedLines() {
        showCommentedLinesCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onChangedShowCommentedLinesCheckbox(showCommentedLinesCheckbox.isChecked());
            }
        });
    }

    private void configureRestoreDefaultsButton() {
        restoreDefaultConfigurationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.resetDNSConfigToDefault();
            }
        });
    }

    @Override
    public void setManualDNSServers(boolean isManual) {
        manualDNSCheck.setChecked(isManual);
    }

    @Override
    public void showRawModeError(String errorMessage) {
        manualDNSEditText.setError(errorMessage);
        manualDNSRawModeCheckbox.setChecked(true);
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showToastAndCloseScreen(String message) {
        String finalMessage = "DNS servers config updated";
        if (message != null && !message.isEmpty()) {
            finalMessage = message;
        }
        Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show();
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    Intent returnIntent = new Intent();
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                }
            }
        }, 500);
    }

    @Override
    public void resetToDefaultMode() {
        DNSServerConfigUtils.hideKeyboard(manualDNSEditText);
        manualDNSRawModeCheckbox.setChecked(false);
        manualDNSList.setVisibility(View.VISIBLE);
        manualDNSEditText.setVisibility(View.GONE);
        manualDNSEditText.setError(null);
        showCommentedLinesCheckbox.setVisibility(View.VISIBLE);
        presenter.onChangedShowCommentedLinesCheckbox(showCommentedLinesCheckbox.isChecked());
    }

    @Override
    public void showRawMode(String rawModeText) {
        if (!manualDNSRawModeCheckbox.isChecked()) {
            manualDNSRawModeCheckbox.setChecked(true);
        }
        manualDNSEditText.setText(rawModeText);
        manualDNSList.setVisibility(View.GONE);
        manualDNSEditText.setVisibility(View.VISIBLE);
        showCommentedLinesCheckbox.setVisibility(View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        presenter.saveState(
                outState,
                manualDNSRawModeCheckbox.isChecked(),
                manualDNSEditText.getText().toString(),
                showCommentedLinesCheckbox.isChecked()
        );
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }
}

interface DNSServerConfigView {
    void setManualDNSServers(boolean isManual);

    void showRawModeError(String errorMessage);

    void showToast(String message);

    void showToastAndCloseScreen(String message);

    void resetToDefaultMode();

    void showRawMode(String rawModeText);
}
