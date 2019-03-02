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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.StringTokenizer;

import dnsfilter.DNSCommunicator;
import dnsfilter.DNSFilterManager;

import util.Logger;
import util.LoggerInterface;
import util.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
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
import android.app.Dialog;


public class DNSProxyActivity extends Activity implements OnClickListener, LoggerInterface, TextWatcher, DialogInterface.OnKeyListener, ActionMode.Callback, MenuItem.OnMenuItemClickListener,View.OnTouchListener, View.OnFocusChangeListener {

	protected static boolean BOOT_START = false;

	private Button startBtn;
	private Button stopBtn;
	private Button reloadFilterBtn;
	private static EditText logOutView;
	private static TextView dnsField;
	private static CheckBox advancedConfigCheck;
	private static CheckBox editFilterLoadCheck;
	private static CheckBox editAdditionalHostsCheck;
	private static CheckBox backupRestoreCheck;
	private static Button backupBtn;
	private static Button restoreBtn;
	private static Button restoreDefaultsBtn;
	private static TextView addFilterBtn;
	private static TextView removeFilterBtn;
	private static CheckBox appWhiteListCheck;
	private static ScrollView appWhiteListScroll;
	private static AppSelectorView appSelector;
	private static CheckBox keepAwakeCheck;
	private static CheckBox enableAutoStartCheck;
	private static CheckBox enableAdFilterCheck;
	private static EditText filterReloadIntervalView;
	private static FilterConfig filterCfg;
	private static EditText additionalHostsField;
	private static TextView scrollLockField;
	private static Dialog advDNSConfigDia;
	private static CheckBox manualDNSCheck;
	private static EditText manualDNSView;
	private static boolean advDNSConfigDia_open = false;
	private static String SCROLL_PAUSE = "II  ";
	private static String SCROLL_CONTINUE = ">>  ";
	private static boolean scroll_locked = false;
	private static TextView donate_field;
	private static MenuItem add_filter;
	private static MenuItem remove_filter;


	private static boolean additionalHostsChanged = false;
	private static LoggerInterface myLogger;

	private ScrollView scrollView = null;

	private static boolean appStart = true;

	public static String DNSNAME = null;
	public static File WORKPATH = null;

	private static String ADDITIONAL_HOSTS_TO_LONG = "additionalHosts.txt too long to edit here!\nSize Limit: 512 KB!\nUse other editor!";

	private static WifiLock wifiLock;
	private static WakeLock wakeLock;

	private static Intent SERVICE = null;
	private static Properties config = null;
	protected static boolean debug = false;

	private static int NO_ACTION_MENU = 0;


	private static String IN_FILTER_PREF = "X \u0009";
	private static String NO_FILTER_PREF = "✓\u0009";

	private void addToLogView(String logStr) {

		StringTokenizer logLines = new StringTokenizer(logStr,"\n");
		while (logLines.hasMoreElements()) {

			String logLn = logLines.nextToken();

			boolean filterHostLog = logLn.startsWith(IN_FILTER_PREF);
			boolean okHostLog = logLn.startsWith(NO_FILTER_PREF);

			if (filterHostLog || okHostLog) {

				if (filterHostLog)
					logLn = "<font color='#D03D06'>" + logLn + "</font><br>";
				else
					logLn = "<font color='#23751C'>" + logLn + "</font><br>";

				logOutView.append(fromHtml(logLn));
			} else {
				String newLn = "\n";
				if (!logLines.hasMoreElements() && !logStr.endsWith("\n"))
					newLn = "";
				logOutView.append(logLn + newLn);
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
			setTitle("personalDNSfilter V" + DNSFilterManager.VERSION + " (Connections:" + DNSFilterService.openConnectionsCount() + ")");
			dnsField.setText(DNSCommunicator.getInstance().getLastDNSAddress());
		}
	}

	private Spanned fromHtml(String txt) {
		if (Build.VERSION.SDK_INT >= 24)
			return Html.fromHtml(txt, 0);
		else
			return Html.fromHtml(txt);
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getBooleanExtra("SHOULD_FINISH", false)) {
			finish();
			System.exit(0);
		}
		setContentView(R.layout.main);
		setTitle("personalDNSfilter V" + DNSFilterManager.VERSION + " (Connections:" + DNSFilterService.openConnectionsCount() + ")");

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
		backupBtn = (Button) findViewById(R.id.backupBtn);
		backupBtn.setOnClickListener(this);
		restoreBtn = (Button) findViewById(R.id.RestoreBackupBtn);
		restoreBtn.setOnClickListener(this);
		restoreDefaultsBtn = (Button) findViewById(R.id.RestoreDefaultBtn);
		restoreDefaultsBtn.setOnClickListener(this);
		addFilterBtn = (TextView) findViewById(R.id.addFilterBtn);
		addFilterBtn.setOnClickListener(this);
		removeFilterBtn = (TextView) findViewById(R.id.removeFilterBtn);
		removeFilterBtn.setOnClickListener(this);
		donate_field = (TextView)findViewById(R.id.donate);
		donate_field.setText(fromHtml("<strong>Want to support us? Feel free to <a href='https://www.paypal.me/iZenz'>DONATE</a></strong>!"));
		donate_field.setOnClickListener(this);

		scrollLockField = (TextView) findViewById(R.id.scrolllock);
		if (scroll_locked)
			scrollLockField.setText(SCROLL_CONTINUE);
		else
			scrollLockField.setText(SCROLL_PAUSE);

		scrollLockField.setOnClickListener(this);


		Spannable logTxt = null;
		if (logOutView != null)
			logTxt = logOutView.getText();

		logOutView = (EditText) findViewById(R.id.logOutput);

		if (logTxt != null)
			logOutView.setText(logTxt);
		else
			logOutView.setText(fromHtml("<strong><em>****This is personalDNSfilter V+"+DNSFilterManager.VERSION +"****</em></strong><br><br>"));

		logOutView.setKeyListener(null);
		logOutView.setCustomSelectionActionModeCallback(this);
		logOutView.setOnTouchListener(this);
		logOutView.setOnFocusChangeListener(this);
		logOutView.setOnClickListener(this);

		uiText = "";

		scrollView = (ScrollView) findViewById(R.id.ScrollView01);

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

		handleAdvancedConfig();

		if (myLogger != null)
			myLogger.closeLogger();
		/*try {
			Logger.setLogger(new AsyncBulkLogger(this));
		} catch (IOException e) {
			Logger.setLogger(this);
			Logger.getLogger().logException(e);
		}*/
		Logger.setLogger(this);
		myLogger = Logger.getLogger();

		if (appStart) {
			logLine("Initializing ...");
			if (BOOT_START) {
				Logger.getLogger().logLine("Running on SDK" + Build.VERSION.SDK_INT);
				if (Build.VERSION.SDK_INT >= 20) //on older Android we have to keep the app in forgrounnd due to teh VPN Accespt dialog popping up after each reboot.
					finish();
				BOOT_START = false;
			}
			loadAndApplyConfig();
			appStart = false; // now started
		}
	}

	private void loadAndApplyConfig() {

		config = getConfig();

		if (config != null) {
			debug = Boolean.parseBoolean(config.getProperty("debug", "false"));

			releaseWakeLock(); // will be set again below in case configured

			manualDNSCheck.setChecked(!Boolean.parseBoolean(config.getProperty("detectDNS", "true")));

			String manualDNS_Help =
					"# Format: <IP>::<PORT>::<PROTOCOL>::<URL END POINT>\n"+
					"# IPV6 Addresses with '::' must be in brackets '[IPV6]'!\n" +
					"# Cloudflare examples below:\n" +
					"# 1.1.1.1::53::UDP (Default DNS on UDP port 53 / just 1.1.1.1 will work as well)\n" +
					"# 1.1.1.1::853::DoT (DNS over TLS)\n" +
					"# 1.1.1.1::443::DoH::https://cloudflare-dns.com/dns-query (DNS over HTTPS)\n\n";
			manualDNSView.setText(manualDNS_Help+config.getProperty("fallbackDNS").replace(";", "\n").replace(" ", ""));

			FilterConfig.FilterConfigEntry[] filterEntries = buildFilterEntries(config);
			filterCfg.setEntries(filterEntries);

			filterReloadIntervalView.setText(config.getProperty("reloadIntervalDays", "7"));

			enableAdFilterCheck.setChecked(config.getProperty("filterHostsFile") != null);
			enableAutoStartCheck.setChecked(Boolean.parseBoolean(config.getProperty("AUTOSTART", "false")));

			keepAwakeCheck.setChecked(Boolean.parseBoolean(config.getProperty("androidKeepAwake", "false")));
			if (keepAwakeCheck.isChecked())
				requestWakeLock();

			//set whitelisted Apps into UI
			appSelector.setSelectedApps(config.getProperty("androidAppWhiteList", ""));
			handleStart();
		}
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


	private Properties getConfig() {

		File propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
		if (!propsFile.exists()) {
			Logger.getLogger().logLine(propsFile + " not found! - creating default config!");
			createDefaultConfiguration();
			propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
		}
		try {
			InputStream in = new FileInputStream(propsFile);
			Properties config = new Properties();
			config.load(in);
			in.close();

			// check for additionalHosts.txt
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/additionalHosts.txt");
			if (!f.exists()) {
				f.createNewFile();
				FileOutputStream fout = new FileOutputStream(f);

				AssetManager assetManager = this.getAssets();
				InputStream defIn = assetManager.open("additionalHosts.txt");
				Utils.copyFully(defIn, fout, true);
			}

			//check versions, in case different merge existing configuration with defaults
			File versionFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/VERSION.TXT");
			String vStr = "";
			if (versionFile.exists()) {
				InputStream vin = new FileInputStream(versionFile);
				vStr = new String(Utils.readFully(vin, 100));
				vin.close();
			}
			if (!vStr.equals(DNSFilterManager.VERSION)) {
				//Version Change ==> merge config with new default config
				Logger.getLogger().logLine("Updated version! Previous version:" + vStr + ", current version:" + DNSFilterManager.VERSION);
				createDefaultConfiguration();
				config = mergeAndPersistConfig(config);
			}
			return config;
		} catch (Exception e) {
			Logger.getLogger().logException(e);
			return null;
		}
	}

	private Properties mergeAndPersistConfig(Properties currentConfig) throws IOException {
		String[] currentKeys = currentConfig.keySet().toArray(new String[0]);
		BufferedReader defCfgReader = new BufferedReader(new InputStreamReader(this.getAssets().open("dnsfilter.conf")));
		File mergedConfig = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
		FileOutputStream mergedout = new FileOutputStream(mergedConfig);
		String ln = "";
		while ((ln = defCfgReader.readLine()) != null) {
			for (int i = 0; i < currentKeys.length; i++)
				if (ln.startsWith(currentKeys[i] + " ="))
					ln = currentKeys[i] + " = " + currentConfig.getProperty(currentKeys[i], "");

			mergedout.write((ln + "\r\n").getBytes());
		}
		defCfgReader.close();

		//take over custom properties (such as filter overrules) which are not in def config
		Properties defProps = new Properties();
		defProps.load(this.getAssets().open("dnsfilter.conf"));
		boolean first = true;
		for (int i = 0; i < currentKeys.length; i++) {
			if (!defProps.containsKey(currentKeys[i])) {
				if (first)
					mergedout.write(("\r\n# Merged custom config from previous config file:\r\n").getBytes());
				first = false;
				ln = currentKeys[i] + " = " + currentConfig.getProperty(currentKeys[i], "");
				mergedout.write((ln + "\r\n").getBytes());
			}
		}
		mergedout.flush();
		mergedout.close();
		Logger.getLogger().logLine("Merged configuration 'dnsfilter.conf' after update to version " + DNSFilterManager.VERSION + "!");
		InputStream in = new FileInputStream(mergedConfig);
		Properties config = new Properties();
		config.load(in);
		in.close();

		return config;
	}


	private void createDefaultConfiguration() {
		try {
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter");
			f.mkdir();

			//dnsfilter.conf
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
			f.createNewFile();
			FileOutputStream fout = new FileOutputStream(f);

			AssetManager assetManager = this.getAssets();
			InputStream defIn = assetManager.open("dnsfilter.conf");
			Utils.copyFully(defIn, fout, true);

			//additionalHosts.txt
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/additionalHosts.txt");
			if (!f.exists()) {
				f.createNewFile();
				fout = new FileOutputStream(f);

				assetManager = this.getAssets();
				defIn = assetManager.open("additionalHosts.txt");
				Utils.copyFully(defIn, fout, true);
			}

			//VERSION.TXT
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/VERSION.TXT");
			f.createNewFile();
			fout = new FileOutputStream(f);

			fout.write(DNSFilterManager.VERSION.getBytes());

			fout.flush();
			fout.close();

			Logger.getLogger().logLine("Default configuration created successfully!");
		} catch (IOException e) {
			Logger.getLogger().logLine("FAILED creating default Configuration!");
			Logger.getLogger().logException(e);
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

			persistAdditionalHosts();

			boolean filterAds = enableAdFilterCheck.isChecked();

			if (filterReloadIntervalView.getText().toString().equals(""))
				filterReloadIntervalView.setText("7");

			String[] filterCfgStrings = getFilterCfgStrings(filterCfg.getFilterEntries());

			File propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			String ln;

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propsFile)));
			while ((ln = reader.readLine()) != null) {

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

				else if (ln.trim().startsWith("androidKeepAwake"))
					ln = "androidKeepAwake = " + keepAwakeCheck.isChecked();

				else if (ln.trim().startsWith("#!!!filterHostsFile") && filterAds)
					ln = ln.replace("#!!!filterHostsFile", "filterHostsFile");

				else if (ln.trim().startsWith("filterHostsFile") && !filterAds)
					ln = ln.replace("filterHostsFile", "#!!!filterHostsFile");

				out.write((ln + "\r\n").getBytes());
			}

			reader.close();
			out.flush();
			out.close();

			FileOutputStream fout = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/dnsfilter.conf");
			fout.write(out.toByteArray());
			fout.flush();
			fout.close();
			Logger.getLogger().logLine("Config persisted!\nRestart is required in case of configuration changes!");

		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}


	private boolean advCfgValid(Properties advancedConfigProps) {
		try {
			//check filterAutoUpdateURL
			String urls = advancedConfigProps.getProperty("filterAutoUpdateURL");

			if (urls == null)
				throw new Exception("'filterAutoUpdateURL' property not defined!");

			StringTokenizer urlTokens = new StringTokenizer(urls, ";");

			int urlCnt = urlTokens.countTokens();
			for (int i = 0; i < urlCnt; i++) {
				String urlStr = urlTokens.nextToken().trim();
				if (!urlStr.equals("")) {
					new URL(urlStr);
				}
			}

			//check reloadIntervalDays
			try {
				Integer.parseInt(advancedConfigProps.getProperty("reloadIntervalDays"));
			} catch (Exception e0) {
				throw new Exception("'reloadIntervalDays' property not defined correctly!");
			}
			return true;

		} catch (Exception e) {
			Logger.getLogger().logLine("Exception while validating advanced settings:" + e.getMessage());
			Logger.getLogger().logLine("Advanced settings are invalid - will be reverted!");
			return false;
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

	private void requestWakeLock() {
		wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "personalHttpProxy");
		wifiLock.acquire();
		wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalHttpProxy");
		wakeLock.acquire();
		Logger.getLogger().logLine("Aquired WIFI lock and partial wake lock!");
	}

	private void releaseWakeLock() {
		if (wifiLock != null && wakeLock != null) {
			wifiLock.release();
			wakeLock.release();
			wifiLock = null;
			wakeLock = null;
			Logger.getLogger().logLine("Released WIFI lock and partial wake lock!");
		}
	}


	@Override
	public void onClick(View destination) {

		if (destination == logOutView) {
			findViewById(R.id.copyfromlog).setVisibility(View.GONE);
			return;
		}

		if (destination == addFilterBtn) {
			onCopyFilterFromLogView(true);
			return;
		}
		else if (destination == removeFilterBtn) {
			onCopyFilterFromLogView(false);
			return;
		}
		else if (destination == donate_field) {
			handleDonate();
			return;
		}
		else if (destination == dnsField) {
			handleDNSConfigDialog();
			return;
		}

		else if (destination == scrollLockField) {
			handleScrollLock();
			return;
		}

		else if (destination == backupBtn) {
			doBackup();
			return;
		}

		else if (destination == restoreBtn) {
			doRestore();
			return;
		}
		else if (destination == restoreDefaultsBtn) {
			doRestoreDefaults();
			return;
		}


		persistConfig();

		if (destination == startBtn || destination == enableAdFilterCheck)
			handleStart();
		if (destination == stopBtn)
			handleStop();
		if (destination == reloadFilterBtn)
			handlefilterReload();

		if (destination == advancedConfigCheck || destination == editAdditionalHostsCheck || destination == editFilterLoadCheck || destination == appWhiteListCheck || destination == backupRestoreCheck) {
			handleAdvancedConfig();
		}
		if (destination == keepAwakeCheck) {
			if (keepAwakeCheck.isChecked()) {
				requestWakeLock();
			} else {
				releaseWakeLock();
			}
		}
	}

	private void handleDonate() {
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/IZenz"));
		startActivity(browserIntent);
	}

	private void copyLocalFile(String from, String to) throws IOException {
		File fromFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/" + from);
		File toFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/" + to);
		Utils.copyFile(fromFile, toFile);
	}

	private void copyFromAssets(String from, String to) throws IOException {
		AssetManager assetManager = this.getAssets();
		InputStream defIn = assetManager.open(from);
		File toFile = new File((Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/" + to));
		toFile.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(toFile);
		Utils.copyFully(defIn, out, true);
	}

	private void doBackup() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {
			copyLocalFile("dnsfilter.conf", "backup/dnsfilter.conf");
			copyLocalFile("additionalHosts.txt", "backup/additionalHosts.txt");
			copyLocalFile("VERSION.TXT", "backup/VERSION.TXT");
			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			backupStatusView.setText("Backup Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Backup Failed! " + eio.getMessage());
		}
	}

	private void doRestoreDefaults() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {

			if (!DNSFilterService.stop())
				throw new IOException("Can not stop - Retry later!");

			copyFromAssets("dnsfilter.conf", "dnsfilter.conf");
			copyFromAssets("additionalHosts.txt", "additionalHosts.txt");

			//cleanup hostsfile and index in order to force reload
			String filterHostFile = null;
			if (config != null && ((filterHostFile = config.getProperty("filterHostsFile")) != null)) {
				new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/" + filterHostFile).delete();
			}

			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			loadAndApplyConfig();
			backupStatusView.setText("Restore Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Restore Failed! " + eio.getMessage());
		}
	}

	private void doRestore() {
		TextView backupStatusView = findViewById(R.id.backupLog);
		try {

			if (!DNSFilterService.stop())
				throw new IOException("Can not stop - Retry later!");

			copyLocalFile("backup/dnsfilter.conf", "dnsfilter.conf");
			copyLocalFile("backup/additionalHosts.txt", "additionalHosts.txt");
			copyLocalFile("backup/VERSION.TXT", "VERSION.TXT");

			//cleanup hostsfile and index in order to force reload
			String filterHostFile = null;
			if (config != null && ((filterHostFile = config.getProperty("filterHostsFile")) != null)) {
				new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/" + filterHostFile).delete();
			}

			backupStatusView.setTextColor(Color.parseColor("#23751C"));
			loadAndApplyConfig();
			backupStatusView.setText("Restore Success!");
		} catch (IOException eio) {
			backupStatusView.setTextColor(Color.parseColor("#D03D06"));
			backupStatusView.setText("Restore Failed! " + eio.getMessage());
		}
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

	private void handleAdvancedConfig() {
		((TextView) findViewById(R.id.backupLog)).setText("");
		if (advancedConfigCheck.isChecked()) {
			//App Whitelisting only supported on SDK >= 21
			if (Build.VERSION.SDK_INT >= 21)
				appWhiteListCheck.setVisibility(View.VISIBLE);
			keepAwakeCheck.setVisibility(View.VISIBLE);
			editAdditionalHostsCheck.setVisibility(View.VISIBLE);
			editFilterLoadCheck.setVisibility(View.VISIBLE);
			backupRestoreCheck.setVisibility(View.VISIBLE);

			if (backupRestoreCheck.isChecked()) {
				findViewById(R.id.backupRestoreView).setVisibility(View.VISIBLE);
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
			findViewById(R.id.filtercfgview).setVisibility(View.GONE);
			filterCfg.clear();
			findViewById(R.id.addHostsScroll).setVisibility(View.GONE);
			appWhiteListCheck.setVisibility(View.GONE);
			appWhiteListScroll.setVisibility(View.GONE);
			appSelector.clear();
			findViewById(R.id.backupRestoreView).setVisibility(View.GONE);
			keepAwakeCheck.setVisibility(View.GONE);
			editAdditionalHostsCheck.setVisibility(View.GONE);
			editFilterLoadCheck.setVisibility(View.GONE);
			backupRestoreCheck.setVisibility(View.GONE);
			editAdditionalHostsCheck.setChecked(false);
			additionalHostsField.setText("");
			additionalHostsChanged = false;
		}
	}


	private void loadAdditionalHosts() {
		try {
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/additionalHosts.txt");

			if (f.length() > 524288) {
				additionalHostsField.setText(ADDITIONAL_HOSTS_TO_LONG);
				additionalHostsField.setEnabled(false);
				return;
			}
			InputStream in = new FileInputStream(f);
			additionalHostsField.setText(new String(Utils.readFully(in, 1024)));
			additionalHostsChanged = false;
		} catch (IOException eio) {
			Logger.getLogger().logLine("Can not load /PersonalDNSFilter/additionalHosts.txt!\n" + eio.toString());
		}
	}

	private void persistAdditionalHosts() {
		String addHostsTxt = additionalHostsField.getText().toString();
		if (!addHostsTxt.equals("") && !addHostsTxt.equals(ADDITIONAL_HOSTS_TO_LONG)) {
			if (additionalHostsChanged)
				try {
					File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter/additionalHosts.txt");
					FileOutputStream fout = new FileOutputStream(f);
					fout.write(addHostsTxt.getBytes());
					fout.flush();
					fout.close();
				} catch (IOException eio) {
					Logger.getLogger().logLine("Cannot persistAdditionalHosts!\n" + eio.toString());
				}
		}
	}

	private void handlefilterReload() {
		if (DNSFilterService.DNSFILTER != null)
			DNSFilterService.DNSFILTER.triggerUpdateFilter();
		else Logger.getLogger().logLine("DNS Filter is not running!");
	}


	private synchronized void handleStop() {
		if (SERVICE != null)
			stopService(SERVICE);
		Intent intent = new Intent(this, DNSProxyActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.putExtra("SHOULD_FINISH", true);
		startActivity(intent);
	}

	private void handleStart() {

		if (!DNSFilterService.stop())
			return;

		if (SERVICE != null) {
			stopService(SERVICE);
		}
		SERVICE = null;

		WORKPATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter");

		try {
			Intent intent = VpnService.prepare(this.getApplicationContext());
			if (intent != null) {
				startActivityForResult(intent, 0);
			} else { //already prepared
				startVPN();
			}
		} catch (NullPointerException e) { // NullPointer might occur on Android 4.4 when vpn already initialized
			Logger.getLogger().logLine("Seems we are on Android 4.4 or older!");
			startVPN(); // assume it is ok!
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}

	private void startVPN() {
		if (SERVICE == null)
			SERVICE = new Intent(this, DNSFilterService.class);

		startService(SERVICE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
			startVPN();
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
				while (text.charAt(end) != '\n' && end < text.length()-1)
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

		applyCopiedHosts(selection, filter);
	}

	private void applyCopiedHosts(String entryStr, boolean filter) {
		findViewById(R.id.copyfromlog).setVisibility(View.GONE);

		StringTokenizer entryTokens = new StringTokenizer(entryStr, "\n");
		String entries = "";
		while (entryTokens.hasMoreTokens()) {
			String token = entryTokens.nextToken();
			if (token.startsWith(IN_FILTER_PREF) || token.startsWith(NO_FILTER_PREF)) {
				entries = entries+token.substring(1).trim()+"\n";
			}
		}
		DNSFilterManager filterMgr = DNSFilterService.DNSFILTER;
		if (filterMgr != null)
			try {
				filterMgr.updateFilter(entries,filter);
			} catch (IOException e) {
				Logger.getLogger().logException(e);
			}
	}
}