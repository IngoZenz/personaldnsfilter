package dnsfilter.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;

import dnsfilter.ConfigUtil;
import dnsfilter.ConfigurationAccess;
import util.GroupedLogger;
import util.Logger;
import util.LoggerInterface;
import util.SuppressRepeatingsLogger;
import util.TimeoutListener;
import util.TimoutNotificator;

public class DNSProxyActivity_Test extends AppCompatActivity implements View.OnClickListener, LoggerInterface {

    protected static ImageButton reset_button;
    protected static ImageButton settings_button;
    protected static LinearLayout power_button_layout;
    protected static ImageButton power_button;
    protected static TextView power_status;
    protected static Boolean isPowerOn = true;
    protected static TextView logOutView;

    protected static TextView link_field;
    protected static int link_field_color = Color.TRANSPARENT;
    protected static String link_field_txt = "";
    private static boolean MSG_ACTIVE = false;

    protected static ConfigurationAccess CONFIG = ConfigurationAccess.getLocal();

    protected static String IN_FILTER_PREF = "✗\u2002\u2009";
    protected static String NO_FILTER_PREF = "✓\u2004\u2009";
    protected static String IP_FORWARD_PREF = "➞\u200A";

    //log color and format
    protected static String filterLogFormat;
    protected static String acceptLogFormat;
    protected static String fwdLogFormat;
    protected static String normalLogFormat = "($CONTENT)";

    protected static SuppressRepeatingsLogger myLogger;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_main_activity);

        AndroidEnvironment.initEnvironment(this);
        MsgTO.setActivity(this);

        reset_button = (ImageButton) findViewById(R.id.reset_button);
        reset_button.setOnClickListener(this);
        settings_button = (ImageButton) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);
        power_button_layout = (LinearLayout) findViewById(R.id.power_icon_layout);
        power_button = (ImageButton) findViewById(R.id.power_button);
        power_button.setOnClickListener(this);
        power_status = (TextView) findViewById(R.id.power_status);
        logOutView = (TextView) findViewById(R.id.live_log);
        logOutView.setMovementMethod(new ScrollingMovementMethod());

        //Log formatting
        filterLogFormat = getConfig().getConfigValue("filterLogFormat", "<font color='#E53935'>($CONTENT)</font>");
        acceptLogFormat = getConfig().getConfigValue("acceptLogFormat", "<font color='#43A047'>($CONTENT)</font>");
        fwdLogFormat = getConfig().getConfigValue("fwdLogFormat", "<font color='#FFB300'>($CONTENT)</font>");
        normalLogFormat = getConfig().getConfigValue("normalLogFormat", "($CONTENT)");

        if (myLogger != null) {
            if (CONFIG.isLocal()) {
					/*(((GroupedLogger) Logger.getLogger()).detachLogger(myLogger);
					myLogger = new SuppressRepeatingsLogger(this);
					((GroupedLogger) Logger.getLogger()).attachLogger(myLogger);)*/
                ((SuppressRepeatingsLogger) myLogger).setNestedLogger(this);
            }
        } else {
            myLogger = new SuppressRepeatingsLogger(this);
            Logger.setLogger(new GroupedLogger(new LoggerInterface[]{myLogger}));
        }

        link_field = (TextView) findViewById(R.id.link_field);
        link_field.setText(fromHtml(link_field_txt));
        link_field.setOnClickListener(this);

        link_field_txt = getConfig().getConfigValue("footerLink", "");
        if (!MSG_ACTIVE)
            link_field.setText(fromHtml(link_field_txt));

        Drawable background = link_field.getBackground();
        if (background instanceof ColorDrawable)
            link_field_color = ((ColorDrawable) background).getColor();
        startup();

    }


    @Override
    public void onClick(View v) {


        switch (v.getId()) {


            case R.id.reset_button:
                Toast.makeText(this, "reset", Toast.LENGTH_LONG).show();
                break;

            case R.id.settings_button:
                goToSettings();
                Toast.makeText(this, "settings", Toast.LENGTH_LONG).show();
                break;

            case R.id.power_button:
                if (isPowerOn) {
                    startup();
                    isPowerOn = false;
                    power_button_layout.setBackgroundResource(R.drawable.power_button_border);
                    power_button.setImageResource(R.drawable.power_icon);
                    power_status.setText("ON");
                } else {
                    stopSyc();
                    isPowerOn = true;
                    power_button_layout.setBackgroundResource(R.drawable.power_button_border_white);
                    power_button.setImageResource(R.drawable.power_icon_white);
                    power_status.setText("OFF");
                }
                break;


        }

    }

    protected void goToSettings(){


        Fragment fragment = new SettingsFragment();
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.main_fragment_container, fragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

    }

    protected void startup() {

        if (DNSFilterService.SERVICE != null) {
            Logger.getLogger().logLine("DNS filter service is running!");
            Logger.getLogger().logLine("Filter statistic since last restart:");
            showFilterRate(false);
            //Logger.getLogger().message("Attached already running Service!");
            return;
        }

        try {
            long repeatingLogSuppressTime = Long.parseLong(getConfig().getConfigValue("repeatingLogSuppressTime", "1000"));
            boolean liveLogTimestampEnabled = Boolean.parseBoolean(getConfig().getConfigValue("addLiveLogTimestamp", "false"));
            myLogger.setTimestampFormat(null);
            if (liveLogTimestampEnabled) {
                String timeStampPattern = getConfig().getConfigValue("liveLogTimeStampFormat", "hh:mm:ss");
                myLogger.setTimestampFormat(timeStampPattern);
            }
            myLogger.setSuppressTime(repeatingLogSuppressTime);
            boolean vpnInAdditionToProxyMode = Boolean.parseBoolean(getConfig().getConfigValue("vpnInAdditionToProxyMode", "false"));
            boolean vpnDisabled = !vpnInAdditionToProxyMode && Boolean.parseBoolean(getConfig().getConfigValue("dnsProxyOnAndroid", "false"));
            Intent intent = null;
            if (!vpnDisabled)
                intent = VpnService.prepare(this.getApplicationContext());
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else { //already prepared or VPN disabled
                startSvc();
            }
        } catch (NullPointerException e) { // NullPointer might occur on Android 4.4 when VPN already initialized
            Logger.getLogger().logLine("Seems we are on Android 4.4 or older!");
            startSvc(); // assume it is ok!
        } catch (Exception e) {
            Logger.getLogger().logException(e);
        }

        isPowerOn = true;
        power_button_layout.setBackgroundResource(R.drawable.power_button_border);
        power_button.setImageResource(R.drawable.power_icon);
        power_status.setText("ON");

    }

    private void startSvc() {
        startService(new Intent(this, DNSFilterService.class));
    }


    private void stopSyc() {

    }



    private static class MsgTimeoutListener implements TimeoutListener {

        long timeout = Long.MAX_VALUE;

        DNSProxyActivity_Test activity;

        public void setActivity(DNSProxyActivity_Test activity) {
            this.activity = activity;
        }

        private void setTimeout(int timeout) {
            this.timeout = System.currentTimeMillis() + timeout;
            TimoutNotificator.getInstance().register(this);
        }

        @Override
        public void timeoutNotification() {
            if (CONFIG.isLocal())
                activity.setMessage(fromHtml(link_field_txt), link_field_color);
            else
                activity.setMessage(fromHtml("<font color='#F7FB0A'><strong>" + CONFIG + "</strong></font>"), link_field_color);

            MSG_ACTIVE = false;
        }

        @Override
        public long getTimoutTime() {
            return timeout;
        }
    }

    ;


    private static MsgTimeoutListener MsgTO = new MsgTimeoutListener();


    private static Spanned fromHtml(String txt) {
        if (Build.VERSION.SDK_INT >= 24)
            return Html.fromHtml(txt, 0);
        else
            return Html.fromHtml(txt);
    }


    private void addToLogView(String logStr) {

        StringTokenizer logLines = new StringTokenizer(logStr, "\n");
        while (logLines.hasMoreElements()) {

            String logLn = logLines.nextToken();

            boolean filterHostLog = logLn.startsWith(IN_FILTER_PREF);
            boolean okHostLog = logLn.startsWith(NO_FILTER_PREF);
            boolean fwdHostLog = logLn.startsWith(IP_FORWARD_PREF);

            if (filterHostLog || okHostLog || fwdHostLog) {

                if (filterHostLog)
                    logLn = filterLogFormat.replace("($CONTENT)", logLn) + "<br>";
                else if (okHostLog)
                    logLn = acceptLogFormat.replace("($CONTENT)", logLn) + "<br>";
                else
                    logLn = fwdLogFormat.replace("($CONTENT)", logLn) + "<br>";

                logOutView.append(fromHtml(logLn));
            } else {
                String newLn = "\n";
                if (!logLines.hasMoreElements() && !logStr.endsWith("\n"))
                    newLn = "";
                //logOutView.append(fromHtml("<font color='#455a64'>" + logLn + "</font>"));
                logOutView.append(fromHtml(normalLogFormat.replace("($CONTENT)", logLn)));
                logOutView.append(newLn);
            }
        }
    }


    private class MyUIThreadLogger implements Runnable {

        private String m_logStr;

        public MyUIThreadLogger(String logStr) {
            m_logStr = logStr;
        }

        @Override
        public synchronized void run() {

            m_logStr = m_logStr.replace("FILTERED:", IN_FILTER_PREF);
            m_logStr = m_logStr.replace("ALLOWED:", NO_FILTER_PREF);
            m_logStr = m_logStr.replace("MAPPED_CUSTOM_IP:", IP_FORWARD_PREF);

            addToLogView(m_logStr);

            int logSize = logOutView.getText().length();

            if (logSize >= 10000) {
                Spannable logStr = (Spannable) logOutView.getText();
                int start = logSize / 2;

                while (logStr.charAt(start) != '\n' && start < logStr.length() - 1)
                    start++;

                logOutView.setText(logStr.subSequence(start, logStr.length()));
            }

            String version = "<unknown>";
            String connCnt = "-1";
            String lastDNS = "<unknown>";
            try {
                version = CONFIG.getVersion();
                connCnt = CONFIG.openConnectionsCount() + "";
            } catch (IOException e) {
                addToLogView(e.toString() + "\n");
            }
            setTitle("personalDNSfilter V" + version + " (Connections:" + connCnt + ")");
        }
    }

    @Override
    public void logLine(String txt) {
        runOnUiThread(new MyUIThreadLogger(txt + "\n"));
    }

    @Override
    public void logException(Exception e) {
        StringWriter str = new StringWriter();
        e.printStackTrace(new PrintWriter(str));
        runOnUiThread(new MyUIThreadLogger(str.toString() + "\n"));
    }

    @Override
    public void log(String txt) {
        runOnUiThread(new MyUIThreadLogger(txt));
    }


    @Override
    public void message(String txt) {
        setMessage(fromHtml("<strong>" + txt + "</strong>"), Color.parseColor("#FFC107"));
        MsgTO.setTimeout(5000);
    }


    private void setMessage(final Spanned msg, final int backgroundColor) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                link_field.setBackgroundColor(backgroundColor);
                link_field.setText(msg);
                MSG_ACTIVE = true;
            }
        });
    }


    @Override
    public void closeLogger() {
        // TODO Auto-generated method stub

    }


    protected ConfigUtil getConfig() {
        try {
            return CONFIG.getConfigUtil();
        } catch (Exception e) {
            Logger.getLogger().logException(e);
            return null;
        }
    }


    protected void showFilterRate(boolean asMessage) {

        try {
            long[] stats = CONFIG.getFilterStatistics();
            long all = stats[0] + stats[1];

            if (all != 0) {
                long filterRate = 100 * stats[1] / all;
                if (asMessage)
                    myLogger.message("Block rate: " + filterRate + "% (" + stats[1] + " blocked)!");
                else
                    myLogger.logLine("Block rate: " + filterRate + "% (" + stats[1] + " blocked)!");
            }
        } catch (Exception e) {
            Logger.getLogger().logException(e);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            startSvc();
        } else if (requestCode == 0 && resultCode != Activity.RESULT_OK) {
            Logger.getLogger().logLine("VPN dialog not accepted!\r\nPress restart to display dialog again!");
        }
    }

}
