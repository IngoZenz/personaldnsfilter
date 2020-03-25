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
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.text.format.Formatter;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

import dnsfilter.ConfigurationAccess;
import dnsfilter.DNSCommunicator;
import dnsfilter.DNSFilterManager;
import dnsfilter.DNSFilterProxy;
import dnsfilter.DNSResolver;
import dnsfilter.DNSServer;
import ip.IPPacket;
import ip.UDPPacket;
import util.ExecutionEnvironment;
import util.Logger;
import util.Utils;


public class DNSFilterService extends VpnService  {

	private static String VIRTUALDNS_IPV4 = "10.10.10.10";
	private static String VIRTUALDNS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa1";
	private static String ADDRESS_IPV4 = "10.0.2.15";
	private static String ADDRESS_IPV6 = "fdc8:1095:91e1:aaaa:aaaa:aaaa:aaaa:aaa2";

	protected static Intent SERVICE=null;

	private static String START_DNSCRYPTPROXY = "dnscrypt-proxy";
	private static String KILL_DNSCRYPTPROXY = "killall dnscrypt-proxy";

	public static DNSFilterManager DNSFILTER = null;
	public static DNSFilterProxy DNSFILTERPROXY = null;
	private static DNSFilterService INSTANCE = null;

	private static boolean JUST_STARTED = false;

	private static boolean DNS_PROXY_PORT_IS_REDIRECTED = false;

	private static boolean dnsProxyMode = false;
	private static boolean rootMode = false;
	private static boolean vpnInAdditionToProxyMode = false;
	private static boolean is_running = false;
	protected static DNSReqForwarder dnsReqForwarder = new DNSReqForwarder();

	private static int startCounter = 0;

	private boolean blocking = false;
	private VPNRunner vpnRunner=null;
	boolean manageDNSCryptProxy = false;
	boolean dnsCryptProxyStartTriggered = false;
	PendingIntent pendingIntent;
	private int mtu;


	protected static class DNSReqForwarder {
		//used in case vpn mode is disabled for forwaring dns requests to local dns proxy

		String forwardip = null;
		String ipFilePath = ExecutionEnvironment.getEnvironment().getWorkDir()+"forward_ip";

		public String getALocalIpAddress() throws IOException {

			String ip = null;

			NetworkInterface nif = null;

			Enumeration allNifs = NetworkInterface.getNetworkInterfaces();
			while (allNifs.hasMoreElements()) {
				nif = (NetworkInterface) allNifs.nextElement();

				Enumeration ips = nif.getInetAddresses();
				while (ips.hasMoreElements()) {
					InetAddress adr = (InetAddress) ips.nextElement();
					if (!adr.isLoopbackAddress() && adr instanceof Inet4Address) {
						String ipStr = adr.getHostAddress();
						if (nif.getName().startsWith("tun"))
							return ipStr; //prefer vpn interface in order to work together with real VPN
						else if (ip == null)
							ip = ipStr;
					}
				}
			}
			return ip;
		}


		public void clean() {
			File f = new File(ipFilePath);
			try {
				if (f.exists()) {
					InputStream in = new FileInputStream(f);
					String ip = new String(Utils.readFully(in, 100));
					in.close();

					if (!f.delete()){
						throw(new IOException("Cannot delete "+ipFilePath));
					}

					Logger.getLogger().logLine("Cleaning up a previous redirect from previous not correctly terminated execution!");
					runOSCommand(false, "iptables -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination "+ip+":5300");

				}
			} catch (Exception e) {
				Logger.getLogger().logLine(e.toString());
			}

		}


		public synchronized void updateForward() {
			try {
				String ip = getALocalIpAddress();
				if (ip !=null &&!ip.equals(forwardip)){
					clearForward();
					runOSCommand(false, "iptables -t nat -I OUTPUT -p udp --dport 53 -j DNAT --to-destination " + ip + ":5300");
					forwardip = ip;
					FileOutputStream ipFile = new FileOutputStream(ipFilePath);
					ipFile.write(ip.getBytes());
					ipFile.flush();
					ipFile.close();
				}
			} catch (Exception e) {
				Logger.getLogger().logLine(e.getMessage());
			}
		}

		public synchronized void clearForward() {
			if (forwardip == null)
				return;

			try {
				runOSCommand(false, "iptables -t nat -D OUTPUT -p udp --dport 53 -j DNAT --to-destination "+forwardip+":5300");
				forwardip = null;
				if (!new File(ipFilePath).delete()){
					throw (new IOException("Cannot delete "+ipFilePath));
				}
			} catch (Exception e) {
				Logger.getLogger().logLine(e.getMessage());
			}
		}

	}

	private static boolean supportsIPVersion(int version) throws Exception {
		/*Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
		while (nifs.hasMoreElements()) {
			final Iterator<InterfaceAddress> nifAddresses = nifs.nextElement().getInterfaceAddresses().iterator();
			while (nifAddresses.hasNext()) {
				final InetAddress ip = nifAddresses.next().getAddress();
				if ((version == 6 && ip instanceof Inet6Address) || (version == 4 && ip instanceof Inet4Address)) {
					Logger.getLogger().logLine("IPV"+version+" supported:"+ip);
					return true;
				}
			}
		}
		return false;*/
		return DNSFilterManager.getInstance().getConfig().getProperty("ipVersionSupport", "4, 6").indexOf(""+version)!=-1;
	}

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
				int max_resolvers  = Integer.parseInt(DNSFilterManager.getInstance().getConfig().getProperty("maxResolverCount", "100"));;
				while (!stopped) {
					byte[] data = new byte[DNSServer.getBufSize()];
					int length = in.read(data);
				
					if (stopped)
						break;

					boolean skip = false;
					if (DNSResolver.getResolverCount()>max_resolvers) {
						Logger.getLogger().message("Max Resolver Count reached: "+max_resolvers);
						skip = true;
					}

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

								if (!skip)
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

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static Network[] getConnectedNetworks(ConnectivityManager conMan, int type) {
		ArrayList<Network> nwList = new ArrayList<Network>();
		Network[] nw = conMan.getAllNetworks();
		for (int i = 0; i < nw.length;i++) {
			NetworkInfo ni = conMan.getNetworkInfo(nw[i]);
			if (ni != null && (ni.getType() == type || type == -1) && ni.isConnected())
				nwList.add(nw[i]);
		}
		return nwList.toArray(new Network[nwList.size()]);
	}


	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static String[] getDNSviaConnectivityManager() {

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
			return new String[0];

		HashSet<String> result = new HashSet<String>();
		ConnectivityManager connectivityManager = (ConnectivityManager) INSTANCE.getSystemService(CONNECTIVITY_SERVICE);
		Network[] networks = getConnectedNetworks(connectivityManager, ConnectivityManager.TYPE_WIFI); //prefer WiFi
		if (networks.length == 0)
			networks = getConnectedNetworks(connectivityManager, -1); //fallback all networks
		for (Network network : networks) {
			NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);

			LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
			List<InetAddress> dnsList = linkProperties.getDnsServers();
			for (int i = 0; i < dnsList.size(); i++)
				result.add(dnsList.get(i).getHostAddress());

		}
		return result.toArray(new String[result.size()]);
	}


	private static String[] getDNSviaSysProps() {

		try {
			HashSet<String> result = new HashSet<String>();
			Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
			Method method = SystemProperties.getMethod("get", new Class[]{String.class});

			for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4"}) {
				String value = (String) method.invoke(null, name);
				if (value != null && !value.equals("")) {
					result.add(value);
				}
			}
			return result.toArray(new String[result.size()]);

		} catch (Exception e) {
			Logger.getLogger().logException(e);
			return new String[0];
		}

	}

	public static void possibleNetworkChange() {
		detectDNSServers();
		if (rootMode)
			dnsReqForwarder.updateForward();
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

			if (detect && !(rootMode) ) {

				String[] dnsServers = getDNSviaConnectivityManager();

				if (dnsServers.length == 0) {
					if (DNSProxyActivity.debug)
						Logger.getLogger().logLine("Fallback DNS detection via SystemProperties");

					dnsServers = getDNSviaSysProps();

				} else if (DNSProxyActivity.debug)
					Logger.getLogger().logLine("DNS detection via ConnectivityManager");

				try {
					for (int i = 0; i < dnsServers.length; i++) {
						String value = dnsServers[i];
						if (value != null && !value.equals("")) {
							if (DNSProxyActivity.debug)
								Logger.getLogger().logLine("DNS:" + value);
							if (!value.equals(VIRTUALDNS_IPV4) && !value.equals(VIRTUALDNS_IPV6))
								dnsAdrs.add(DNSServer.getInstance().createDNSServer(DNSServer.UDP, InetAddress.getByName(value), 53, timeout, null));
						}
					}
				} catch (Exception e) {
					Logger.getLogger().logException(e);
				}
			}
			if (dnsAdrs.isEmpty()) { //fallback
				if (detect && rootMode)
					Logger.getLogger().message("DNS Detection not possible in rootMode!");
				StringTokenizer fallbackDNS = new StringTokenizer(dnsFilterMgr.getConfig().getProperty("fallbackDNS", ""), ";");
				int cnt = fallbackDNS.countTokens();
				for (int i = 0; i < cnt; i++) {
					String dnsEntry = fallbackDNS.nextToken().trim();
					if (DNSProxyActivity.debug) Logger.getLogger().logLine("DNS:" + dnsEntry);
					try {
						DNSServer dnsServer = DNSServer.getInstance().createDNSServer(dnsEntry, timeout);
						if (rootMode && dnsServer.getPort() == 53)
							throw new IOException("Port 53 not allowed when running in Root Mode! Use Dot or DoH!");
						dnsAdrs.add(DNSServer.getInstance().createDNSServer(dnsEntry, timeout));
					} catch (Exception e) {
						Logger.getLogger().logLine("Cannot create DNS Server for " + dnsEntry + "!\n" + e.toString());
						Logger.getLogger().message("Invalid DNS Server entry: '" + dnsEntry+"'");
					}
				}
			}
			DNSCommunicator.getInstance().setDNSServers(dnsAdrs.toArray(new DNSServer[dnsAdrs.size()]));

		} catch (IOException e) {
			Logger.getLogger().logException(e);
		}
	}



	private ParcelFileDescriptor initVPN() throws Exception {

		mtu = Integer.parseInt(ConfigurationAccess.getLocal().getConfig().getProperty("MTU","3000"));

		Builder builder = new Builder();

		builder.setSession("DNS Filter");

		if (supportsIPVersion(4))
			builder.addAddress(ADDRESS_IPV4, 24).addDnsServer(VIRTUALDNS_IPV4).addRoute(VIRTUALDNS_IPV4, 32);

		if (supportsIPVersion(6))
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

		builder.setMtu(mtu);

		return builder.setConfigureIntent(pendingIntent).establish();
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		AndroidEnvironment.initEnvironment(this);
		INSTANCE = this;
		SERVICE = intent;

		if (DNSFILTER != null) {
			Logger.getLogger().logLine("DNS Filter already running!");
		} else {
			try {

				DNSFilterManager.WORKDIR = DNSProxyActivity.WORKPATH.getAbsolutePath() + "/";
				DNSFILTER = DNSFilterManager.getInstance();
				DNSFILTER.init();
				JUST_STARTED = true; //used in detectDNSServers to ensure eventually changed static DNS Servers config is taken

				dnsProxyMode = Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("dnsProxyOnAndroid", "false"));
				rootMode = Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("rootModeOnAndroid", "false"));
				vpnInAdditionToProxyMode = Boolean.parseBoolean(DNSFILTER.getConfig().getProperty("vpnInAdditionToProxyMode", "false"));

				if (rootMode && !dnsProxyMode) {
					rootMode = false;
					Logger.getLogger().logLine("WARNING! Root Mode only possible in combination with DNS Proxy Mode!");
				}

				if (rootMode) {
					dnsReqForwarder.clean(); //cleanup possible hangig iprules after a crash
					dnsReqForwarder.updateForward();
				}

				registerReceiver(ConnectionChangeReceiver.getInstance(), new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));

				detectDNSServers();

				//start DNS Proxy Mode if configured
				if (dnsProxyMode) {
					if (rootMode)
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

				is_running = true;

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

			// Initialize and start VPN Mode if not disabled

			if (!dnsProxyMode || vpnInAdditionToProxyMode) {
				ParcelFileDescriptor vpnInterface = initVPN();

				if (vpnInterface != null) {
					vpnRunner = new VPNRunner(++startCounter, vpnInterface);
					new Thread(vpnRunner).start();
				} else Logger.getLogger().logLine("Error! Cannot get VPN Interface! Try restart!");
			}


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


	private static void runOSCommand(boolean ignoreError, String command) throws Exception {

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

	private static void runOSCommand(final boolean ignoreError, boolean async, final String command) throws Exception {

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
		shutdown();
		super.onDestroy();
	}

	private boolean shutdown() {
		if (!is_running)
			return true;
		try {
			if (DNSFILTER != null && !DNSFILTER.canStop()) {
				Logger.getLogger().logLine("Cannot stop - pending operation!");
				return false;
			}

			try {
				unregisterReceiver(ConnectionChangeReceiver.getInstance());
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (rootMode) {
				dnsReqForwarder.clearForward();
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
			stopService(SERVICE);
			SERVICE = null;
			is_running = false;
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
			if (instance.manageDNSCryptProxy && appExit)
				try {
					instance.runOSCommand(false, KILL_DNSCRYPTPROXY);
					instance.dnsCryptProxyStartTriggered = false;
				} catch (Exception e) {
					Logger.getLogger().logException(e);
				}
			if (instance.shutdown()) {
				INSTANCE= null;
				return true;
			} else {
				return false;
			}
		}
	}


	public  void reload() throws IOException {
		//only for reloading VPN and dns servers
		//DNS Proxy and dnscrypt proxy are handled seperated

		if (!dnsProxyMode || vpnInAdditionToProxyMode) {
			VPNRunner runningVPN = vpnRunner;

			if (runningVPN != null) {
				vpnRunner.stop();
			}
			DNSFILTER = DNSFilterManager.getInstance();
			ParcelFileDescriptor vpnInterface = null;
			try {
				vpnInterface = initVPN();
			} catch (Exception e) {
				throw new IOException("Cannot initialize VPN!", e);
			}

			if (vpnInterface != null) {
				vpnRunner = new VPNRunner(++startCounter, vpnInterface);
				new Thread(vpnRunner).start();


			} else throw new IOException("Error! Cannot get VPN Interface! Try restart!");
		}
		JUST_STARTED = true; //used in detectDNSServers to ensure eventually changed static DNS Servers config is taken
		detectDNSServers();
	}

	public static void onReload() throws IOException {
		DNSFilterService instance = INSTANCE;
		if (instance != null)
			instance.reload();
		else
			throw new IOException("Service instance is null!");
	}


}
