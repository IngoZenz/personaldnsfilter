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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager.WakeLock;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.Vector;

import dnsfilter.DNSCommunicator;
import dnsfilter.DNSFilterManager;
import dnsfilter.DNSFilterProxy;
import dnsfilter.DNSResolver;
import dnsfilter.DNSServer;
import ip.IPPacket;
import ip.UDPPacket;
import util.Logger;


public class DNSFilterService extends VpnService  {

	private static String VIRTUALDNS_IPV4 = "10.10.10.10";
	private static String VIRTUALDNS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa1";
	private static String ADDRESS_IPV4 = "10.0.2.15";
	private static String ADDRESS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa2";

	private static String START_DNSCRYPTPROXY = "dnscrypt-proxy";
	private static String KILL_DNSCRYPTPROXY = "killall dnscrypt-proxy";

	public static DNSFilterManager DNSFILTER = null;
	public static DNSFilterProxy DNSFILTERPROXY = null;
	private static DNSFilterService INSTANCE = null;

	private static boolean JUST_STARTED = false;
	private static boolean DNS_PROXY_PORT_IS_REDIRECTED = false;

	private static int startCounter = 0;

	private boolean blocking = false;
	private VPNRunner vpnRunner=null;
	boolean manageDNSCryptProxy = false;
	boolean dnsCryptProxyStartTriggered = false;
	PendingIntent pendingIntent;


	class VPNRunner implements Runnable {

		ParcelFileDescriptor vpnInterface;
		FileInputStream in = null;
		FileOutputStream out = null;
		Thread thread = null;
		boolean stopped = false;
		int id;


		private VPNRunner(int id, ParcelFileDescriptor vpnInterface){
			this.id=id;
			this.vpnInterface= vpnInterface;
			in = new FileInputStream(vpnInterface.getFileDescriptor());
			out = new FileOutputStream(vpnInterface.getFileDescriptor());
			Logger.getLogger().logLine("VPN Connected!");
		}

		private void stop() {
			stopped = true;
			try {
				in.close();
				out.close();
				vpnInterface.close();
				if (thread != null)
					thread.interrupt();
			} catch (Exception e) {
				Logger.getLogger().logException(e);
			}
		}

		@Override
		public void run() {
			Logger.getLogger().logLine("VPN Runner Thread "+id+" started!");
			thread = Thread.currentThread();
			try {
				while (!stopped) {
					byte[] data = new byte[1024];
					int length = in.read(data);
					if (stopped)
						break;

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
				if (!stopped) { //not stopped
					Logger.getLogger().logLine("VPN);Runner died!");
					Logger.getLogger().logException(e);
				}
			}

			Logger.getLogger().logLine("VPN Runner Thread "+id+" terminated!");
		}

	}



	public static void detectDNSServers() {

		try {
			DNSFilterManager dnsFilterMgr = DNSFILTER;

			if (dnsFilterMgr == null)
				return;

			boolean detect = Boolean.parseBoolean(dnsFilterMgr.getConfig().getProperty("detectDNS", "true"));

			int timeout = 15000;

			try {
				timeout = Integer.parseInt(dnsFilterMgr.getConfig().getProperty("dnsRequestTimeout", "15000"));
			} catch (Exception e) {
				Logger.getLogger().logException(e);
			}

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
								dnsAdrs.add(DNSServer.getInstance().createDNSServer(DNSServer.UDP, InetAddress.getByName(value), 53, 15000, null));
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
						dnsAdrs.add(DNSServer.getInstance().createDNSServer(dnsEntry, timeout));
					} catch (Exception e) {
						Logger.getLogger().logLine("Cannot create DNS Server for " + dnsEntry + "!\n" + e.toString());
						Logger.getLogger().message("Invalid DNS Server entry: '" + dnsEntry);
					}
				}
			}
			DNSCommunicator.getInstance().setDNSServers(dnsAdrs.toArray(new DNSServer[dnsAdrs.size()]));

		} catch (IOException e) {
			Logger.getLogger().logException(e);
		}
	}



	private ParcelFileDescriptor initVPN() throws Exception {

		Builder builder = new Builder();

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

		return builder.setConfigureIntent(pendingIntent).establish();
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		INSTANCE = this;

		registerReceiver(ConnectionChangeReceiver.getInstance(), new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

		if (DNSFILTER != null) {
			Logger.getLogger().logLine("DNS Filter already running!");
		} else {
			try {
				DNSFilterManager.WORKDIR = DNSProxyActivity.WORKPATH.getAbsolutePath() + "/";
				DNSFILTER = DNSFilterManager.getInstance();
				DNSFILTER.init();
				JUST_STARTED = true; //used in detectDNSServers to ensure eventually changed static DNS Servers config is taken
				detectDNSServers();

				//start DNS Proxy Mode if configured 
				if (Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("dnsProxyOnAndroid", "false"))) {
					setUpPortRedir();
					DNSFILTERPROXY = new DNSFilterProxy(5300);
					new Thread(DNSFILTERPROXY).start();
				}

				//run DNSCryptProxy when configured
				manageDNSCryptProxy = Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("manageDNSCryptProxy", "false"));

				if (manageDNSCryptProxy && !dnsCryptProxyStartTriggered) {
					try {
						runOSCommand(true,false, KILL_DNSCRYPTPROXY);
						runOSCommand(false, true,START_DNSCRYPTPROXY+" "+DNSFILTER.getConfig().getProperty("dnsCryptProxyStartOptions",""));
						dnsCryptProxyStartTriggered = true;
					} catch (Exception e) {
						Logger.getLogger().logException(e);
					}
				}

			} catch (Exception e) {
				DNSFILTER = null;
				Logger.getLogger().logException(e);
				return START_STICKY;
			}
		}
		try {

			Intent notificationIntent = new Intent(this, DNSProxyActivity.class);
			pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
			Notification noti;

			// Initialize and start VPN Mode
			ParcelFileDescriptor vpnInterface = initVPN();

			if (vpnInterface != null) {
				vpnRunner = new  VPNRunner(++startCounter, vpnInterface);
				new Thread(vpnRunner).start();
			} else Logger.getLogger().logLine("Error! Cannot get VPN Interface! Try restart!");


			if (android.os.Build.VERSION.SDK_INT >= 16) {

				Notification.Builder notibuilder;
				if (android.os.Build.VERSION.SDK_INT >= 26)
					notibuilder = new Notification.Builder(this, getChannel());
				else
					notibuilder = new Notification.Builder(this);

				noti = notibuilder
						.setContentTitle("DNSFilter is running!")
						.setSmallIcon(R.drawable.icon)
						.setContentIntent(pendingIntent)
						.build();
			} else {
				noti = new Notification(R.drawable.icon, "DNSFilter is running!",0);
			}

			startForeground(1, noti);

		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}

		return START_STICKY;
	}

	private String getChannel() {
		final String NOTIFICATION_CHANNEL_ID = "DNS Filter";

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= 26) {
			mNotificationManager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT));
		}

		return NOTIFICATION_CHANNEL_ID;
	}

	private void setUpPortRedir() {
		if (DNS_PROXY_PORT_IS_REDIRECTED)
			return;
		try {
			runOSCommand(false, "iptables -t nat -A PREROUTING -p udp --dport 53 -j REDIRECT --to-port 5300");
			DNS_PROXY_PORT_IS_REDIRECTED = true;
		} catch (Exception e) {
			Logger.getLogger().logLine("Exception during setting Port redirection:" + e.toString());

		}
	}


	private void runOSCommand(boolean ignoreError, String command) throws Exception {

		Logger.getLogger().logLine("Exec '"+command+"' !");

		Process su = Runtime.getRuntime().exec("su");
		DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
		outputStream.writeBytes(command+"\n");
		outputStream.flush();

		outputStream.writeBytes("exit\n");
		outputStream.flush();

		InputStream stdout = su.getInputStream();

		byte[] buf = new byte[1024];
		int r;

		while ( (r = stdout.read(buf)) != -1) {
			Logger.getLogger().log(new String(buf,0,r));
		}

		InputStream stderr = su.getErrorStream();

		while ( (r = stderr.read(buf)) != -1) {
			Logger.getLogger().log(new String(buf,0,r));
		}

		su.waitFor();
		int exitVal = su.exitValue();
		if (exitVal != 0  && !ignoreError)
			throw new Exception ("Error in process execution: "+exitVal);
	}

	private void runOSCommand(final boolean ignoreError, boolean async, final String command) throws Exception {

		if (!async)
			runOSCommand(ignoreError, command);
		else{
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						runOSCommand(ignoreError, command);
					} catch (Exception e) {
						e.printStackTrace();
						Logger.getLogger().logException(e);
					}
				}
			}).start();
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
			VPNRunner runningVPN = vpnRunner;
			if (runningVPN != null) {
				vpnRunner.stop();
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

	public static boolean stop(boolean appExit) {

		DNSFilterService instance = INSTANCE;

		if (instance == null)
			return true;
		else {
			if (instance.manageDNSCryptProxy)
				try {
					instance.runOSCommand(false, KILL_DNSCRYPTPROXY);
					instance.dnsCryptProxyStartTriggered = false;
				} catch (Exception e) {
					Logger.getLogger().logException(e);
				}
			if (instance.stopVPN()) {
				INSTANCE= null;
				return true;
			} else
				return false;
		}
	}


	public  void reload() throws IOException {
		VPNRunner runningVPN = vpnRunner;
		if (runningVPN != null) {
			vpnRunner.stop();
		}

		ParcelFileDescriptor vpnInterface=null;
		try {
			vpnInterface = initVPN();
		} catch (Exception e){
			throw new IOException("Cannot initialize VPN!",e);
		}

		if (vpnInterface != null) {
			vpnRunner = new  VPNRunner(++startCounter, vpnInterface);
			new Thread(vpnRunner).start();

			JUST_STARTED = true; //used in detectDNSServers to ensure eventually changed static DNS Servers config is taken
			detectDNSServers();
		}
		else throw new IOException("Error! Cannot get VPN Interface! Try restart!");
	}

	public static void onReload() throws IOException {
		DNSFilterService instance = INSTANCE;
		if (instance != null)
			instance.reload();
		else
			throw new IOException("Service instance is null!");
	}


}