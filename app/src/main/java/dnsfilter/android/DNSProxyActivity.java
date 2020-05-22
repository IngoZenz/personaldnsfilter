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
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSFilterManager;
import util.ExecutionEnvironment;
import util.GroupedLogger;
import util.Logger;
import util.LoggerInterface;
import util.TimeoutListener;
import util.TimoutNotificator;


public class DNSProxyActivity extends Activity implements OnClickListener, LoggerInterface, TextWatcher, DialogInterface.OnKeyListener, ActionMode.Callback, MenuItem.OnMenuItemClickListener,View.OnTouchListener, View.OnFocusChangeListener {


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
	protected static CheckBox enableAutoStartCheck;
	protected static CheckBox rootModeCheck;
	protected static CheckBox enableCloakProtectCheck;
	protected static EditText filterReloadIntervalView;
	protected static FilterConfig filterCfg;
	protected static EditText additionalHostsField;
	protected static TextView scrollLockField;
	protected static Dialog advDNSConfigDia;
	protected static CheckBox manualDNSCheck;
	protected static EditText manualDNSView;
	protected static boolean advDNSConfigDia_open = false;
	protected static String SCROLL_PAUSE = "II  ";
	protected static String SCROLL_CONTINUE = ">>  ";
	protected static boolean scroll_locked = false;
	protected static TextView donate_field;
	protected static int donate_field_color = Color.TRANSPARENT;
	protected static Spanned donate_field_txt = fromHtml("<strong>Want to support us? Feel free to <a href='https://www.paypal.me/iZenz'>DONATE</a></strong>!");
	protected static MenuItem add_filter;
	protected static MenuItem remove_filter;
	protected static String[] availableBackups;
	protected static int selectedBackup;


	protected static boolean additionalHostsChanged = false;
	protected static LoggerInterface myLogger;

	protected ScrollView scrollView = null;

	protected static boolean appStart = true;

	protected static File WORKPATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter");

	protected static String ADDITIONAL_HOSTS_TO_LONG = "additionalHosts.txt too long to edit here!\nSize Limit: 512 KB!\nUse other editor!";

	protected static Properties config = null;
	protected static boolean debug = false;

	protected static int NO_ACTION_MENU = 0;


	protected static String IN_FILTER_PREF = "X \u0009";
	protected static String NO_FILTER_PREF = "✓\u0009";

	//log color and format
	protected static String filterLogFormat;
	protected static String acceptLogFormat;
	protected static String normalLogFormat="($CONTENT)";

	protected static ConfigurationAccess CONFIG = ConfigurationAccess.getLocal();
	protected static boolean switchingConfig = false;

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
			if (CONFIG.isLocal())
				activity.setMessage(donate_field_txt, donate_field_color);
			else
				activity.setMessage(fromHtml("<font color='#F7FB0A'><strong>"+ CONFIG +"</strong></font>"), donate_field_color);
		}

		@Override
		public long getTimoutTime() {
			return timeout;
		}
	};


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

			if (filterHostLog || okHostLog) {

				if (filterHostLog)
					logLn = filterLogFormat.replace("($CONTENT)",logLn)+"<br>";
				else
					logLn = acceptLogFormat.replace("($CONTENT)",logLn)+"<br>";

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
			AndroidEnvironment.initEnvironment(this);

			MsgTO.setActivity(this);

			if (getIntent().getBooleanExtra("SHOULD_FINISH", false)) {
				finish();
				System.exit(0);
			}

			DNSFilterManager.WORKDIR = DNSProxyActivity.WORKPATH.getAbsolutePath() + "/";

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

			FilterConfig.FilterConfigEntry[] cfgEntries = null;
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

			uiText = "";

			advDNSConfigDia = new Dialog(DNSProxyActivity.this, R.style.Theme_dialog_TitleBar);
			advDNSConfigDia.setContentView(R.layout.dnsconfigdialog);
			advDNSConfigDia.setTitle(getResources().getString(R.string.dnsCfgConfigDialogTitle));
			advDNSConfigDia.setOnKeyListener(this);

			boolean checked = manualDNSCheck != null && manualDNSCheck.isChecked();

			manualDNSCheck = (CheckBox) advDNSConfigDia.findViewById(R.id.manualDNSCheck);
			manualDNSCheck.setChecked(checked);

			if (manualDNSView != null)
				uiText = manualDNSView.getText().toString();

			manualDNSView = (EditText) advDNSConfigDia.findViewById(R.id.manualDNS);
			manualDNSView.setText(uiText);

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
				remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.baseline_settings_remote_24px), null);
			else
				remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, getResources().getDrawable(R.drawable.outline_settings_remote_24px), null);

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
			donate_field = (TextView) findViewById(R.id.donate);
			donate_field.setText(donate_field_txt);
			donate_field.setOnClickListener(this);

			Drawable background = donate_field.getBackground();
			if (background instanceof ColorDrawable)
				donate_field_color = ((ColorDrawable) background).getColor();

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

			checked = enableAdFilterCheck != null && enableAdFilterCheck.isChecked();
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

			findViewById(R.id.copyfromlog).setVisibility(View.GONE);

			handleAdvancedConfig(null);

			if (myLogger != null) {
				if (CONFIG.isLocal()) {
					((GroupedLogger) Logger.getLogger()).detachLogger(myLogger);
					((GroupedLogger) Logger.getLogger()).attachLogger(this);
					myLogger = this;
				}
			} else {
				Logger.setLogger(new GroupedLogger(new LoggerInterface[]{this}));
				myLogger = this;
			}

			boolean storagePermission = true;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
					storagePermission = false;
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
					Logger.getLogger().logLine("Need Storage Permissions to start!");
				}
			}

			if (appStart && storagePermission) {
				initAppAndStartup();
			}

		} catch (Exception e){
			dump(e);
			throw new RuntimeException(e);
		}
	}

	private void dump(Exception e) {
		StringWriter str = new StringWriter();
		e.printStackTrace(new PrintWriter(str));
		try {
			FileOutputStream dump = new FileOutputStream(WORKPATH+"/dump-"+System.currentTimeMillis()+".txt");
			dump.write(("TIME: "+new Date()+"\nVERSION: "+DNSFilterManager.VERSION+"\n\n").getBytes());
			dump.write(str.toString().getBytes());
			dump.flush();
			dump.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}


	protected Properties getConfig() {
		try {
			return CONFIG.getConfig();
		} catch (Exception e){
			Logger.getLogger().logException(e);
			return null;
		}
	}



	protected void updateConfig(byte[] cfg ) throws IOException {
		CONFIG.updateConfig(cfg);
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
				for (int i = 0; i < availableBackups.length; i++)
					list.add(availableBackups[i]);

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
			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			backupStatusView.setText("Backup Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Backup Failed! " + eio.getMessage());
		}
	}

	protected void doRestoreDefaults() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			CONFIG.doRestoreDefaults();
			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			loadAndApplyConfig(false);
			backupStatusView.setText("Restore Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Restore Failed! " + eio.getMessage());
		}
	}

	protected void doRestore() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			CONFIG.doRestore(getBackupSubFolder());
			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			loadAndApplyConfig(false);
			backupStatusView.setText("Restore Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Restore Failed! " + eio.getMessage());
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
			if (token.startsWith(IN_FILTER_PREF) || token.startsWith(NO_FILTER_PREF)) {
				entries = entries+token.substring(1).trim()+"\n";
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
		if (BOOT_START) {
			Logger.getLogger().logLine("Running on SDK" + Build.VERSION.SDK_INT);
			if (Build.VERSION.SDK_INT >= 20) //on older Android we have to keep the app in forgrounnd due to teh VPN Accespt dialog popping up after each reboot.
				finish();
			BOOT_START = false;
		}
		loadAndApplyConfig(true);
		appStart = false; // now started
	}


	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
			initAppAndStartup();
		}
		else {
			System.exit(-1);
		}
	}

	protected void loadAndApplyConfig(boolean startApp) {

		config = getConfig();

		if (config != null) {
			Runnable uiUpdater = new Runnable() {
				@Override
				public void run() {

					//Log formatting

					filterLogFormat = config.getProperty("filterLogFormat", "<font color='#D03D06'>($CONTENT)</font>");
					acceptLogFormat = config.getProperty("acceptLogFormat", "<font color='#23751C'>($CONTENT)</font>");
					normalLogFormat = config.getProperty("normalLogFormat","($CONTENT)");

					try {
						int logSize = Integer.parseInt(config.getProperty("logTextSize", "14"));
						logOutView.setTextSize(TypedValue.COMPLEX_UNIT_SP, logSize);
					} catch (Exception e) {
						Logger.getLogger().logLine("Error in log Text Size setting! "+e.toString());
					}

					debug = Boolean.parseBoolean(config.getProperty("debug", "false"));

					manualDNSCheck.setChecked(!Boolean.parseBoolean(config.getProperty("detectDNS", "true")));

					String manualDNS_Help =
							"# Format: <IP>::<PORT>::<PROTOCOL>::<URL END POINT>\n"+
									"# IPV6 Addresses with '::' must be in brackets '[IPV6]'!\n" +
									"# Cloudflare examples below:\n" +
									"# 1.1.1.1::53::UDP (Default DNS on UDP port 53 / just 1.1.1.1 will work as well)\n" +
									"# 1.1.1.1::853::DOT::cloudflare-dns.com (DNS over TLS, domain name is optional)\n" +
									"# 1.1.1.1::443::DOH::https://cloudflare-dns.com/dns-query (DNS over HTTPS)\n\n";
					manualDNSView.setText(manualDNS_Help+config.getProperty("fallbackDNS").replace(";", "\n").replace(" ", ""));

					FilterConfig.FilterConfigEntry[] filterEntries = buildFilterEntries(config);
					filterCfg.setEntries(filterEntries);

					filterReloadIntervalView.setText(config.getProperty("reloadIntervalDays", "7"));

					enableAdFilterCheck.setChecked(config.getProperty("filterHostsFile") != null);
					enableAutoStartCheck.setChecked(Boolean.parseBoolean(config.getProperty("AUTOSTART", "false")));

					enableCloakProtectCheck.setChecked(Boolean.parseBoolean(config.getProperty("checkCNAME", "true")));

					keepAwakeCheck.setChecked(Boolean.parseBoolean(config.getProperty("androidKeepAwake", "false")));

					proxyModeCheck.setChecked(Boolean.parseBoolean(config.getProperty("dnsProxyOnAndroid", "false")));

					rootModeCheck.setChecked(Boolean.parseBoolean(config.getProperty("rootModeOnAndroid", "false")));

					//set whitelisted Apps into UI
					appSelector.setSelectedApps(config.getProperty("androidAppWhiteList", ""));

					if (!CONFIG.isLocal())
						remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null,getResources().getDrawable(R.drawable.baseline_settings_remote_24px), null);
					else
						remoteCtrlBtn.setCompoundDrawablesWithIntrinsicBounds(null, null,getResources().getDrawable(R.drawable.outline_settings_remote_24px), null);

					switchingConfig = false;
				}
			};

			runOnUiThread(uiUpdater);

			if (startApp)
				startup();

		} else
			switchingConfig =false;
	}

	private FilterConfig.FilterConfigEntry[] buildFilterEntries(Properties config) {
		String urls = config.getProperty("filterAutoUpdateURL", "");
		String url_IDs = config.getProperty("filterAutoUpdateURL_IDs", "");
		String url_switchs = config.getProperty("filterAutoUpdateURL_switchs", "");
		String url_categories = config.getProperty("filterAutoUpdateURL_categories", "");

		StringTokenizer urlTokens = new StringTokenizer(urls, ";");
		StringTokenizer urlIDTokens = new StringTokenizer(url_IDs, ";");
		StringTokenizer urlSwitchTokens = new StringTokenizer(url_switchs, ";");
		StringTokenizer categoryTokens = new StringTokenizer(url_categories, ";");

		int count = urlTokens.countTokens();
		FilterConfig.FilterConfigEntry[] result = new FilterConfig.FilterConfigEntry[count];

		for (int i = 0; i < count; i++) {
			String urlHost = null;
			String urlStr = urlTokens.nextToken().trim();
			String url_id = "";
			if (urlIDTokens.hasMoreTokens())
				url_id = urlIDTokens.nextToken().trim();
			else {
				URL url = null;
				try {
					url = new URL(urlStr);
					urlHost=url.getHost();
					url_id = urlHost;
				} catch (MalformedURLException e) {
					Logger.getLogger().logException(e);
					url_id = "-";
				}
			}
			String url_category = "";
			if (categoryTokens.hasMoreTokens())
				url_category = categoryTokens.nextToken().trim();
			else if (urlHost != null)
				url_category = urlHost;
			else {
				URL url = null;
				try {
					url = new URL(urlStr);
					url_category = url.getHost();
				} catch (MalformedURLException e) {
					Logger.getLogger().logException(e);
					url_category = "-";
				}
			}
			boolean active = true;
			if (urlSwitchTokens.hasMoreTokens())
				active = Boolean.parseBoolean(urlSwitchTokens.nextToken().trim());

			result[i] = new FilterConfig.FilterConfigEntry(active, url_category,url_id, urlStr);
		}
		return result;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && !scroll_locked) {
			logOutView.setSelection(logOutView.getText().length());
			scrollView.fullScroll(ScrollView.FOCUS_DOWN);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (advDNSConfigDia_open)
			advDNSConfigDia.dismiss();
	}

	@Override
	protected void onRestoreInstanceState(Bundle outState) {
		if (advDNSConfigDia_open) {
			advDNSConfigDia.show();
			((HorizontalScrollView) advDNSConfigDia.findViewById(R.id.manualDNSScroll)).fullScroll(ScrollView.FOCUS_LEFT);
		}
	}




	public String[] getFilterCfgStrings(FilterConfig.FilterConfigEntry[] filterEntries) {
		String[] result = {"", "", "", ""};
		String dim = "";
		for (int i = 0; i < filterEntries.length; i++) {
			result[0] = result[0] + dim + filterEntries[i].active;
			result[1] = result[1] + dim + filterEntries[i].id;
			result[2] = result[2] + dim + filterEntries[i].url;
			result[3] = result[3] + dim + filterEntries[i].category;
			dim = "; ";
		}
		return result;
	}

	private String getFallbackDNSSettingFromUI(){
		String uiText =  manualDNSView.getText().toString();
		String result="";
		StringTokenizer entries = new StringTokenizer(uiText,"\n");
		while (entries.hasMoreTokens()) {
			String entry = entries.nextToken().trim();
			if (!entry.startsWith("#")&& !entry.equals(""))
				result = result+entry+" ;";
		}
		if (!result.equals(""))
			result = result.substring(0,result.length()-2); // cut last seperator;

		return result;
	}

	private void persistConfig() {
		try {

			boolean changed = persistAdditionalHosts();

			boolean filterAds = enableAdFilterCheck.isChecked();

			if (filterReloadIntervalView.getText().toString().equals(""))
				filterReloadIntervalView.setText("7");

			String[] filterCfgStrings = getFilterCfgStrings(filterCfg.getFilterEntries());

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			String ln;

			BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(CONFIG.readConfig())));
			while ((ln = reader.readLine()) != null) {

				String lnOld = ln;

				if (ln.trim().startsWith("detectDNS"))
					ln = "detectDNS = " + !manualDNSCheck.isChecked();

				else if (ln.trim().startsWith("fallbackDNS"))
					ln = "fallbackDNS = " + getFallbackDNSSettingFromUI();
				else if (ln.trim().startsWith("filterAutoUpdateURL_IDs"))
					ln = "filterAutoUpdateURL_IDs = " + filterCfgStrings[1];

				else if (ln.trim().startsWith("filterAutoUpdateURL_switchs"))
					ln = "filterAutoUpdateURL_switchs = " + filterCfgStrings[0];

				else if (ln.trim().startsWith("filterAutoUpdateURL_categories"))
					ln = "filterAutoUpdateURL_categories = " + filterCfgStrings[3];

				else if (ln.trim().startsWith("filterAutoUpdateURL"))
					ln = "filterAutoUpdateURL = " + filterCfgStrings[2];

				else if (ln.trim().startsWith("reloadIntervalDays"))
					ln = "reloadIntervalDays = " + filterReloadIntervalView.getText();

				else if (ln.trim().startsWith("AUTOSTART"))
					ln = "AUTOSTART = " + enableAutoStartCheck.isChecked();

				else if (ln.trim().startsWith("androidAppWhiteList"))
					ln = "androidAppWhiteList = " + appSelector.getSelectedAppPackages();

				else if (ln.trim().startsWith("checkCNAME"))
					ln = "checkCNAME = " + enableCloakProtectCheck.isChecked();

				else if (ln.trim().startsWith("androidKeepAwake"))
					ln = "androidKeepAwake = " + keepAwakeCheck.isChecked();

				else if (ln.trim().startsWith("dnsProxyOnAndroid"))
					ln = "dnsProxyOnAndroid = " + proxyModeCheck.isChecked();

				else if (ln.trim().startsWith("rootModeOnAndroid"))
					ln = "rootModeOnAndroid = " + rootModeCheck.isChecked();

				else if (ln.trim().startsWith("#!!!filterHostsFile") && filterAds)
					ln = ln.replace("#!!!filterHostsFile", "filterHostsFile");

				else if (ln.trim().startsWith("filterHostsFile") && !filterAds)
					ln = ln.replace("filterHostsFile", "#!!!filterHostsFile");

				out.write((ln + "\r\n").getBytes());

				changed = changed || !lnOld.equals(ln);
			}

			reader.close();
			out.flush();
			out.close();

			if (changed) {
				updateConfig(out.toByteArray());
			}

		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}


	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
			dialog.dismiss();
			advDNSConfigDia_open = false;
			persistConfig();
		}
		return false;
	}



	@Override
	public void onClick(View destination) {

		if (switchingConfig ) {
			advancedConfigCheck.setChecked(false);
			Logger.getLogger().logLine("Config Switch in progress - Wait!");
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
		} else if (destination == donate_field) {
			handleDonate();
			return;
		} else if (destination == helpBtn) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zenz-home.com/personaldnsfilter/help/help.php"));
			startActivity(browserIntent);
			return;
		} else if (destination == dnsField) {
			handleDNSConfigDialog();
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
		}

		if (destination == rootModeCheck && rootModeCheck.isChecked() && !proxyModeCheck.isChecked()) {
			proxyModeCheck.setChecked(true);
			Logger.getLogger().logLine("Enabled also DNS Proxy Mode as required by Root Mode!");
		}

		if (destination == proxyModeCheck && !proxyModeCheck.isChecked() && rootModeCheck.isChecked()) {
			rootModeCheck.setChecked(false);
			Logger.getLogger().logLine("Disabled also Root Mode as it requires DNS Proxy Mode!");
		}

		persistConfig();

		if (destination == remoteCtrlBtn) {
			//close advanced settings to force reload from new config
			advancedConfigCheck.setChecked(false);
			handleAdvancedConfig(null);
			handleRemoteControl();
		}
		if (destination == startBtn || destination == enableAdFilterCheck)
			handleRestart();
		if (destination == stopBtn)
			handleExitApp();
		if (destination == reloadFilterBtn)
			handlefilterReload();

		if (destination == advancedConfigCheck || destination == editAdditionalHostsCheck || destination == editFilterLoadCheck || destination == appWhiteListCheck || destination == backupRestoreCheck) {
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



	private void handleRemoteControl() {

		if (switchingConfig)
			// Connecting in progress!
			return;

		switchingConfig = true;

		if (CONFIG.isLocal()) {

			try {
				final String host = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_host", "");
				final String keyphrase = ConfigurationAccess.getLocal().getConfig().getProperty("client_remote_ctrl_keyphrase", "");

				if (host.equals("") || host.equals("0.0.0.0") || keyphrase.equals(""))
					throw new IOException("Remote Control not configured!");

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
							onRemoteConnected(ConfigurationAccess.getRemote(myLogger, host, port, keyphrase));
						} catch (IOException e) {
							Logger.getLogger().logLine("Remote Connect failed!" + e.toString());
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
			myLogger = this;
			((GroupedLogger) Logger.getLogger()).attachLogger(this);
			loadAndApplyConfig(false);
			message("CONNECTED TO "+ CONFIG);
			logLine("=>CONNECTED to "+ CONFIG +"<=");
		}
	}


	private void handleDonate() {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/IZenz"));
		startActivity(browserIntent);
	}


	private void handleDNSConfigDialog() {
		advDNSConfigDia.show();
		((HorizontalScrollView) advDNSConfigDia.findViewById(R.id.manualDNSScroll)).fullScroll(ScrollView.FOCUS_LEFT);
		advDNSConfigDia_open = true;
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

		((TextView) findViewById(R.id.backupLog)).setText("");
		if (advancedConfigCheck.isChecked()) {
			setVisibilityForAdvCfg(View.GONE);
			//App Whitelisting only supported on SDK >= 21
			if (Build.VERSION.SDK_INT >= 21 && CONFIG.isLocal()) {
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
			rootModeCheck.setVisibility(View.VISIBLE);
			enableCloakProtectCheck.setVisibility(View.VISIBLE);
			editAdditionalHostsCheck.setVisibility(View.VISIBLE);
			editFilterLoadCheck.setVisibility(View.VISIBLE);
			backupRestoreCheck.setVisibility(View.VISIBLE);

			if (dest == null) {
				if (editAdditionalHostsCheck.isChecked())
					dest = editAdditionalHostsCheck;
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
					rootModeCheck.setVisibility(View.GONE);
					enableCloakProtectCheck.setVisibility(View.GONE);
					if (dest != editAdditionalHostsCheck) {
						editAdditionalHostsCheck.setChecked(false);
						editAdditionalHostsCheck.setVisibility(View.GONE);
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
					rootModeCheck.setVisibility(View.VISIBLE);
					enableCloakProtectCheck.setVisibility(View.VISIBLE);
					editAdditionalHostsCheck.setVisibility(View.VISIBLE);
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
		} else {
			setVisibilityForAdvCfg(View.VISIBLE);
			findViewById(R.id.filtercfgview).setVisibility(View.GONE);
			filterCfg.clear();
			findViewById(R.id.addHostsScroll).setVisibility(View.GONE);
			findViewById(R.id.advSettingsScroll).setVisibility(View.GONE);
			appWhiteListCheck.setChecked(false);
			appSelector.clear();
			findViewById(R.id.backupRestoreView).setVisibility(View.GONE);
			editAdditionalHostsCheck.setChecked(false);
			editFilterLoadCheck.setChecked(false);
			backupRestoreCheck.setChecked(false);
			editAdditionalHostsCheck.setChecked(false);
			additionalHostsField.setText("");
			additionalHostsChanged = false;
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

			if (!DNSFilterService.stop(false))
				return;

			startup();
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

		if (DNSFilterService.SERVICE != null) {
			Logger.getLogger().logLine("DNSFilterService is running!");
			Logger.getLogger().logLine("Filter Statistic since last restart:");
			showFilterRate(false);
			//Logger.getLogger().message("Attached already running Service!");
			return;
		}

		try {
			boolean vpnInAdditionToProxyMode = Boolean.parseBoolean(getConfig().getProperty("vpnInAdditionToProxyMode", "false"));
			boolean vpnDisabled = !vpnInAdditionToProxyMode && Boolean.parseBoolean(getConfig().getProperty("dnsProxyOnAndroid", "false"));
			Intent intent = null;
			if (!vpnDisabled)
				intent = VpnService.prepare(this.getApplicationContext());
			if (intent != null) {
				startActivityForResult(intent, 0);
			} else { //already prepared or vpn disabled
				startSvc();
			}
		} catch (NullPointerException e) { // NullPointer might occur on Android 4.4 when vpn already initialized
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
			Logger.getLogger().logLine("VPN Dialog not accepted!\r\nPress Restart to display Dialog again!");
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
		setMessage(fromHtml("<strong>"+txt+"</strong>"), Color.parseColor("#ffcc00"));
		MsgTO.setTimeout(5000);
	}


	private void setMessage(final Spanned msg, final int backgroundColor) {
		runOnUiThread(new Runnable () {

			@Override
			public void run() {
				donate_field.setBackgroundColor(backgroundColor);
				donate_field.setText(msg);
			}
		});
	}

	@Override
	public void closeLogger() {
		// TODO Auto-generated method stub

	}


	@Override
	public void afterTextChanged(Editable s) {
		additionalHostsChanged = true;
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
			add_filter = menu.add(R.string.addFilter);
			//add_filter.setOnMenuItemClickListener(this);
		}
		if (selection.indexOf(IN_FILTER_PREF) != -1 ) {
			remove_filter = menu.add(R.string.removeFilter);
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
			// get Action Menu on old devices before 6.0
			int start = logOutView.getSelectionStart();
			int end = logOutView.getSelectionEnd();

			if (end > start)
				findViewById(R.id.copyfromlog).setVisibility(View.VISIBLE);
		}*/

		super.onActionModeStarted(mode);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if (Build.VERSION.SDK_INT < 23)
			return false; // for old devices anyhow the fallback option is used

		if (event.getAction() == android.view.MotionEvent.ACTION_UP) {

			String selection = getSelectedText(true);

			if (NO_ACTION_MENU >= 0  && !selection.equals("")) {

				if (NO_ACTION_MENU <=1) {
					NO_ACTION_MENU++;
					doAsyncCheck(); //check again after a second if the action menu works - if not => fallback
				}

				if (NO_ACTION_MENU > 1) {
					//2 times no Action ==> Action Menu not working on this device
					// ==>Fallback to the Buttons on top of Log View
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