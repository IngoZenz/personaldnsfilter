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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import dnsfilter.DNSCommunicator;
import dnsfilter.DNSFilterManager;

import util.AsyncBulkLogger;
import util.Logger;
import util.LoggerInterface;
import util.Utils;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;


public class DNSProxyActivity extends Activity implements OnClickListener, LoggerInterface, TextWatcher {
	
	protected static boolean BOOT_START = false;
	
	private Button startBtn;
	private Button stopBtn;	
	private Button reloadFilterBtn;
	private static EditText logOutView;
	private static int logSize = 0;
	private static EditText dnsField;
	private  static CheckBox advancedConfigCheck;
	private  static CheckBox editFilterLoadCheck;
	private  static CheckBox editAdditionalHostsCheck;
	private static CheckBox keepAwakeCheck;
	private static CheckBox enableAutoStartCheck;	
	private static CheckBox enableAdFilterCheck;
	private static EditText advancedConfigField;
	private static EditText additionalHostsField;
	private static TextView scrollLockField;
	private static String SCROLL_PAUSE = "II  ";
	private static String SCROLL_CONTINUE = ">>  ";
	private static boolean scroll_locked = false;

	private static boolean additionalHostsChanged=false;
	private static LoggerInterface myLogger;
	
	private ScrollView scrollView = null;

	private static boolean appStart = true;
	
	public static String DNSNAME=null;		
	public static File WORKPATH=null;
	
	private static String ADDITIONAL_HOSTS_TO_LONG ="additionalHosts.txt too long to edit here!\nSize Limit: 512 KB!\nUse other editor!";
	
	private static WifiLock wifiLock;
	private static WakeLock wakeLock;
	
	private static Intent SERVICE = null;


	private class MyUIThreadLogger implements Runnable {;

		private String m_logStr;

		public MyUIThreadLogger(String logStr) {
			m_logStr = logStr;
		}

		@Override
		public synchronized void run() {

			if (!scroll_locked) {
				if (m_logStr.startsWith("FILTERED")) {
					m_logStr = "<font color='#D03D06'>" + m_logStr.substring(9, m_logStr.length() - 1) + "</font><br>";
					logOutView.append(fromHtml(m_logStr));

				} else if (m_logStr.startsWith("ALLOWED")) {
					m_logStr = "<font color='#23751C'>" + m_logStr.substring(8, m_logStr.length() - 1) + "</font><br>";
					logOutView.append(fromHtml(m_logStr));
				} else {
					logOutView.append(m_logStr);
				}

				logSize = logSize + m_logStr.length();

				if (logSize >= 20000) {
					String logStr = toHtml(logOutView.getEditableText());
					logStr = logStr.substring(logSize - 10000);
					int newLine = logStr.indexOf("<br>");
					if (newLine != -1)
						logStr = logStr.substring(newLine + 4);
					logSize = logStr.length();
					logOutView.setText(fromHtml(logStr));
				}
				if (!scroll_locked) { //do not disturb in case a select and copy is active
					logOutView.setSelection(logOutView.getText().length());
					scrollView.fullScroll(ScrollView.FOCUS_DOWN);
				}
			}
			setTitle("personalDNSfilter (Connections:"+DNSFilterService.openConnectionsCount()+")");
			dnsField.setText(DNSCommunicator.getInstance().getLastDNSAddress());
		}
	}	

	private String toHtml(Spanned txt){
		if (Build.VERSION.SDK_INT>=24)
			return Html.toHtml(txt,0);
		else return Html.toHtml(txt);
	}

	private Spanned fromHtml(String txt) {
		if (Build.VERSION.SDK_INT>=24)
			return Html.fromHtml(txt,0);
		else
			return Html.fromHtml(txt);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getBooleanExtra("SHOULD_FINISH", false)) {
			finish();
			System.exit(0);
		}
		setContentView(R.layout.main);
		setTitle("PersonalDNSFilter (Connections:"+DNSFilterService.openConnectionsCount()+")");
		startBtn = (Button) findViewById(R.id.startBtn);
		startBtn.setOnClickListener(this);
		stopBtn = (Button) findViewById(R.id.stopBtn);
		stopBtn.setOnClickListener(this);
		reloadFilterBtn = (Button) findViewById(R.id.filterReloadBtn);
		reloadFilterBtn.setOnClickListener(this);

		String uiText = "";

		scrollLockField = (TextView)findViewById(R.id.scrolllock);
		if (scroll_locked)
			scrollLockField.setText(SCROLL_CONTINUE);
		else
			scrollLockField.setText(SCROLL_PAUSE);

		scrollLockField.setOnClickListener(this);

		if (logOutView != null)
			uiText = toHtml(logOutView.getEditableText());
		logOutView = (EditText) findViewById(R.id.logOutput);
		logOutView.setText(fromHtml(uiText));
		logOutView.setKeyListener(null);

		uiText = "";

		scrollView = (ScrollView) findViewById(R.id.ScrollView01);

		if (dnsField != null)
			uiText = dnsField.getText().toString();
		dnsField = (EditText) findViewById(R.id.dnsField);
		dnsField.setText(uiText);
		dnsField.setEnabled(false);
		
		boolean checked = enableAdFilterCheck != null && enableAdFilterCheck.isChecked();		
		enableAdFilterCheck = (CheckBox) findViewById(R.id.enableAddFilter);
		enableAdFilterCheck.setChecked(checked);
		enableAdFilterCheck.setOnClickListener(this);
		
		checked = enableAutoStartCheck != null && enableAutoStartCheck.isChecked();
		enableAutoStartCheck = (CheckBox) findViewById(R.id.enableAutoStart);
		enableAutoStartCheck.setChecked(checked);
		enableAutoStartCheck.setOnClickListener(this);
		
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

		if (advancedConfigField != null)
			uiText = advancedConfigField.getText().toString();

		advancedConfigField = (EditText) findViewById(R.id.advancedConfigField);
		advancedConfigField.setText(uiText);	
		
		if (additionalHostsField != null)
			uiText = additionalHostsField.getText().toString();
		
		additionalHostsField = (EditText) findViewById(R.id.additionalHostsField);
		additionalHostsField.setText(uiText);	
		additionalHostsField.addTextChangedListener(this);
		
		handleAdvancedConfig();

		if (myLogger!= null)
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
			if (BOOT_START) {
				Logger.getLogger().logLine("Running on SDK"+Build.VERSION.SDK_INT);
				if (Build.VERSION.SDK_INT>=20) //on older Android we have to keep the app in forgrounnd due to teh VPN Accespt dialog popping up after each reboot.
					finish();
				BOOT_START = false;
			}
			Properties config = getConfig();
			if (config != null) {
				dnsField.setText(config.getProperty("DNS"));	
				enableAdFilterCheck.setChecked(config.getProperty("filterHostsFile")!=null);
				enableAutoStartCheck.setChecked(Boolean.parseBoolean(config.getProperty("AUTOSTART","false")));

				//set advanced formatted config field text
				advancedConfigField.setText(getFormattedAdvCfgText(config));

				logLine("Initializing ...");			
				appStart = false; // now started
				
				handleStart(); //start
			}
		}
	}

	@Override
	public void onWindowFocusChanged (boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && !scroll_locked) {
			logOutView.setSelection(logOutView.getText().length());
			scrollView.fullScroll(ScrollView.FOCUS_DOWN);
		}
	}

	private String getFormattedAdvCfgText(Properties config) {
		String advCfg = "# clear field to restore defaults!\n\nfilterAutoUpdateURL = \n";
		String filterReloadURL = config.getProperty("filterAutoUpdateURL", "");
		StringTokenizer urlTokens = new StringTokenizer(filterReloadURL,";");
		int urlCnt = urlTokens.countTokens();
		for (int i = 0; i < urlCnt; i++) {
			String url = urlTokens.nextToken().trim();
			advCfg = advCfg+"  "+url;
			if (i+1 < urlCnt)
				advCfg = advCfg+";\n";
			else
				advCfg = advCfg+"\n";
		}
		//Logger.getLogger().logLine(advCfg);

		advCfg = advCfg+ "\nreloadIntervalDays = "+config.getProperty("reloadIntervalDays", "4")+"\n";
		return advCfg;
	}


	private Properties getConfig() {
			
		File propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
		if (!propsFile.exists()) {
			Logger.getLogger().logLine(propsFile+" not found! - creating default config!");
			createDefaultConfiguration();
			propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
		} 
		try {
			InputStream in = new FileInputStream(propsFile);
			Properties config= new Properties(); 
			config.load(in);
			in.close();
			
			// check for additionalHosts.txt
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/additionalHosts.txt");
			if (!f.exists()) {
				f.createNewFile();
				FileOutputStream fout = new FileOutputStream(f);
				
				AssetManager assetManager=this.getAssets();
				InputStream defIn = assetManager.open("additionalHosts.txt");
				Utils.copyFully(defIn, fout, true);
			}
			
			//check versions, in case different merge existing configuration with defaults
			File versionFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/VERSION.TXT");
			String vStr="";
			if (versionFile.exists()) {
				InputStream vin = new FileInputStream(versionFile);
				vStr = new String(Utils.readFully(vin, 100));
				vin.close();				
			}
			if (!vStr.equals(DNSFilterManager.VERSION)) {
				//Version Change ==> merge config with new default config
				Logger.getLogger().logLine("Updated version! Previous version:"+vStr+", current version:"+DNSFilterManager.VERSION);
				createDefaultConfiguration();
				config = mergeAndPersistConfig(config);				
			}
			return config;
		} catch (Exception e ){
			Logger.getLogger().logException(e);
			return null;
		}
	}	
	
	private Properties mergeAndPersistConfig(Properties currentConfig) throws IOException {
		String[] currentKeys = currentConfig.keySet().toArray(new String[0]);		
		BufferedReader defCfgReader = new BufferedReader( new InputStreamReader(this.getAssets().open("dnsfilter.conf")));
		File mergedConfig = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
		FileOutputStream mergedout = new FileOutputStream(mergedConfig);
		String ln="";
		while ( (ln = defCfgReader.readLine()) != null) {
			for (int i = 0; i < currentKeys.length; i++) 
				if (ln.startsWith(currentKeys[i])) 
					ln = currentKeys[i]+" = "+currentConfig.getProperty(currentKeys[i],"");
			
			mergedout.write((ln+"\r\n").getBytes());
		}
		defCfgReader.close();
		
		//take over custom properties (such as filter overrules) which are not in def config
		Properties defProps = new Properties();		
		defProps.load(this.getAssets().open("dnsfilter.conf"));
		boolean first = true;
		for (int i = 0; i < currentKeys.length; i++) {
			if (!defProps.containsKey(currentKeys[i])) {
				if (first)
					mergedout.write(("\r\n# Merged custom config from previous config file:\r\n" ).getBytes());
				first = false;
				ln = currentKeys[i]+" = "+currentConfig.getProperty(currentKeys[i],"");
				mergedout.write((ln+"\r\n").getBytes());
			}
		}
		mergedout.flush();
		mergedout.close();
		Logger.getLogger().logLine("Merged configuration 'dnsfilter.conf' after update to version "+DNSFilterManager.VERSION+"!");
		InputStream in = new FileInputStream(mergedConfig);
		Properties config= new Properties(); 
		config.load(in);
		in.close();		
		
		return config;				
	}


	private void createDefaultConfiguration() {
		try {
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter");
			f.mkdir();		
			
			//dnsfilter.conf
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
			f.createNewFile();
			FileOutputStream fout = new FileOutputStream(f);
			
			AssetManager assetManager=this.getAssets();
			InputStream defIn = assetManager.open("dnsfilter.conf");
			Utils.copyFully(defIn, fout, true);
						
			//additionalHosts.txt
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/additionalHosts.txt");
			if (!f.exists()) {
				f.createNewFile();
				fout = new FileOutputStream(f);
				
				assetManager=this.getAssets();
				defIn = assetManager.open("additionalHosts.txt");
				Utils.copyFully(defIn, fout, true);
			}
						
			//VERSION.TXT
			f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/VERSION.TXT");
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


	private void persistConfig() {
		try {
			
			persistAdditionalHosts();
			
			boolean filterAds = enableAdFilterCheck.isChecked();
			
			File propsFile = new File (Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			String ln;

			Properties advancedConfigProps = getAdvancedProps();
			if ((advancedConfigField.getText().toString().trim().equals(""))) {
				restoreAdvancedConfigDefault();
				advancedConfigProps = getAdvancedProps();
			}
			else if (!advCfgValid(advancedConfigProps)) {
				revertAdvancedConfig();
				advancedConfigProps = getAdvancedProps();
			}
			

			BufferedReader reader = new BufferedReader( new InputStreamReader(new FileInputStream(propsFile)));
			while ((ln = reader.readLine())!= null) {
								
				if (ln.trim().startsWith("filterAutoUpdateURL"))
					ln = "filterAutoUpdateURL = "+advancedConfigProps.remove("filterAutoUpdateURL");				
				
				if (ln.trim().startsWith("reloadIntervalDays"))
					ln = "reloadIntervalDays = "+advancedConfigProps.remove("reloadIntervalDays");
				
				if (ln.trim().startsWith("AUTOSTART"))
					ln = "AUTOSTART = "+  enableAutoStartCheck.isChecked();
				
				if (ln.trim().startsWith("#!!!filterHostsFile") && filterAds)
					ln = ln.replace("#!!!filterHostsFile", "filterHostsFile");
				
				if (ln.trim().startsWith("filterHostsFile") && !filterAds)
					ln = ln.replace("filterHostsFile", "#!!!filterHostsFile");
				
				out.write((ln+"\r\n").getBytes());
			}
			
			//read remaining supported properties from advanced config
			Iterator it = advancedConfigProps.keySet().iterator();
			while (it.hasNext()) {	
				String prop = (String) it.next();
				if (prop.equals("reloadIntervalDays")) //new propertry added since first release
					out.write(  (prop+" = "+advancedConfigProps.getProperty(prop,"")+"\r\n").getBytes());
			}
			reader.close();
			out.flush();
			out.close();
			FileOutputStream fout = new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");
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
			
			StringTokenizer urlTokens = new StringTokenizer(urls,";");			
			
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
			} catch (Exception e0){
				throw new Exception("'reloadIntervalDays' property not defined correctly!");
			}
			return true;

		} catch (Exception e) {
			Logger.getLogger().logLine("Exception while validating advanced settings:"+e.getMessage());
			Logger.getLogger().logLine("Advanced settings are invalid - will be reverted!");
			return false;
		}
	}

	private Properties getAdvancedProps()  {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader((new ByteArrayInputStream(advancedConfigField.getText().toString().getBytes()))));
			String ln;
			String result = "";
			while ((ln = reader.readLine()) != null) {
				ln = ln.trim();
				if (ln.endsWith("=") || ln.endsWith(";"))
					result = result +" "+ ln;
				else
					result = result +" "+ ln + "\r\n";
			}
			//Logger.getLogger().logLine(result);
			Properties resultProps = new Properties();
			resultProps.load(new ByteArrayInputStream(result.getBytes()));
			return resultProps;
		} catch (IOException eio){
			Logger.getLogger().logLine("Can nor parse advanced config!\n"+eio.toString());
			return null;
		}
	}


	private void revertAdvancedConfig() throws IOException {
		File propsFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/dnsfilter.conf");	
		InputStream in = new FileInputStream(propsFile);
		restoreAdvancedConfig(in);
	}


	private void restoreAdvancedConfigDefault() throws IOException {		
		AssetManager assetManager=this.getAssets();
		InputStream defIn = assetManager.open("dnsfilter.conf");
		restoreAdvancedConfig(defIn);
	}
	
	private void restoreAdvancedConfig(InputStream in) throws IOException {
		Properties defProps = new Properties();
		defProps.load(in);
		in.close();

		advancedConfigField.setText(getFormattedAdvCfgText(defProps));
	}


	@Override
	public void onClick(View destination) {

		if (destination == scrollLockField) {
			handleScrollLock();
			return;
		}

		persistConfig();

		if (destination == startBtn || destination == enableAdFilterCheck)
			handleStart();
		if (destination == stopBtn)
			handleStop();
		if (destination == reloadFilterBtn)
			handlefilterReload();
		
		if (destination == advancedConfigCheck || destination ==editAdditionalHostsCheck || destination == editFilterLoadCheck ) {
			handleAdvancedConfig();
		}		
		if (destination == keepAwakeCheck) {
			if (keepAwakeCheck.isChecked()) {
				wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "personalHttpProxy");
				wifiLock.acquire();
				wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalHttpProxy");
				wakeLock.acquire();	
				Logger.getLogger().logLine("Aquired WIFI lock and partial wake lock!");
			} else {
				if (wifiLock != null  && wakeLock != null){
					wifiLock.release();
					wakeLock.release();
					wifiLock = null;
					wakeLock = null;
					Logger.getLogger().logLine("Released WIFI lock and partial wake lock!");
				}
			}
		}
	}

	private void handleScrollLock() {
		if (scroll_locked) {
			scroll_locked=false;
			scrollLockField.setText(SCROLL_PAUSE);
			logOutView.setSelection(logOutView.getText().length());
			scrollView.fullScroll(ScrollView.FOCUS_DOWN);
		} else {
			scroll_locked=true;
			scrollLockField.setText(SCROLL_CONTINUE);
		}
	}

	private void handleAdvancedConfig() {
		if (advancedConfigCheck.isChecked()) {			
			keepAwakeCheck.setVisibility(View.VISIBLE);
			editAdditionalHostsCheck.setVisibility(View.VISIBLE);
			editFilterLoadCheck.setVisibility(View.VISIBLE);
			
			if (editFilterLoadCheck.isChecked())
				advancedConfigField.setVisibility(View.VISIBLE);
			else
				advancedConfigField.setVisibility(View.GONE);
			
			if (editAdditionalHostsCheck.isChecked()) {
				loadAdditionalHosts();
				additionalHostsField.setVisibility(View.VISIBLE);
			}
			else {
				additionalHostsField.setText("");
				additionalHostsField.setVisibility(View.GONE);
				additionalHostsChanged = false;
			}
		}
		else {
			advancedConfigField.setVisibility(View.GONE);	
			keepAwakeCheck.setVisibility(View.GONE);
			editAdditionalHostsCheck.setVisibility(View.GONE);
			editFilterLoadCheck.setVisibility(View.GONE);
			advancedConfigField.setVisibility(View.GONE);
			editAdditionalHostsCheck.setChecked(false);
			additionalHostsField.setText("");
			additionalHostsField.setVisibility(View.GONE);
			additionalHostsChanged = false;
		}
	}


	private void loadAdditionalHosts() {
		try {
			File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter/additionalHosts.txt");
			
			if (f.length() > 524288) {
				additionalHostsField.setText(ADDITIONAL_HOSTS_TO_LONG);
				additionalHostsField.setEnabled(false);
				return;
			}
			InputStream in = new FileInputStream(f);
			additionalHostsField.setText(new String(Utils.readFully(in, 1024)));
			additionalHostsChanged = false;
		} catch (IOException eio) {
			Logger.getLogger().logLine("Can not load /PersonalDNSFilter/additionalHosts.txt!\n"+eio.toString() );
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
		
		if (SERVICE!=null) {
			stopService(SERVICE);		
		}
		SERVICE = null;
		
		WORKPATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/PersonalDNSFilter");		

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
			SERVICE=new Intent(this,DNSFilterService.class);		
		
		startService(SERVICE);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
	    	startVPN();
	    } 
	    else if (requestCode == 0 && resultCode != Activity.RESULT_OK) {
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
		additionalHostsChanged=true;		
	}


	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		// TODO Auto-generated method stub
		
	}
}