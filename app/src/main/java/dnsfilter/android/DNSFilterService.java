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

import dnsfilter.DNSServer;
import ip.IPPacket;
import ip.UDPPacket;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Vector;

import util.ExecutionEnvironment;
import util.ExecutionEnvironmentInterface;
import util.Logger;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import dnsfilter.DNSCommunicator;
import dnsfilter.DNSFilterManager;
import dnsfilter.DNSFilterProxy;
import dnsfilter.DNSResolver;
import util.Utils;

public class DNSFilterService extends VpnService implements Runnable, ExecutionEnvironmentInterface {

	private static String VIRTUALDNS_IPV4 = "10.10.10.10";
	private static String VIRTUALDNS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa1";
	private static String ADDRESS_IPV4 = "10.0.2.15";
	private static String ADDRESS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa2";

	public static DNSFilterManager DNSFILTER = null;
	public static DNSFilterProxy DNSFILTERPROXY = null;
	private static DNSFilterService INSTANCE = null;

	private static boolean JUST_STARTED = false;
	private static boolean DNS_PROXY_PORT_IS_REDIRECTED = false;

	private ParcelFileDescriptor vpnInterface;
	FileInputStream in = null;
	FileOutputStream out = null;

	private boolean blocking = false;
	private static WakeLock wakeLock = null;


	public static void detectDNSServers() {

		DNSFilterManager dnsFilterMgr = DNSFILTER;

		if (dnsFilterMgr == null)
			return;

		boolean detect = Boolean.parseBoolean(dnsFilterMgr.getConfig().getProperty("detectDNS", "true"));

		if (!detect && !JUST_STARTED)
			return;  //only static DNS server config already loaded

		JUST_STARTED = false;

		if (DNSProxyActivity.debug)
			Logger.getLogger().logLine("Detecting DNS Servers...");
		Vector<DNSServer> dnsAdrs = new Vector<DNSServer>();

		if (detect) {
			try {
				Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
				Method method = SystemProperties.getMethod("get", new Class[]{String.class});

				for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4",}) {
					String value = (String) method.invoke(null, name);
					if (value != null && !value.equals("")) {
						if (DNSProxyActivity.debug) Logger.getLogger().logLine("DNS:" + value);
						if (!value.equals(VIRTUALDNS_IPV4) && !value.equals(VIRTUALDNS_IPV6))
							dnsAdrs.add(DNSServer.getInstance().createDNSServer(DNSServer.UDP,InetAddress.getByName(value),53,15000,null));
					}
				}
			} catch (Exception e) {
				Logger.getLogger().logException(e);
			}
		}
		if (dnsAdrs.isEmpty()) { //fallback
			StringTokenizer fallbackDNS = new StringTokenizer(dnsFilterMgr.getConfig().getProperty("fallbackDNS", ""), ";");
			int cnt = fallbackDNS.countTokens();
			for (int i = 0; i < cnt; i++) {
				String dnsEntry = fallbackDNS.nextToken().trim();
				if (DNSProxyActivity.debug) Logger.getLogger().logLine("DNS:" + dnsEntry);
				try {
					dnsAdrs.add(DNSServer.getInstance().createDNSServer(dnsEntry,15000));
				} catch (Exception e) {
					Logger.getLogger().logLine("Invalid fallbackDNS entry: '" +dnsEntry + "'\n" + e.toString());
				}
			}
		}
		DNSCommunicator.getInstance().setDNSServers(dnsAdrs.toArray(new DNSServer[dnsAdrs.size()]));
	}

	public void run() {
		Logger.getLogger().logLine("VPN Runner Thread started!");
		try {
			while (true) {

				byte[] data = new byte[1024];
				int length = in.read(data);

				if (length > 0) {
					try {
						IPPacket parsedIP = new IPPacket(data, 0, length);
						if (parsedIP.getVersion() == 6) {
							if (DNSProxyActivity.debug) { //IPV6 Debug Logging
								Logger.getLogger().logLine("!!!IPV6 Packet!!! Protocol:" + parsedIP.getProt());
								Logger.getLogger().logLine("SourceAddress:" + IPPacket.int2ip(parsedIP.getSourceIP()));
								Logger.getLogger().logLine("DestAddress:" + IPPacket.int2ip(parsedIP.getDestIP()));
								Logger.getLogger().logLine("TTL:" + parsedIP.getTTL());
								Logger.getLogger().logLine("Length:" + parsedIP.getLength());
								if (parsedIP.getProt() == 0) {
									Logger.getLogger().logLine("Hopp by Hopp Header");
									Logger.getLogger().logLine("NextHeader:" + (data[40] & 0xff));
									Logger.getLogger().logLine("Hdr Ext Len:" + (data[41] & 0xff));
									if ((data[40] & 0xff) == 58) // ICMP
										Logger.getLogger().logLine("Received ICMP IPV6 Paket Type:" + (data[48] & 0xff));
								}
							}
						}
						if (parsedIP.checkCheckSum() != 0)
							throw new IOException("IP Header Checksum Error!");

						if (parsedIP.getProt() == 1) {
							if (DNSProxyActivity.debug) Logger.getLogger().logLine("Received ICMP Paket Type:" + (data[20] & 0xff));
						}
						if (parsedIP.getProt() == 17) {

							UDPPacket parsedPacket = new UDPPacket(data, 0, length);
							if (parsedPacket.checkCheckSum() != 0)
								throw new IOException("UDP packet Checksum Error!");

							new Thread(new DNSResolver(parsedPacket, out)).start();
						}
					} catch (IOException e) {
						Logger.getLogger().logLine("IOEXCEPTION: " + e.toString());
					} catch (Exception e) {
						Logger.getLogger().logException(e);
					}
				} else if (!blocking)
					Thread.sleep(1000);
			}

		} catch (Exception e) {
			if (vpnInterface != null) //not stopped
				Logger.getLogger().logLine("EXCEPTION: " + e.toString());
			Logger.getLogger().logLine("VPN Runner Thread terminated!");
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		INSTANCE = this;
		ExecutionEnvironment.setEnvironment(this);

		registerReceiver(ConnectionChangeReceiver.getInstance(), new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

		if (DNSFILTER != null) {
			Logger.getLogger().logLine("DNS Filter already running!");
		} else {
			try {
				DNSFilterManager.WORKDIR = DNSProxyActivity.WORKPATH.getAbsolutePath() + "/";
				DNSFILTER = new DNSFilterManager();
				DNSFILTER.init();
				JUST_STARTED = true; //used in detectDNSServers to ensure eventually changed static DNS Servers config is taken
				detectDNSServers();

				//start DNS Proxy Mode if configured 
				if (Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("dnsProxyOnAndroid", "false"))) {
					setUpPortRedir();
					DNSFILTERPROXY = new DNSFilterProxy(5300);
					new Thread(DNSFILTERPROXY).start();
				}
			} catch (Exception e) {
				DNSFILTER = null;
				Logger.getLogger().logException(e);
				return START_STICKY;
			}
		}
		try {
			// Initialize and start VPN Mode
			Builder builder = new Builder();
			Intent notificationIntent = new Intent(this, DNSProxyActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			builder.setSession("DNS Filter");
			builder.addAddress(ADDRESS_IPV4, 24).addDnsServer(VIRTUALDNS_IPV4).addRoute(VIRTUALDNS_IPV4, 32);
			builder.addAddress(ADDRESS_IPV6, 48).addDnsServer(VIRTUALDNS_IPV6).addRoute(VIRTUALDNS_IPV6, 128);

			// add additional IPs to route e.g. for handling application like
			// google chrome bypassing the DNS via own DNS servers
			StringTokenizer additionalRouteIps = new StringTokenizer(DNSFILTER.getConfig().getProperty("routeIPs", ""), ";");
			int cnt = additionalRouteIps.countTokens();
			if (cnt != 0 && Build.VERSION.SDK_INT < 21) {
				cnt = 0;
				Logger.getLogger().logLine("WARNING!: Setting 'routeIPs' not supported for Android version below 5.01!\n Setting ignored!");
			}
			for (int i = 0; i < cnt; i++) {
				String value = additionalRouteIps.nextToken().trim();
				Logger.getLogger().logLine("Additional route IP:" + value);
				try {
					InetAddress adr = InetAddress.getByName(value);
					int prefix = 32;
					if (adr instanceof Inet6Address)
						prefix = 128;
					builder.addRoute(InetAddress.getByName(value), prefix);
				} catch (UnknownHostException e) {
					Logger.getLogger().logException(e);
				}
			}

			// this app itself should bypass VPN in order to prevent endless recursion
			if (Build.VERSION.SDK_INT >= 21)
				builder.addDisallowedApplication("dnsfilter.android");

			//apply app whitelist
			StringTokenizer appWhiteList = new StringTokenizer(DNSFILTER.getConfig().getProperty("androidAppWhiteList", ""), ",");
			cnt = appWhiteList.countTokens();
			if (cnt != 0 && Build.VERSION.SDK_INT < 21) {
				cnt = 0;
				Logger.getLogger().logLine("WARNING!: Application whitelisting not supported for Android version below 5.01!\n Setting ignored!");
			}
			for (int i = 0; i < cnt; i++) {
				excludeApp(appWhiteList.nextToken().trim(), builder);
			}

			// Android 7/8 has an issue with VPN in combination with some google apps - bypass the filter
			if (Build.VERSION.SDK_INT >= 24 && Build.VERSION.SDK_INT <= 27) { // Android 7/8
				Logger.getLogger().logLine("Running on SDK" + Build.VERSION.SDK_INT);
				excludeApp("com.android.vending", builder); //white list play store
				excludeApp("com.google.android.apps.docs", builder); //white list google drive
				excludeApp("com.google.android.apps.photos", builder); //white list google photos
				excludeApp("com.google.android.gm", builder); //white list gmail
				excludeApp("com.google.android.apps.translate", builder); //white list google translate
			}

			if (Build.VERSION.SDK_INT >= 21) {
				builder.setBlocking(true);
				Logger.getLogger().logLine("Using Blocking Mode!");
				blocking = true;
			}

			vpnInterface = builder.setConfigureIntent(pendingIntent).establish();

			if (vpnInterface != null) {
				in = new FileInputStream(vpnInterface.getFileDescriptor());
				out = new FileOutputStream(vpnInterface.getFileDescriptor());
				Logger.getLogger().logLine("VPN Connected!");
				new Thread(this).start();
			} else Logger.getLogger().logLine("Error! Cannot get VPN Interface! Try restart!");

		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
		return START_STICKY;
	}

	private void setUpPortRedir() {
		if (DNS_PROXY_PORT_IS_REDIRECTED)
			return;
		try {
			Process su = Runtime.getRuntime().exec("su");
			DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

			outputStream.writeBytes("iptables -t nat -A PREROUTING -p udp --dport 53 -j REDIRECT --to-port 5300\n");
			outputStream.flush();

			outputStream.writeBytes("exit\n");
			outputStream.flush();
			InputStream stdout = su.getInputStream();
			Logger.getLogger().logLine("\n" + new String(Utils.readFully(stdout, 1024)));
			su.waitFor();
			Logger.getLogger().logLine("SUCCESS! EXECUTED SU, iptables -t nat -A PREROUTING -p udp --dport 53 -j REDIRECT --to-port 5300!");
			DNS_PROXY_PORT_IS_REDIRECTED = true;
		} catch (Exception e) {
			Logger.getLogger().logLine("Exception during setting Port redirection:" + e.toString());

		}
	}

	@SuppressLint("NewApi")
	private void excludeApp(String app, Builder builder) {
		try {
			builder.addDisallowedApplication(app);
		} catch (PackageManager.NameNotFoundException e) {
			Logger.getLogger().logLine("Error during app whitelisting:" + e.getMessage());
		}
	}


	@Override
	public void onDestroy() {
		Logger.getLogger().logLine("destroyed");
		stopVPN();
		try {
			unregisterReceiver(ConnectionChangeReceiver.getInstance());
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	private boolean stopVPN() {
		try {
			if (DNSFILTER != null && !DNSFILTER.canStop()) {
				Logger.getLogger().logLine("Cannot stop - pending operation!");
				return false;
			}
			ParcelFileDescriptor runningVPN = vpnInterface;
			if (runningVPN != null) {
				vpnInterface = null;
				in.close();
				out.close();
				runningVPN.close();
			}
			//stop eventually running proxy mode
			if (DNSFILTERPROXY != null) {
				DNSFILTERPROXY.stop();
				DNSFILTERPROXY = null;
				Logger.getLogger().logLine("DNSFilterProxy Mode stopped!");
			}
			if (DNSFILTER != null) {
				DNSFILTER.stop();
				DNSFILTER = null;
				Logger.getLogger().logLine("DNSFilter stopped!");
			}
			Thread.sleep(200);
			return true;
		} catch (Exception e) {
			Logger.getLogger().logException(e);
			return false;
		}
	}

	public static boolean stop() {
		if (INSTANCE == null)
			return true;
		else {
			if (INSTANCE.stopVPN()) {
				INSTANCE = null;
				return true;
			} else
				return false;
		}
	}


	public static String openConnectionsCount() {
		return "" + DNSResolver.getResolverCount();
	}


	@Override
	public void wakeLock() {
		wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
		wakeLock.acquire();
	}

	@Override
	public void releaseWakeLock() {
		WakeLock wl = wakeLock;
		if (wl != null)
			wl.release();
	}

	@Override
	public String getWorkDir() {
		return DNSProxyActivity.WORKPATH+"/";
	}

	@Override
	public boolean debug() {
		return DNSProxyActivity.debug;
	}
}