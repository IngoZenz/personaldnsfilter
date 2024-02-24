/*
 PersonalDNSFilter 1.5
 Copyright (C) 2017 Ingo Zenz

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

 Find the latest version at http://www.zenz-solutions.de/personaldnsfilter
 Contact:i.z@gmx.net 
 */

package dnsfilter.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import dnsfilter.ConfigUtil;
import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSFilterManager;
import dnsfilter.android.dnsserverconfig.DNSServerConfigActivity;
import util.ExecutionEnvironment;
import util.GroupedLogger;
import util.Logger;
import util.LoggerInterface;
import util.SuppressRepeatingsLogger;
import util.TimeoutListener;
import util.TimoutNotificator;


public class DNSProxyActivity extends Activity
		implements OnClickListener,
		LoggerInterface,
		TextWatcher,
		DialogInterface.OnKeyListener,
		ActionMode.Callback,
		MenuItem.OnMenuItemClickListener,
		View.OnTouchListener,
		View.OnFocusChangeListener {


	protected static boolean BOOT_START = false;

	protected Button startBtn;
	protected Button stopBtn;
	protected Button reloadFilterBtn;
	protected Button remoteCtrlBtn;
	protected Button helpBtn;
	protected static EditText logOutView;
	protected static TextView dnsField;
	protected static CheckBox advancedConfigCheck;
	protected static CheckBox editFilterLoadCheck;
	protected static CheckBox editAdditionalHostsCheck;
	protected static CheckBox backupRestoreCheck;
	protected static Button backupBtn;
	protected static Button restoreBtn;
	protected static Button restoreDefaultsBtn;
	protected static Button backupDnBtn;
	protected static Button backupUpBtn;
	protected static TextView addFilterBtn;
	protected static TextView removeFilterBtn;
	protected static CheckBox appWhiteListCheck;
	protected static boolean appWhitelistingEnabled;
	protected static ScrollView appWhiteListScroll;
	protected static AppSelectorView appSelector;
	protected static CheckBox keepAwakeCheck;
	protected static CheckBox enableAdFilterCheck;
	protected static CheckBox proxyModeCheck;
	protected static CheckBox proxyLocalOnlyCheck;
	protected static CheckBox enableAutoStartCheck;
	protected static CheckBox rootModeCheck;
	protected static CheckBox enableCloakProtectCheck;
	protected static CheckBox manuallyEditConfChk;
	protected static EditText filterReloadIntervalView;
	protected static FilterConfig filterCfg;
	protected static EditText additionalHostsField;
	protected static EditText manuallyEditField;
	protected static TextView scrollLockField;
	protected static String SCROLL_PAUSE = "II  ";
	protected static String SCROLL_CONTINUE = " ▶ ";
	protected static boolean scroll_locked = false;
	protected static TextView link_field;
	protected static int link_field_color = Color.TRANSPARENT;
	protected static String link_field_txt = "";
	protected static MenuItem add_filter;
	protected static MenuItem remove_filter;
	protected static String[] availableBackups;
	protected static int selectedBackup;

	protected static boolean additionalHostsChanged = false;
	protected static boolean manuallyConfEdited = false;
	protected static SuppressRepeatingsLogger myLogger;

	protected ScrollView scrollView = null;

	protected static boolean appStart = true;

	protected static String ADDITIONAL_HOSTS_TO_LONG = "additionalHosts.txt too long to edit here!\nSize Limit: 512 KB!\nUse other editor!";

	protected static ConfigUtil config = null;
	protected static boolean debug = false;

	protected static int NO_ACTION_MENU = 0;
	protected boolean ACTION_MENU_FALLBACK = false;


	protected static String IN_FILTER_PREF = "✗\u2002\u2009";
	protected static String NO_FILTER_PREF = "✓\u2004\u2009";
	protected static String IP_FORWARD_PREF = "➞\u200A";

	//log color and format
	protected static String filterLogFormat;
	protected static String acceptLogFormat;
	protected static String fwdLogFormat;
	protected static String normalLogFormat="($CONTENT)";

	protected static ConfigurationAccess CONFIG = ConfigurationAccess.getLocal();
	protected static boolean switchingConfig = false;

	protected static AlertDialog PASSWORD_DIAG = null;

	protected static boolean NO_VPN = false;

	protected static boolean MSG_ACTIVE = false;

	protected static int DISPLAY_WIDTH = 0;
	protected static int DISPLAY_HEIGTH = 0;

	protected static DNSProxyActivity INSTANCE;

	public static void reloadLocalConfig() {
		DNSProxyActivity instance = INSTANCE;
		if (instance != null && CONFIG.isLocal())
			instance.loadAndApplyConfig(false);
	}

	private static class MsgTimeoutListener implements TimeoutListener {

		long timeout = Long.MAX_VALUE;

		DNSProxyActivity activity;

		public void setActivity(DNSProxyActivity activity) {
			this.activity= activity;
		}

		private void setTimeout(int timeout) {
			this.timeout = System.currentTimeMillis()+timeout;
			TimoutNotificator.getInstance().register(this);
		}

		@Override
		public void timeoutNotification() {
			if (CONFIG.isLocal()) {
				activity.setMessage(fromHtml(link_field_txt), link_field_color);
				link_field.setMovementMethod(LinkMovementMethod.getInstance());
			}
			else
				activity.setMessage(fromHtml("<font color='#F7FB0A'><strong>"+ CONFIG +"</strong></font>"), link_field_color);

			MSG_ACTIVE = false;
		}

		@Override
		public long getTimoutTime() {
			return timeout;
		}
	}

	private static MsgTimeoutListener MsgTO = new MsgTimeoutListener();

	private static Spanned fromHtml(String txt) {
		if (Build.VERSION.SDK_INT >= 24)
			return Html.fromHtml(txt, 0);
		else
			return Html.fromHtml(txt);
	}


	private void addToLogView(String logStr) {

		StringTokenizer logLines = new StringTokenizer(logStr,"\n");
		while (logLines.hasMoreElements()) {

			String logLn = logLines.nextToken();

			boolean filterHostLog = logLn.startsWith(IN_FILTER_PREF);
			boolean okHostLog = logLn.startsWith(NO_FILTER_PREF);
			boolean fwdHostLog = logLn.startsWith(IP_FORWARD_PREF);

			if (filterHostLog || okHostLog || fwdHostLog) {

				if (filterHostLog)
					logLn = filterLogFormat.replace("($CONTENT)",logLn)+"<br>";
				else if (okHostLog)
					logLn = acceptLogFormat.replace("($CONTENT)",logLn)+"<br>";
				else
					logLn =  fwdLogFormat.replace("($CONTENT)",logLn)+"<br>";

				logOutView.append(fromHtml(logLn));
			} else {
				String newLn = "\n";
				if (!logLines.hasMoreElements() && !logStr.endsWith("\n"))
					newLn = "";
				//logOutView.append(fromHtml("<font color='#455a64'>" + logLn + "</font>"));
				logOutView.append(fromHtml(normalLogFormat.replace("($CONTENT)",logLn)));
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

			if (!scroll_locked) {
				m_logStr = m_logStr.replace("FILTERED:",IN_FILTER_PREF);
				m_logStr = m_logStr.replace("ALLOWED:",NO_FILTER_PREF);
				m_logStr = m_logStr.replace("MAPPED_CUSTOM_IP:",IP_FORWARD_PREF);

				addToLogView(m_logStr);

				int logSize = logOutView.getText().length();

				if (logSize >= 10000) {
					Spannable logStr = logOutView.getText();
					int start = logSize / 2;

					while (logStr.charAt(start) != '\n' && start < logStr.length()-1)
						start++;

					logOutView.setText(logStr.subSequence(start, logStr.length()));

				}

				if (!advancedConfigCheck.isChecked()) { //avoid focus lost when editing advanced settings
					logOutView.setSelection(logOutView.getText().length());
					scrollView.fullScroll(ScrollView.FOCUS_DOWN);
				}
			}
			String version = "<unknown>";
			String connCnt = "-1";
			String lastDNS = "<unknown>";
			try {
				version = CONFIG.getVersion();
				connCnt = CONFIG.openConnectionsCount()+"";
				lastDNS= CONFIG.getLastDNSAddress();
			} catch (IOException e){
				addToLogView(e.toString()+"\n");
			}
			setTitle("personalDNSfilter V" + version + " (Connections:" + connCnt + ")");
			dnsField.setText(lastDNS);
		}
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		try {

			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().build());

			super.onCreate(savedInstanceState);

			if (getIntent().getBooleanExtra("SHOULD_FINISH", false)) {
				finish();
				System.exit(0);
			}
			AndroidEnvironment.initEnvironment(this);

			DISPLAY_WIDTH = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getWidth();
			DISPLAY_HEIGTH= ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getHeight();

			MsgTO.setActivity(this);
			INSTANCE = this;

			if (Build.VERSION.SDK_INT >= 21) {
				Window window = this.getWindow();
				window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
				window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
				window.setStatusBarColor(this.getResources().getColor(R.color.colorPrimary));
				getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimary));
			}

			setContentView(R.layout.main);

			//log view init
			Spannable logTxt = null;
			float logSize = -1;
			if (logOutView != null) {
				logTxt = logOutView.getText();
				logSize = logOutView.getTextSize();
			}

			logOutView = (EditText) findViewById(R.id.logOutput);
			if (logSize != -1)
				logOutView.setTextSize(TypedValue.COMPLEX_UNIT_PX, logSize);

			if (logTxt != null)
				logOutView.setText(logTxt);
			else
				logOutView.setText(fromHtml("<strong><em>****This is personalDNSfilter V+" + DNSFilterManager.VERSION + "****</em></strong><br><br>"));

			logOutView.setKeyListener(null);
			logOutView.setCustomSelectionActionModeCallback(this);
			logOutView.setOnTouchListener(this);
			logOutView.setOnFocusChangeListener(this);
			logOutView.setOnClickListener(this);

			String version = "<unknown>";
			String connCnt = "-1";

			try {
				version = CONFIG.getVersion();
				connCnt = CONFIG.openConnectionsCount() + "";
			} catch (IOException e) {
				addToLogView(e.toString() + "\n");
			}
			setTitle("personalDNSfilter V" + version + " (Connections:" + connCnt + ")");

			ConfigUtil.HostFilterList[] cfgEntries = null;
			String filterCategory = null;
			if (filterCfg != null) {
				cfgEntries = filterCfg.getFilterEntries();
				filterCategory = filterCfg.getCurrentCategory();
				filterCfg.clear(); //clean references etc
			}

			Button categoryUp = ((Button) findViewById(R.id.CategoryUp));
			Button categoryDn = ((Button) findViewById(R.id.CategoryDown));
			TextView categoryField = ((TextView) findViewById(R.id.categoryFilter));


			filterCfg = new FilterConfig((TableLayout) findViewById(R.id.filtercfgtable), categoryUp, categoryDn, categoryField);
			if (cfgEntries != null) {
				filterCfg.setEntries(cfgEntries);
				filterCfg.setCurrentCategory(filterCategory);
			}

			String uiText = "";
			if (filterReloadIntervalView != null)
				uiText = filterReloadIntervalView.getText().toString();
			filterReloadIntervalView = (EditText) findViewById(R.id.filterloadinterval);
			filterReloadIntervalView.setText(uiText);

			startBtn = (Button) findViewById(R.id.startBtn);
			startBtn.setOnClickListener(this);
			stopBtn = (Button) findViewById(R.id.stopBtn);
			stopBtn.setOnClickListener(this);
			reloadFilterBtn = (Button) findViewById(R.id.filterReloadBtn);
			reloadFilterBtn.setOnClickListener(this);
			helpBtn = (Button) findViewById(R.id.helpBtn);
			helpBtn.setOnClickListener(this);
			remoteCtrlBtn = (Button) findViewById(R.id.remoteCtrlBtn);
			if (!CONFIG.isLocal())
				remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.remote_icon), null);
			else
				remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.remote_icon_outline), null);

			remoteCtrlBtn.setOnClickListener(this);
			backupBtn = (Button) findViewById(R.id.backupBtn);
			backupBtn.setOnClickListener(this);
			restoreBtn = (Button) findViewById(R.id.RestoreBackupBtn);
			restoreBtn.setOnClickListener(this);
			restoreDefaultsBtn = (Button) findViewById(R.id.RestoreDefaultBtn);
			restoreDefaultsBtn.setOnClickListener(this);
			backupDnBtn = (Button) findViewById(R.id.BackupIdDn);
			backupDnBtn.setOnClickListener(this);
			backupUpBtn = (Button) findViewById(R.id.BackupIdUp);
			backupUpBtn.setOnClickListener(this);
			addFilterBtn = (TextView) findViewById(R.id.addFilterBtn);
			addFilterBtn.setOnClickListener(this);
			removeFilterBtn = (TextView) findViewById(R.id.removeFilterBtn);
			removeFilterBtn.setOnClickListener(this);
			link_field = (TextView) findViewById(R.id.link_field);
			link_field.setText(fromHtml(link_field_txt));
			link_field.setMovementMethod(LinkMovementMethod.getInstance());
			
			Drawable background = link_field.getBackground();
			if (background instanceof ColorDrawable)
				link_field_color = ((ColorDrawable) background).getColor();

			scrollLockField = (TextView) findViewById(R.id.scrolllock);
			if (scroll_locked)
				scrollLockField.setText(SCROLL_CONTINUE);
			else
				scrollLockField.setText(SCROLL_PAUSE);

			scrollLockField.setOnClickListener(this);

			uiText = "";

			scrollView = (ScrollView) findViewById(R.id.logScroll);

			if (dnsField != null)
				uiText = dnsField.getText().toString();
			dnsField = (TextView) findViewById(R.id.dnsField);
			dnsField.setText(uiText);
			dnsField.setEnabled(true);
			dnsField.setOnClickListener(this);

			boolean checked = enableAdFilterCheck != null && enableAdFilterCheck.isChecked();
			enableAdFilterCheck = (CheckBox) findViewById(R.id.enableAddFilter);
			enableAdFilterCheck.setChecked(checked);
			enableAdFilterCheck.setOnClickListener(this);

			checked = enableAutoStartCheck != null && enableAutoStartCheck.isChecked();
			enableAutoStartCheck = (CheckBox) findViewById(R.id.enableAutoStart);
			enableAutoStartCheck.setChecked(checked);
			enableAutoStartCheck.setOnClickListener(this);

			checked = backupRestoreCheck != null && backupRestoreCheck.isChecked();
			backupRestoreCheck = (CheckBox) findViewById(R.id.backupRestoreChk);
			backupRestoreCheck.setChecked(checked);
			backupRestoreCheck.setOnClickListener(this);

			checked = appWhiteListCheck != null && appWhiteListCheck.isChecked();
			appWhiteListCheck = (CheckBox) findViewById(R.id.appWhitelist);
			appWhiteListCheck.setChecked(checked);
			appWhiteListCheck.setOnClickListener(this);

			checked = keepAwakeCheck != null && keepAwakeCheck.isChecked();
			keepAwakeCheck = (CheckBox) findViewById(R.id.keepAwakeCheck);
			keepAwakeCheck.setChecked(checked);
			keepAwakeCheck.setOnClickListener(this);

			checked = proxyModeCheck != null && proxyModeCheck.isChecked();
			proxyModeCheck = (CheckBox) findViewById(R.id.proxyModeCheck);
			proxyModeCheck.setChecked(checked);
			proxyModeCheck.setOnClickListener(this);

			checked = proxyLocalOnlyCheck != null && proxyLocalOnlyCheck .isChecked();
			proxyLocalOnlyCheck = (CheckBox) findViewById(R.id.proxyLocalOnlyCheck);
			proxyLocalOnlyCheck.setChecked(checked);
			proxyLocalOnlyCheck.setOnClickListener(this);

			checked = rootModeCheck != null && rootModeCheck.isChecked();
			rootModeCheck = (CheckBox) findViewById(R.id.rootModeCheck);
			rootModeCheck.setChecked(checked);
			rootModeCheck.setOnClickListener(this);

			checked = enableCloakProtectCheck != null && enableCloakProtectCheck.isChecked();
			enableCloakProtectCheck = (CheckBox) findViewById(R.id.cloakProtectCheck);
			enableCloakProtectCheck.setChecked(checked);
			enableCloakProtectCheck.setOnClickListener(this);

			checked = advancedConfigCheck != null && advancedConfigCheck.isChecked();
			advancedConfigCheck = (CheckBox) findViewById(R.id.advancedConfigCheck);
			advancedConfigCheck.setChecked(checked);
			advancedConfigCheck.setOnClickListener(this);

			checked = editFilterLoadCheck != null && editFilterLoadCheck.isChecked();
			editFilterLoadCheck = (CheckBox) findViewById(R.id.editFilterLoad);
			editFilterLoadCheck.setChecked(checked);
			editFilterLoadCheck.setOnClickListener(this);

			checked = manuallyEditConfChk != null && manuallyEditConfChk.isChecked();
			manuallyEditConfChk= (CheckBox) findViewById(R.id.manuallyEditConfChk);
			manuallyEditConfChk.setChecked(checked);
			manuallyEditConfChk.setOnClickListener(this);

			checked = editAdditionalHostsCheck != null && editAdditionalHostsCheck.isChecked();
			editAdditionalHostsCheck = (CheckBox) findViewById(R.id.editAdditionalHosts);
			editAdditionalHostsCheck.setChecked(checked);
			editAdditionalHostsCheck.setOnClickListener(this);

			appWhiteListScroll = (ScrollView) findViewById(R.id.appWhiteListScroll);
			String whitelistedApps = "";
			if (appSelector != null) {
				appSelector.clear();
				whitelistedApps = appSelector.getSelectedAppPackages();
			}
			appSelector = findViewById(R.id.appSelector);
			appSelector.setSelectedApps(whitelistedApps);

			if (additionalHostsField != null)
				uiText = additionalHostsField.getText().toString();

			additionalHostsField = (EditText) findViewById(R.id.additionalHostsField);
			additionalHostsField.setText(uiText);
			additionalHostsField.addTextChangedListener(this);

			if (manuallyEditField != null)
				uiText = manuallyEditField.getText().toString();

			manuallyEditField = (EditText) findViewById(R.id.manuallyEditField);
			manuallyEditField.setText(uiText);
			manuallyEditField.addTextChangedListener(this);

			findViewById(R.id.copyfromlog).setVisibility(View.GONE);

			handleAdvancedConfig(null);

			if (myLogger != null) {
				if (CONFIG.isLocal()) {
					/*(((GroupedLogger) Logger.getLogger()).detachLogger(myLogger);
					myLogger = new SuppressRepeatingsLogger(this);
					((GroupedLogger) Logger.getLogger()).attachLogger(myLogger);)*/
					((SuppressRepeatingsLogger)myLogger).setNestedLogger(this);
				}
			} else {
				myLogger = new SuppressRepeatingsLogger(this);
				Logger.setLogger(new GroupedLogger(new LoggerInterface[]{myLogger}));
			}

			String forcedDisplayMode = ConfigurationAccess.getLocal().getConfigUtil().getConfigValue("forceAndroidDisplayMode", "none").trim();
			if (forcedDisplayMode.equalsIgnoreCase("portrait"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			else if (forcedDisplayMode.equalsIgnoreCase("landscape"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else if(getResources().getBoolean(R.bool.portrait_only)){
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}

			debug = Boolean.parseBoolean(ConfigurationAccess.getLocal().getConfigUtil().getConfigValue("debug", "false"));
			if (debug)
				Runtime.getRuntime().exec("logcat -d -f" + ExecutionEnvironment.getEnvironment().getWorkDir()+"/Logcat_file.txt");

			if (appStart) {
				if (Build.VERSION.SDK_INT >= 33) {
					if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
						this.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
					}
				}
				initAppAndStartup();
			}

		} catch (Exception e){
			dump(e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
		if (grantResults.length == 0)
			return;
		if (permissions[0].equals(Manifest.permission.POST_NOTIFICATIONS) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			handleRestart(); //let it take effect
			handleInitialInfoPopUp();
		}
		else Logger.getLogger().message("NOTIFICATION PERMISSION IS REQUIRED!");
	}

	@Override
	public void onResume() {
		try {
			super.onResume();
			checkPasscode();
		} catch (Exception e){
			e.printStackTrace();
			Logger.getLogger().logLine("onResume() failed! "+e.toString());
		}
	}

	private void checkPasscode() {

		if (PASSWORD_DIAG != null && PASSWORD_DIAG.isShowing() )
			PASSWORD_DIAG.dismiss();
		try {
			Properties config = CONFIG.getConfig();
			if (config == null){
				logLine("Error: Config is null!");
				return;
			}
			final String code = CONFIG.getConfig().getProperty("passcode", "").trim();

			if (code.equals(""))
				return;

			final AlertDialog.Builder builder = new AlertDialog.Builder(this).setCancelable(false);

			builder.setTitle("Passcode required!");
			final EditText input = new EditText(this);
			input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			builder.setView(input);

			builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					PASSWORD_DIAG = null;
					String inputcode = input.getText().toString();
					if (!inputcode.equals(code)) {
						message("Wrong passcode!");
						checkPasscode();
					}
				}
			});

			Runnable ui = new Runnable() {
				@Override
				public void run() {
					PASSWORD_DIAG = builder.show();
				}
			};
			runOnUiThread(ui);

		} catch (IOException eio) {
			logException(eio);
		}
	}

	private void dump(Exception e) {
		StringWriter str = new StringWriter();
		e.printStackTrace(new PrintWriter(str));
		try {
			FileOutputStream dump = new FileOutputStream(ExecutionEnvironment.getEnvironment().getWorkDir()+"/dump-"+System.currentTimeMillis()+".txt");
			dump.write(("TIME: "+new Date()+"\nVERSION: "+DNSFilterManager.VERSION+"\n\n").getBytes());
			dump.write(str.toString().getBytes());
			dump.flush();
			dump.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}


	protected ConfigUtil getConfig() {
		try {
			return CONFIG.getConfigUtil();
		} catch (Exception e){
			Logger.getLogger().logException(e);
			return null;
		}
	}

	protected void showFilterRate(boolean asMessage) {

		try {
			long[] stats = CONFIG.getFilterStatistics();
			long all = stats[0]+stats[1];

			if (all != 0) {
				long filterRate = 100*stats[1] / all;
				if (asMessage)
					myLogger.message("Block rate: "+filterRate+"% ("+stats[1]+" blocked)!");
				else
					myLogger.logLine("Block rate: "+filterRate+"% ("+stats[1]+" blocked)!");
			}
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}

	private String getBackupSubFolder() {
		String subFolder = ".";
		String txt = ((TextView) findViewById(R.id.BackupId)).getText().toString();
		if (selectedBackup != -1 || !txt.equals("<default>")) {
			if (selectedBackup!=-1)
				subFolder = availableBackups[selectedBackup];

			if (!txt.equals(subFolder)) {
				//edited name

				//Check if in List
				for (int i = 0; i < availableBackups.length; i++)
					if (txt.equals(availableBackups[i])) {
						selectedBackup = i;
						return txt;
					}

				//new entry => add to list
				ArrayList<String> list = new ArrayList<String>();
                list.addAll(Arrays.asList(availableBackups));

				list.add(txt);

				Collections.sort(list);
				availableBackups = list.toArray(new String[list.size()]);

				for (int i = 0; i < availableBackups.length; i++)
					if (txt.equals(availableBackups[i])) {
						selectedBackup = i;
						return txt;

					}

				//something wrong
				Logger.getLogger().logException(new Exception("Something is wrong!"));
				return txt;
			}
			else return txt;
		}
		else return ".";
	}



	protected void doBackup() {

		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			CONFIG.doBackup(getBackupSubFolder());
			backupStatusView.setTextColor(Color.parseColor("#43A047"));
			backupStatusView.setText("Backup success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#E53935"));
			backupStatusView.setText("Backup failed! " + eio.getMessage());
		}
	}

	protected void doRestoreDefaults() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			CONFIG.doRestoreDefaults();
			backupStatusView.setTextColor(Color.parseColor("#43A047"));
			loadAndApplyConfig(false);
			backupStatusView.setText("Restore success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#E53935"));
			backupStatusView.setText("Restore failed! " + eio.getMessage());
		}
	}

	protected void doRestore() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			CONFIG.doRestore(getBackupSubFolder());
			backupStatusView.setTextColor(Color.parseColor("#43A047"));
			loadAndApplyConfig(false);
			backupStatusView.setText("Restore success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#E53935"));
			backupStatusView.setText("Restore failed! " + eio.getMessage());
		}
	}




	protected void loadAdditionalHosts() {
		int limit= 524288;
		try {
			byte[] content = CONFIG.getAdditionalHosts(limit);
			if (content == null) {
				additionalHostsField.setText(ADDITIONAL_HOSTS_TO_LONG);
				additionalHostsField.setEnabled(false);
				return;
			}
			additionalHostsField.setText(new String(content));
			additionalHostsChanged = false;
		} catch (IOException eio) {
			Logger.getLogger().logLine("Can not load /PersonalDNSFilter/additionalHosts.txt!\n" + eio.toString());
		}
	}

	protected void loadManuallyEditConf()  {
		try {
			byte[] content = CONFIG.readConfig();
			manuallyEditField.setText(new String(content));
			manuallyConfEdited = false;
		} catch (IOException eio) {
			Logger.getLogger().logLine("Can not load /PersonalDNSFilter/dnsfilter.conf!\n" + eio.toString());
		}
	}


	protected boolean persistAdditionalHosts() {
		String addHostsTxt = additionalHostsField.getText().toString();
		if (!addHostsTxt.equals("") && !addHostsTxt.equals(ADDITIONAL_HOSTS_TO_LONG)) {
			if (additionalHostsChanged)
				try {
					CONFIG.updateAdditionalHosts(addHostsTxt.getBytes());
				} catch (IOException eio) {
					Logger.getLogger().logLine("Cannot persistAdditionalHosts!\n" + eio.toString());
				}
		}
		return additionalHostsChanged;
	}

	protected boolean persistManuallyEditConf()  {
		String addHostsTxt = manuallyEditField.getText().toString();
		if (!addHostsTxt.equals("")) {
			if (manuallyConfEdited)
				try {
					CONFIG.updateConfigMergeDefaults(addHostsTxt.getBytes());
					loadAndApplyConfig(false);
				} catch (IOException eio) {
					Logger.getLogger().logLine("Cannot persist manually edited config!\n" + eio.toString());
				}
		}
		return manuallyConfEdited;
	}




	protected void handlefilterReload() {
		try {
			CONFIG.triggerUpdateFilter();
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}

	protected  void applyCopiedHosts(String entryStr, boolean filter) {
		findViewById(R.id.copyfromlog).setVisibility(View.GONE);

		StringTokenizer entryTokens = new StringTokenizer(entryStr, "\n");
		String entries = "";
		while (entryTokens.hasMoreTokens()) {
			String token = entryTokens.nextToken();
			if (token.startsWith(IN_FILTER_PREF) && filter == false) {
				entries = entries+token.substring(IN_FILTER_PREF.length()).trim()+"\n";
			}
			if (token.startsWith(NO_FILTER_PREF) && filter == true) {
				entries = entries+token.substring(NO_FILTER_PREF.length()).trim()+"\n";
			}
		}

		try {
			CONFIG.updateFilter(entries.trim(),filter);
		} catch (IOException e) {
			Logger.getLogger().logException(e);
		}
	}

	private void initAppAndStartup() {
		logLine("Initializing ...");
		try {
			ACTION_MENU_FALLBACK = Boolean.parseBoolean(ConfigurationAccess.getLocal().getConfigUtil().getConfigValue("useActionMenuFallback", "false"));
		} catch (IOException e) {
			Logger.getLogger().logLine("Cannot get Config for useActionMenuFallback "+e.toString());
		}
		if (BOOT_START) {
			Logger.getLogger().logLine("Running on SDK" + Build.VERSION.SDK_INT);
			if (Build.VERSION.SDK_INT >= 20) //on older Android we have to keep the app in forground due to the VPN accept dialog popping up after each reboot.
				finish();
			BOOT_START = false;
		}
		loadAndApplyConfig(true);

		appStart = false; // now started
	}

	private static Dialog popUpDialog = null;
	private static boolean showInitialInfoPopUp = true;
	private static boolean popUpDialogChanged = false;
	private static Button initialInfoPopUpExitBtn = null;


	private void closeInitialInfoPopUp() {
		showInitialInfoPopUp = !((CheckBox)popUpDialog.findViewById(R.id.disableInfoPopUp)).isChecked();
		popUpDialog.dismiss();
		if (!showInitialInfoPopUp) {
			popUpDialogChanged = true;
			persistConfig();
			popUpDialogChanged = false;
		}
	}

	private boolean checkNotificationPermission() {
		if (Build.VERSION.SDK_INT >= 33) {
			if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
				Logger.getLogger().logLine("NOTIFICATION PERMISSION IS REQUIRED!");
				Logger.getLogger().message("NOTIFICATION PERMISSION IS REQUIRED!");
				return false;
			}
		}
		return true;
	}


	private void handleInitialInfoPopUp() {
		try {
			ConfigUtil config = CONFIG.getConfigUtil();
			showInitialInfoPopUp  = Boolean.parseBoolean(config.getConfigValue("showInitialInfoPopUp", "true"));
			if (showInitialInfoPopUp) {
				popUpDialog = new Dialog(DNSProxyActivity.this, R.style.Theme_dialog_TitleBar);
				popUpDialog.setContentView(R.layout.popup);
				popUpDialog.setTitle(config.getConfigValue("initialInfoPopUpTitle", ""));
				((TextView)popUpDialog.findViewById(R.id.infoPopUpTxt)).setText(fromHtml(config.getConfigValue("initialInfoPopUpText","")));
				((TextView)popUpDialog.findViewById(R.id.infoPopUpTxt)).setMovementMethod(LinkMovementMethod.getInstance());
				initialInfoPopUpExitBtn = (Button) popUpDialog.findViewById(R.id.closeInfoPopupBtn);
				initialInfoPopUpExitBtn.setOnClickListener(this);
				popUpDialog.show();
				Window window = popUpDialog.getWindow();
				window.setLayout((int) (Math.min(DISPLAY_WIDTH, DISPLAY_HEIGTH)*0.9), WindowManager.LayoutParams.WRAP_CONTENT);
				window.setBackgroundDrawableResource(android.R.color.transparent);
			}
		} catch (Exception e) {
			Logger.getLogger().logException(e);
			showInitialInfoPopUp = false; //some issue => do not try again for future starts of app
		}
	}
	protected void loadAndApplyConfig(final boolean startApp) {

		config = getConfig();

		if (config != null) {

			boolean dnsProxyMode = Boolean.parseBoolean(config.getConfigValue("dnsProxyOnAndroid", "false"));
			boolean vpnInAdditionToProxyMode = Boolean.parseBoolean(config.getConfigValue("vpnInAdditionToProxyMode", "false"));
			NO_VPN = dnsProxyMode && !vpnInAdditionToProxyMode;

			Runnable uiUpdater = new Runnable() {
				@Override
				public void run() {

					//Link field
					link_field_txt = config.getConfigValue("footerLink", "");
					if (!MSG_ACTIVE) {
						link_field.setText(fromHtml(link_field_txt));
						link_field.setMovementMethod(LinkMovementMethod.getInstance());
					}

					//Log formatting
					filterLogFormat = config.getConfigValue("filterLogFormat", "<font color='#E53935'>($CONTENT)</font>");
					acceptLogFormat = config.getConfigValue("acceptLogFormat", "<font color='#43A047'>($CONTENT)</font>");
					fwdLogFormat = config.getConfigValue("fwdLogFormat", "<font color='#FFB300'>($CONTENT)</font>");
					normalLogFormat = config.getConfigValue("normalLogFormat","($CONTENT)");

					try {
						int logSize = Integer.parseInt(config.getConfigValue("logTextSize", "14"));
						logOutView.setTextSize(TypedValue.COMPLEX_UNIT_SP, logSize);
					} catch (Exception e) {
						Logger.getLogger().logLine("Error in log text size setting! "+e.toString());
					}

					ConfigUtil.HostFilterList[] filterEntries = config.getConfiguredFilterLists();
					filterCfg.setEntries(filterEntries);

					filterReloadIntervalView.setText(config.getConfigValue("reloadIntervalDays", "7"));

					enableAdFilterCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("filterActive", "true")));

					enableAutoStartCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("AUTOSTART", "false")));

					enableCloakProtectCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("checkCNAME", "true")));

					keepAwakeCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("androidKeepAwake", "false")));

					proxyModeCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("dnsProxyOnAndroid", "false")));

					proxyLocalOnlyCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("dnsProxyOnlyLocalRequests", "true")));

					rootModeCheck.setChecked(Boolean.parseBoolean(config.getConfigValue("rootModeOnAndroid", "false")));

					//set whitelisted apps into UI
					appSelector.setSelectedApps(config.getConfigValue("androidAppWhiteList", ""));

					if (!CONFIG.isLocal())
						remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null,getResources().getDrawable(R.drawable.remote_icon), null);
					else
						remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null,getResources().getDrawable(R.drawable.remote_icon_outline), null);

					switchingConfig = false;
				}
			};

			runOnUiThread(uiUpdater);

			if (!checkNotificationPermission())
				return;

			if (startApp) {
				handleInitialInfoPopUp();
				startup();
			}

		} else
			switchingConfig =false;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && !scroll_locked) {
			logOutView.setSelection(logOutView.getText().length());
			scrollView.fullScroll(ScrollView.FOCUS_DOWN);
		}
	}

	private void persistConfig() {
		try {

			if (persistManuallyEditConf())
				return;

			boolean changed = persistAdditionalHosts();

			if (filterReloadIntervalView.getText().toString().equals(""))
				filterReloadIntervalView.setText("7");

			getConfig().setConfiguredFilterLists(filterCfg.getFilterEntries());

			String ln;

			BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(CONFIG.readConfig())));
			while ((ln = reader.readLine()) != null) {

                if (ln.trim().startsWith("reloadIntervalDays"))
					getConfig().updateConfigValue("reloadIntervalDays", filterReloadIntervalView.getText().toString());

				else if (ln.trim().startsWith("AUTOSTART"))
					getConfig().updateConfigValue("AUTOSTART", enableAutoStartCheck.isChecked()+"");

				else if (ln.trim().startsWith("androidAppWhiteList"))
					getConfig().updateConfigValue("androidAppWhiteList", appSelector.getSelectedAppPackages());

				else if (ln.trim().startsWith("checkCNAME"))
					getConfig().updateConfigValue("checkCNAME", enableCloakProtectCheck.isChecked()+"");

				else if (ln.trim().startsWith("androidKeepAwake"))
					getConfig().updateConfigValue("androidKeepAwake", keepAwakeCheck.isChecked()+"");

				else if (ln.trim().startsWith("dnsProxyOnAndroid"))
					getConfig().updateConfigValue("dnsProxyOnAndroid", proxyModeCheck.isChecked()+"");

				else if (ln.trim().startsWith("dnsProxyOnlyLocalRequests"))
					getConfig().updateConfigValue("dnsProxyOnlyLocalRequests", proxyLocalOnlyCheck.isChecked()+"");

				else if (ln.trim().startsWith("rootModeOnAndroid"))
					getConfig().updateConfigValue("rootModeOnAndroid", rootModeCheck.isChecked()+"");

				else if (ln.trim().startsWith("filterActive"))
					getConfig().updateConfigValue("filterActive", enableAdFilterCheck.isChecked()+"");

				else if (popUpDialogChanged && ln.trim().startsWith("showInitialInfoPopUp"))
					getConfig().updateConfigValue("showInitialInfoPopUp", showInitialInfoPopUp+"");
			}

			reader.close();

			changed = changed || getConfig().isChanged();


			if (getConfig().isChanged()) {
				CONFIG.updateConfig(getConfig().getConfigBytes());
			}

		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}


	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
			dialog.dismiss();
			persistConfig();
		}
		return false;
	}



	@Override
	public void onClick(View destination) {

		if (switchingConfig) {
			advancedConfigCheck.setChecked(false);
			Logger.getLogger().logLine("Config switch in progress - Wait!");
			return;
		}

		if (destination == logOutView) {
			findViewById(R.id.copyfromlog).setVisibility(View.GONE);
			showFilterRate(true);
			return;
		}

		if (destination == addFilterBtn) {
			onCopyFilterFromLogView(true);
			return;
		} else if (destination == removeFilterBtn) {
			onCopyFilterFromLogView(false);
			return;
		} else if (destination == helpBtn) {
			openBrowser("https://www.zenz-home.com/personaldnsfilter/help/help.php");
			return;
		} else if (destination == dnsField) {
            startActivityForResult(new Intent(this, DNSServerConfigActivity.class), DNSServerConfigActivity.ACTIVITY_RESULT_CODE);
			return;
		} else if (destination == scrollLockField) {
			handleScrollLock();
			return;
		} else if (destination == backupBtn) {
			doBackup();
			return;
		} else if (destination == restoreBtn) {
			doRestore();
			return;
		} else if (destination == restoreDefaultsBtn) {
			doRestoreDefaults();
			return;
		} else if (destination == backupDnBtn || destination == backupUpBtn) {
			handleBackUpIdChange(destination == backupUpBtn);
			return;
		} else if (destination == initialInfoPopUpExitBtn) {
			closeInitialInfoPopUp();
			return;
		}

		if (destination == rootModeCheck && rootModeCheck.isChecked() && !proxyModeCheck.isChecked()) {
			proxyModeCheck.setChecked(true);
			Logger.getLogger().logLine("Enabled also DNS proxy mode as required by root mode!");
		}

		if (destination == proxyModeCheck && !proxyModeCheck.isChecked() && rootModeCheck.isChecked()) {
			rootModeCheck.setChecked(false);
			Logger.getLogger().logLine("Disabled also root mode as it requires DNS proxy mode!");
		}

		persistConfig();

		if (destination == remoteCtrlBtn) {
			if (!switchingConfig) {
				//close advanced settings to force reload from new config
				advancedConfigCheck.setChecked(false);
				if (CONFIG.isLocal())
					pepareRemoteControl();
				else
					handleRemoteControl();
			}
		}
		if (destination == startBtn || destination == enableAdFilterCheck)
			handleRestart();
		if (destination == stopBtn)
			handleExitApp();
		if (destination == reloadFilterBtn)
			handlefilterReload();

		if (destination == advancedConfigCheck || destination == editAdditionalHostsCheck || destination == manuallyEditConfChk || destination == editFilterLoadCheck || destination == appWhiteListCheck || destination == backupRestoreCheck) {
			handleAdvancedConfig((CheckBox)destination);
		}

		if (destination == keepAwakeCheck) {
			if (keepAwakeCheck.isChecked()) {
				remoteWakeLock();
			} else {
				remoteReleaseWakeLock();
			}
		}
	}

	private void openBrowser(String url) {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		try {
			startActivity(browserIntent);
		} catch (Exception e) {
			message("Error opening "+url);
			logLine(e.toString());
		}
	}


	private void handleBackUpIdChange(boolean up) {
		if (up && selectedBackup==availableBackups.length-1)
			selectedBackup = -1;
		else if (!up && selectedBackup == -1)
			selectedBackup = availableBackups.length-1;
		else if (up)
			selectedBackup++;
		else if (!up)
			selectedBackup--;
		String txt = "<default>";
		if (selectedBackup != -1)
			txt = availableBackups[selectedBackup];

		((TextView)findViewById(R.id.BackupId)).setText(txt);
	}

	private void onRemoteConnected(ConfigurationAccess remote){
		CONFIG =remote;
		((GroupedLogger) Logger.getLogger()).detachLogger(myLogger);
		loadAndApplyConfig(false);
		message("CONNECTED TO " + CONFIG);
		logLine("=>CONNECTED to "+ CONFIG +"<=");
	}

	private void persistRemoteControlConfig(String destination, String passphrase) throws Exception {
		String host;
		int port;

		try {
			host = destination.substring(0,destination.indexOf(":"));
			port = Integer.parseInt(destination.substring(destination.indexOf(":")+1));
		} catch (Exception e) {
			throw new Exception("Destination needed in format \"host:port\"!");
		}

		getConfig().updateConfigValue("client_remote_ctrl_host", host);
		getConfig().updateConfigValue("client_remote_ctrl_port", port+"");
		getConfig().updateConfigValue("client_remote_ctrl_keyphrase", passphrase);

		if (getConfig().isChanged())
			CONFIG.updateConfig(getConfig().getConfigBytes());
	}

	private void pepareRemoteControl() {

		final Dialog remoteConnectDialog = new Dialog(this, R.style.Theme_dialog_TitleBar);
		remoteConnectDialog.setContentView(R.layout.remoteconnect);
		final Button okBtn = remoteConnectDialog.findViewById(R.id.remoteEditOkBtn);
		final Button cancleBtn = remoteConnectDialog.findViewById(R.id.remoteEditCancelBtn);

		OnClickListener onClickListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				remoteConnectDialog.dismiss();
				if (v == okBtn) {
					try {
						String destination  = ((EditText)remoteConnectDialog.findViewById(R.id.remote_destination)).getText().toString();
						String passphrase = ((EditText)remoteConnectDialog.findViewById(R.id.passphrase)).getText().toString();
						persistRemoteControlConfig(destination, passphrase);
					} catch (Exception e) {
						Logger.getLogger().logLine("Cannot store remote connect configuration! "+e.toString());
						Logger.getLogger().message(e.getMessage());
						return;
					}
					handleRemoteControl();
				}
			}
		};
		remoteConnectDialog.setOnKeyListener(this);
		remoteConnectDialog.setTitle(getResources().getString(R.string.remoteConnectDialogTitle));

		okBtn.setOnClickListener(onClickListener);
		cancleBtn.setOnClickListener(onClickListener);

		String host;
		String passphrase;
		int port;
		try {
			host = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_host", "");
			passphrase = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_keyphrase", "");
			try {
				port = Integer.parseInt(ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_port", "3333"));
			} catch (Exception e) {
				port = 3333;
			}
		} catch (Exception e) {
			Logger.getLogger().logException(e);
			return;
		}
		((EditText)remoteConnectDialog.findViewById(R.id.remote_destination)).setText(host+":"+port);
		((EditText)remoteConnectDialog.findViewById(R.id.passphrase)).setText(passphrase);
		remoteConnectDialog.show();
		Window window = remoteConnectDialog.getWindow();
		window.setLayout((int) (Math.min(DISPLAY_WIDTH, DISPLAY_HEIGTH)*0.9), WindowManager.LayoutParams.WRAP_CONTENT);
		window.setBackgroundDrawableResource(android.R.color.transparent);
	}




	private void handleRemoteControl() {

		if (switchingConfig)
			// Connecting in progress!
			return;

		switchingConfig = true;
		handleAdvancedConfig(null); // reset UI view
		if (CONFIG.isLocal()) {

			try {
				final String host = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_host", "");
				final String keyphrase = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_keyphrase", "");

				if (host.equals("") || host.equals("0.0.0.0") || keyphrase.equals(""))
					throw new IOException("Remote control not configured!");

				final int port;
				try {
					port = Integer.parseInt(ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_port", "3333"));
				} catch (Exception e) {
					throw new IOException("Invalid connect_remote_ctrl_port");
				}

				Runnable asyncConnect = new Runnable() {
					@Override
					public void run() {

						message("Connecting: " + host + ":" + port);
						MsgTO.setTimeout(150000);

						try {
							onRemoteConnected(ConfigurationAccess.getRemote(myLogger.getNestedLogger(), host, port, keyphrase));
							checkPasscode();
						} catch (IOException e) {
							Logger.getLogger().logLine("Remote connect failed!" + e.toString());
							message("Remote Connect Failed!");
							switchingConfig = false;
						}
					}
				};
				new Thread(asyncConnect).start();

			} catch (IOException e) {
				message(e.getMessage());
				CONFIG = ConfigurationAccess.getLocal();
                switchingConfig = false;
			}
		}
		else {
			CONFIG.releaseConfiguration();
			CONFIG = ConfigurationAccess.getLocal();
			((GroupedLogger) Logger.getLogger()).attachLogger(myLogger);
			loadAndApplyConfig(false);
			message("CONNECTED TO "+ CONFIG);
			logLine("=>CONNECTED to "+ CONFIG +"<=");
			checkPasscode();
		}
	}

	private void handleScrollLock() {
		if (scroll_locked) {
			scroll_locked = false;
			scrollLockField.setText(SCROLL_PAUSE);
			logOutView.setSelection(logOutView.getText().length());
			scrollView.fullScroll(ScrollView.FOCUS_DOWN);
		} else {
			scroll_locked = true;
			scrollLockField.setText(SCROLL_CONTINUE);
		}
	}

	private void setVisibilityForAdvCfg(int v){
		enableAdFilterCheck.setVisibility(v);
		enableAutoStartCheck.setVisibility(v);
		findViewById(R.id.scrolllock).setVisibility(v);
		reloadFilterBtn.setVisibility(v);
		startBtn.setVisibility(v);
		stopBtn.setVisibility(v);
	}

	private void handleAdvancedConfig(CheckBox dest) {

		prepareTransition((ViewGroup) findViewById(R.id.linearLayout4));

		((TextView) findViewById(R.id.backupLog)).setText("");
		if (advancedConfigCheck.isChecked()) {
			setVisibilityForAdvCfg(View.GONE);
			//App whitelisting only supported on SDK >= 21
			if (Build.VERSION.SDK_INT >= 21 && CONFIG.isLocal() && !NO_VPN) {
				appWhiteListCheck.setVisibility(View.VISIBLE);
				appWhitelistingEnabled=true;
			}
			else { //Not supported for remote access currently
				appWhiteListCheck.setVisibility(View.GONE);
				appWhiteListCheck.setChecked(false);
				appWhitelistingEnabled=false;
			}

			findViewById(R.id.advSettingsScroll).setVisibility(View.VISIBLE);

			keepAwakeCheck.setVisibility(View.VISIBLE);
			proxyModeCheck.setVisibility(View.VISIBLE);
			proxyLocalOnlyCheck.setVisibility(View.VISIBLE);
			rootModeCheck.setVisibility(View.VISIBLE);
			enableCloakProtectCheck.setVisibility(View.VISIBLE);
			editAdditionalHostsCheck.setVisibility(View.VISIBLE);
			manuallyEditConfChk.setVisibility(View.VISIBLE);
			editFilterLoadCheck.setVisibility(View.VISIBLE);
			backupRestoreCheck.setVisibility(View.VISIBLE);

			if (dest == null) {
				if (editAdditionalHostsCheck.isChecked())
					dest = editAdditionalHostsCheck;
				if (manuallyEditConfChk.isChecked())
					dest = manuallyEditConfChk;
				else if (editFilterLoadCheck.isChecked())
					dest = editFilterLoadCheck;
				else if (backupRestoreCheck.isChecked())
					dest = backupRestoreCheck;
				else if (appWhiteListCheck.isChecked())
					dest = appWhiteListCheck;
			}

			if (dest != advancedConfigCheck && dest != null) {

				if (dest.isChecked()) {
					keepAwakeCheck.setVisibility(View.GONE);
					proxyModeCheck.setVisibility(View.GONE);
					proxyLocalOnlyCheck.setVisibility(View.GONE);
					rootModeCheck.setVisibility(View.GONE);
					enableCloakProtectCheck.setVisibility(View.GONE);
					if (dest != editAdditionalHostsCheck) {
						editAdditionalHostsCheck.setChecked(false);
						editAdditionalHostsCheck.setVisibility(View.GONE);
					}
					if (dest != manuallyEditConfChk) {
						manuallyEditConfChk.setChecked(false);
						manuallyEditConfChk.setVisibility(View.GONE);
					}
					if (dest != editFilterLoadCheck) {
						editFilterLoadCheck.setChecked(false);
						editFilterLoadCheck.setVisibility(View.GONE);
					}
					if (dest != appWhiteListCheck) {
						appWhiteListCheck.setChecked(false);
						appWhiteListCheck.setVisibility(View.GONE);
					}
					if (dest != backupRestoreCheck) {
						backupRestoreCheck.setChecked(false);
						backupRestoreCheck.setVisibility(View.GONE);
					}
				} else {
					keepAwakeCheck.setVisibility(View.VISIBLE);
					proxyModeCheck.setVisibility(View.VISIBLE);
					proxyLocalOnlyCheck.setVisibility(View.VISIBLE);
					rootModeCheck.setVisibility(View.VISIBLE);
					enableCloakProtectCheck.setVisibility(View.VISIBLE);
					editAdditionalHostsCheck.setVisibility(View.VISIBLE);
					manuallyEditConfChk.setVisibility(View.VISIBLE);
					editFilterLoadCheck.setVisibility(View.VISIBLE);
					if (appWhitelistingEnabled) appWhiteListCheck.setVisibility(View.VISIBLE);
					backupRestoreCheck.setVisibility(View.VISIBLE);
				}
			}

			if (backupRestoreCheck.isChecked()) {
				findViewById(R.id.backupRestoreView).setVisibility(View.VISIBLE);
				try {
					availableBackups=CONFIG.getAvailableBackups();
					selectedBackup = -1;
					((TextView)findViewById(R.id.BackupId)).setText("<default>");
				} catch (IOException e) {
					Logger.getLogger().logException(e);
					backupRestoreCheck.setChecked(false);
					findViewById(R.id.backupRestoreView).setVisibility(View.GONE);
				}

			} else {
				findViewById(R.id.backupRestoreView).setVisibility(View.GONE);
			}

			if (appWhiteListCheck.isChecked()) {
				appWhiteListScroll.setVisibility(View.VISIBLE);
				appSelector.loadAppList();
			} else {
				appSelector.clear();
				appWhiteListScroll.setVisibility(View.GONE);
			}

			if (editFilterLoadCheck.isChecked()) {
				filterCfg.load();
				findViewById(R.id.filtercfgview).setVisibility(View.VISIBLE);
			} else {
				findViewById(R.id.filtercfgview).setVisibility(View.GONE);
				filterCfg.clear();
			}

			if (editAdditionalHostsCheck.isChecked()) {
				loadAdditionalHosts();
				findViewById(R.id.addHostsScroll).setVisibility(View.VISIBLE);
			} else {
				additionalHostsField.setText("");
				additionalHostsChanged = false;
				findViewById(R.id.addHostsScroll).setVisibility(View.GONE);
			}
			if (manuallyEditConfChk.isChecked()) {
				loadManuallyEditConf();
				findViewById(R.id.manuallyEditScroll).setVisibility(View.VISIBLE);
			} else {
				manuallyEditField.setText("");
				manuallyConfEdited = false;
				findViewById(R.id.manuallyEditScroll).setVisibility(View.GONE);
			}
		} else {
			setVisibilityForAdvCfg(View.VISIBLE);
			findViewById(R.id.filtercfgview).setVisibility(View.GONE);
			filterCfg.clear();
			findViewById(R.id.addHostsScroll).setVisibility(View.GONE);
			findViewById(R.id.manuallyEditScroll).setVisibility(View.GONE);
			findViewById(R.id.advSettingsScroll).setVisibility(View.GONE);
			appWhiteListCheck.setChecked(false);
			appSelector.clear();
			findViewById(R.id.backupRestoreView).setVisibility(View.GONE);
			editFilterLoadCheck.setChecked(false);
			backupRestoreCheck.setChecked(false);
			editAdditionalHostsCheck.setChecked(false);
			manuallyEditConfChk.setChecked(false);
			additionalHostsField.setText("");
			manuallyEditField.setText("");
			additionalHostsChanged = false;
			manuallyConfEdited = false;
		}
	}

	private void prepareTransition(ViewGroup v) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			TransitionManager.beginDelayedTransition(v);
		}
	}

	protected synchronized void handleExitApp() {
		DNSFilterService.stop(true);
		Intent intent = new Intent(this, DNSProxyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.putExtra("SHOULD_FINISH", true);
		startActivity(intent);
	}

	private void handleRestart() {
	    if (CONFIG.isLocal()) {

			if (!checkNotificationPermission())
				return;

			if (!DNSFilterService.stop(false))
				return;

			startup();
			loadAndApplyConfig(false);
		}
	    else {
            try {
                CONFIG.restart();
                loadAndApplyConfig(false);
            } catch (IOException e) {
                Logger.getLogger().logException(e);
            }
        }
	}

	protected void startup() {
		try {
			long repeatingLogSuppressTime = Long.parseLong(getConfig().getConfigValue("repeatingLogSuppressTime", "1000"));
			boolean liveLogTimestampEnabled = Boolean.parseBoolean(getConfig().getConfigValue("addLiveLogTimestamp", "false"));
			myLogger.setTimestampFormat(null);
			if (liveLogTimestampEnabled) {
				String timeStampPattern = getConfig().getConfigValue("liveLogTimeStampFormat", "hh:mm:ss");
				myLogger.setTimestampFormat(timeStampPattern);
			}
			myLogger.setSuppressTime(repeatingLogSuppressTime);

			if (DNSFilterService.SERVICE != null) {
				Logger.getLogger().logLine("DNS filter service is running!");
				Logger.getLogger().logLine("Filter statistic since last restart:");
				showFilterRate(false);
				//Logger.getLogger().message("Attached already running Service!");
				return;
			}
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

	}

	private void startSvc() {
		startService(new Intent(this, DNSFilterService.class));
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
			startSvc();
		} else if (requestCode == 0 && resultCode != Activity.RESULT_OK) {
			Logger.getLogger().logLine("VPN dialog not accepted!\r\nPress restart to display dialog again!");
		}

		if (requestCode == DNSServerConfigActivity.ACTIVITY_RESULT_CODE && resultCode == Activity.RESULT_OK) {
			persistConfig();
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
		setMessage(fromHtml("<strong>"+txt+"</strong>"), Color.parseColor("#FFC107"));
		MsgTO.setTimeout(5000);
	}


	private void setMessage(final Spanned msg, final int backgroundColor) {
		runOnUiThread(new Runnable () {

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


	@Override
	public void afterTextChanged(Editable s) {

		if (s == additionalHostsField.getEditableText())
			additionalHostsChanged = true;
		else if (s== manuallyEditField.getEditableText())
			manuallyConfEdited = true;
	}


	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		// TODO Auto-generated method stub

	}


	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {

		NO_ACTION_MENU = -1; //received event - callback working on this device!

		String selection = getSelectedText(true);

		if (Build.VERSION.SDK_INT < 23)
			findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);

		if (selection.indexOf(NO_FILTER_PREF) != -1) {
			add_filter = menu.add(this.getString(R.string.addFilter));
			//add_filter.setOnMenuItemClickListener(this);
		}
		if (selection.indexOf(IN_FILTER_PREF) != -1 ) {
			remove_filter = menu.add(this.getString(R.string.removeFilter));
			//remove_filter.setOnMenuItemClickListener(this);
		}

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

		if (item == add_filter) {
			onCopyFilterFromLogView(true);
		}
		else if (item == remove_filter) {
			onCopyFilterFromLogView(false);
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		if (Build.VERSION.SDK_INT < 23)
			//hide the special copy paste buttons if visible
			findViewById(R.id.copyfromlog).setVisibility(View.GONE);
	}

	@Override
	public void onActionModeStarted(android.view.ActionMode mode) {

	/*	if (Build.VERSION.SDK_INT < 23 && logOutView.hasFocus()) {
			// get action menu on old devices before 6.0
			int start = logOutView.getSelectionStart();
			int end = logOutView.getSelectionEnd();

			if (end > start)
				findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);
		}*/

		super.onActionModeStarted(mode);
	}

	public boolean onTouchActionMenuFallback(View v, MotionEvent event) {

		if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && findViewById(R.id.copyfromlog).getVisibility() == View.VISIBLE)   {
			findViewById(R.id.copyfromlog).setVisibility(View.GONE);
			logOutView.setSelection(logOutView.getText().length());
			return false;
		}
		if (event.getAction() == android.view.MotionEvent.ACTION_UP) {

			String selection = getSelectedText(true);

			if (selection.startsWith(IN_FILTER_PREF) || selection.startsWith(NO_FILTER_PREF)) {
				int start= logOutView.getSelectionStart();
				int end = logOutView.getSelectionEnd();
				findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);
				logOutView.setSelection(start, end);
				return false;
			}
		}
		return false;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if (ACTION_MENU_FALLBACK)
			return onTouchActionMenuFallback(v,event);

		if (Build.VERSION.SDK_INT < 23) {
			getSelectedText(true);
			return false; // for old devices anyhow the fallback option is used
		}

		if (event.getAction() == android.view.MotionEvent.ACTION_UP) {

			String selection = getSelectedText(true);

			if (NO_ACTION_MENU >= 0  && !selection.equals("")) {

				if (NO_ACTION_MENU <=1) {
					NO_ACTION_MENU++;
					doAsyncCheck(); //check again after a second if the action menu works - if not => fallback
				}

				if (NO_ACTION_MENU > 1) {
					//2 times no action ==> action menu not working on this device
					// ==>Fallback to the buttons on top of log view
					findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);
				}
			}
		}
		return false;
	}

	private void doAsyncCheck() {
		new Thread(new Runnable() {
			@Override
			synchronized public void run() {
				try {
					wait (1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Runnable uiRunner = new Runnable() {
					public void run() {
						String selection = getSelectedText(true);
						if (NO_ACTION_MENU >= 0 && !selection.equals("")) {
							findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);
						}
					}
				} ;
				runOnUiThread(uiRunner);
			}
		}).start();
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (!hasFocus)
			findViewById(R.id.copyfromlog).setVisibility(View.GONE);

	}



	private String getSelectedText(boolean fullLine){

		int start= logOutView.getSelectionStart();
		int end = logOutView.getSelectionEnd();
		String selection = "";
		if (end > start) {
			Spannable text = logOutView.getText();
			if (fullLine) {
				while (text.charAt(start) != '\n' && start > 0)
					start--;
				if (start !=0)
					start++;
				while (end < text.length()-1 && text.charAt(end) != '\n')
					end++;

				logOutView.setSelection(start, end);
			}
			selection = text.subSequence(start, end).toString();
		}
		return selection;
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {

		/*String selection = getSelectedText();

		if (item == add_filter) {
			onCopyFilterFromLogView(true);
		}
		else if (item == remove_filter) {
			onCopyFilterFromLogView(false);
		}*/

		return false;
	}

	public void onCopyFilterFromLogView(boolean filter) {

		String selection = getSelectedText(false);

		//close menu
		logOutView.clearFocus();

		applyCopiedHosts(selection.trim(), filter);
	}

	public void remoteWakeLock() {
		try {
			CONFIG.wakeLock();
		} catch (IOException e) {
			Logger.getLogger().logLine("WakeLock failed! "+e);
		}
	}

	public void remoteReleaseWakeLock() {
		try {
			CONFIG.releaseWakeLock();
		} catch (IOException e) {
			Logger.getLogger().logLine("releaseWakeLock failed! " + e);
		}
	}
}
