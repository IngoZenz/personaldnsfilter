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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.StrictMode;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import util.ExecutionEnvironment;
import util.Logger;

public class BootUpReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {

		if (DNSFilterService.SERVICE != null){
			Logger.getLogger().logLine("Service is already running! Exit start on boot execution!");
			return;
		}

		AndroidEnvironment.initEnvironment(context);
		Properties config = getConfig();
		if (config != null && Boolean.parseBoolean(config.getProperty("AUTOSTART", "false"))) {

			boolean proxyOnAndroid = Boolean.parseBoolean(config.getProperty("dnsProxyOnAndroid", "false"));
			boolean vpnAndProxy = Boolean.parseBoolean(config.getProperty("vpnInAdditionToProxyMode", "false"));
			boolean vpnNeeded = !proxyOnAndroid || vpnAndProxy;

			Intent prepareIntent = vpnNeeded ? VpnService.prepare(context) : null;
			if (prepareIntent != null) {
				Logger.getLogger().logLine("VPN permission not granted; cannot start VPN service on boot!");
				if (Build.VERSION.SDK_INT < 29) {
					DNSProxyActivity.BOOT_START = true;
					Intent i = new Intent(context, DNSProxyActivity.class);
					i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					context.startActivity(i);
				}
				return;
			}

			if (Build.VERSION.SDK_INT >= 28 && Build.VERSION.SDK_INT < 31 ) {
				Intent i = new Intent(context, DNSFilterService.class);

				StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().build());
				context.startForegroundService(i);

			} else if (Build.VERSION.SDK_INT >= 31) {

				JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
				if (scheduler == null) {
					Logger.getLogger().logLine("JobScheduler unavailable; cannot schedule service start on boot!");
					return;
				}

				JobInfo job = new JobInfo.Builder(1,
						new ComponentName(context, StartServiceJobService.class))
						.setOverrideDeadline(0)   // run immediately when system is ready
						.setBackoffCriteria(10_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
						.setPersisted(false)
						.build();

				scheduler.schedule(job);

			} else { //SDK <28 start directly the app activity
				DNSProxyActivity.BOOT_START = true;
				Intent i = new Intent(context, DNSProxyActivity.class);
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(i);
			}
		}
	}

	public Properties getConfig() {

		File propsFile = new File(ExecutionEnvironment.getEnvironment().getWorkDir()+"/dnsfilter.conf");

		try {
			InputStream in = new FileInputStream(propsFile);
			Properties config = new Properties();
			config.load(in);
			in.close();
			return config;
		} catch (Exception e) {
			return null;
		}
	}
}