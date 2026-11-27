package dnsfilter;

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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

import util.ExecutionEnvironment;
import util.GroupedLogger;
import util.Logger;
import util.LoggerInterface;
import util.SuppressRepeatingsLogger;
import util.Utils;

public class DNSFilterProxy implements Runnable {

	DatagramSocket receiver;
	DatagramSocket ipv6Receiver;
	volatile boolean stopped = false;
	int port = 53;

	public DNSFilterProxy(int port) {
		this.port = port;
	}

	private static void initDNS(DNSFilterManager dnsFilterMgr) {
		try {

			boolean detect = Boolean.parseBoolean(dnsFilterMgr.getConfig().getProperty("detectDNS", "true"));
			if (detect) {
				Logger.getLogger().logLine("DNS detection not supported for this device");
				Logger.getLogger().message("DNS detection not supported - Using fallback!");
			}

			int timeout = Integer.parseInt(dnsFilterMgr.getConfig().getProperty("dnsRequestTimeout", "15000"));
			String specList = dnsFilterMgr.getConfig().getProperty("fallbackDNS", "");
			DNSServer[] dnsServers = DNSServer.getInstance().createDNSServers(specList, timeout, false);
			DNSCommunicator.getInstance().setDNSServers(dnsServers);
        } catch (IOException e) {
            Logger.getLogger().logLine("!!!DNS server initialization failed!!!");
            Logger.getLogger().logLine(e.toString());
            Logger.getLogger().message(e.getMessage());
        } catch (Exception e) {
            Logger.getLogger().logException(e);
        }
	}

	public static void main(String[] args) throws Exception {
		class StandaloneEnvironment extends ExecutionEnvironment  {
			
			volatile boolean debug = false;
			volatile boolean debugInit = false;

			
			@Override
			public boolean debug() {
				if (!debugInit) {
					try {
						debug = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("debug", "false"));
					} catch (IOException e) {
						Logger.getLogger().logException(e);
					}
					debugInit=true;
				}
					
				return debug;
			}

			@Override
			public void onReload() {
				debugInit = false;
				DNSFilterProxy.initDNS(DNSFilterManager.getInstance());
			}

			@Override
			public InputStream getAsset(String path) throws IOException {
				InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
				if (in == null)
					throw new IOException("Asset not found: " + path);
				return in;
			}

		}

		class StandaloneLogger  implements LoggerInterface {

			@Override
			public void logLine(String txt) {
				System.out.println(txt);
			}

			@Override
			public void logException(Exception e) {
				e.printStackTrace();
			}

			@Override
			public void log(String txt) {
				System.out.print(txt);

			}

			@Override
			public void message(String txt) {
				logLine(txt);
			}

			@Override
			public void closeLogger() {

			}

		}


		SuppressRepeatingsLogger myLogger = new SuppressRepeatingsLogger(new StandaloneLogger());
		Logger.setLogger(new GroupedLogger(new LoggerInterface[] {myLogger}));
		ExecutionEnvironment.setEnvironment(new StandaloneEnvironment());
		DNSFilterManager filtermgr = DNSFilterManager.getInstance();
		filtermgr.init();

		long repeatingLogSuppressTime = Long.parseLong(DNSFilterManager.getInstance().getConfig().getProperty("repeatingLogSuppressTime", "1000"));
		myLogger.setSuppressTime(repeatingLogSuppressTime);

		boolean liveLogTimestampEnabled = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("addLiveLogTimestamp", "false"));
		myLogger.setTimestampFormat(null);
		if (liveLogTimestampEnabled) {
			String timeStampPattern = DNSFilterManager.getInstance().getConfig().getProperty("liveLogTimeStampFormat", "hh:mm:ss");
			myLogger.setTimestampFormat(timeStampPattern);
		}

		//initialize Bootstrap DNS Servers to be used initially to resolve configured DNS servers
		String bootstrapDNS = "[8.8.8.8]::443::DOH::https://dns.google/dns-query; [2001:4860:4860::8888]::443::DOH::https://dns.google/dns-query";
		bootstrapDNS = DNSFilterManager.getInstance().getConfig().getProperty("bootstrapDNS",bootstrapDNS);
		Logger.getLogger().logLine("Using bootstrapDNS: "+bootstrapDNS);
		DNSServer[] dnsServers = DNSServer.getInstance().createDNSServers(bootstrapDNS, 5000, false);
		DNSCommunicator.getInstance().setDNSServers(dnsServers);

		waitUntilBootstrapServersAreReady();

		int port = Integer.parseInt(DNSFilterManager.getInstance().getConfig().getProperty("dnsProxyPortNonAndroid","53"));
		DNSFilterProxy runner = new DNSFilterProxy(port);

		new Thread(runner).start();
		Thread.sleep(1000); // wait a second for the runner to become ready!
		
		initDNS(filtermgr);
	}

	private static void waitUntilBootstrapServersAreReady() {
		boolean ready = false;
		long deadline = System.currentTimeMillis() + 30000L;
		while (!ready && System.currentTimeMillis() < deadline) {
			try {
				File dnsPerfFile= new File(ExecutionEnvironment.getEnvironment().getWorkDir()+"/dnsperf.info");
				if (dnsPerfFile.exists()) {
					InputStream in = new FileInputStream(dnsPerfFile);
					try {
						String content = new String(Utils.readFully(in, 1024));
						ready = content.contains("Terminated");
					} finally {
						in.close();
					}
				}
				if (!ready) {
					System.out.println("Waiting for bootstrap DNS servers to become ready!");
					Thread.sleep(1000);
				}

			} catch (IOException e) {
				Logger.getLogger().logException(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Logger.getLogger().logLine(e.toString());
				break;
			}
		}
	}

	private static InetAddress openVPN4pDNSf_Adr;

	static {
		try {
			openVPN4pDNSf_Adr = InetAddress.getByName("10.10.10.10");
		} catch (UnknownHostException e) {
			Logger.getLogger().logException(e);
		}
	}

	public static boolean isAlocalAddress(InetAddress addr) throws IOException {

		if (addr.equals(openVPN4pDNSf_Adr)  || addr.isLoopbackAddress() || addr.isAnyLocalAddress())
			return true;

		return NetworkInterface.getByInetAddress(addr) != null;
	}

	private void receiveLoop(DatagramSocket sock, int maxResolvers, boolean enforceLocalCheck) {
		while (!stopped) {
			try {
				byte[] data = new byte[DNSServer.getBufSize()];
				DatagramPacket request = new DatagramPacket(data, 0, DNSServer.getBufSize());
				sock.receive(request);

				boolean permitted = true;
				if (enforceLocalCheck)
					permitted = isAlocalAddress(request.getAddress());

				if (!permitted)
					Logger.getLogger().logLine(request.getAddress()+" not permitted! Only local access!");

				if (DNSResolver.getResolverCount() > maxResolvers) {
					Logger.getLogger().message("Max resolver count reached: "+maxResolvers);
				}
				else if (permitted)
					new Thread(new DNSResolver(request, sock)).start();

			} catch (IOException e) {
				if (!stopped)
					Logger.getLogger().logLine("Exception:" + e.getMessage());
			}
		}
	}

	@Override
	public void run() {
		final int max_resolvers;
		final boolean onlyLocal;
		final boolean androidRootMode;
		try {
			max_resolvers  = Integer.parseInt(DNSFilterManager.getInstance().getConfig().getProperty("maxResolverCount", "100"));
			onlyLocal = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("dnsProxyOnlyLocalRequests", "true"));
			androidRootMode = Boolean.parseBoolean(DNSFilterManager.getInstance().getConfig().getProperty("rootModeOnAndroid", "false"));
		} catch (Exception e) {
			Logger.getLogger().logLine("Exception:Cannot get configuration!");
			Logger.getLogger().logException(e);
			return;
		}
		try {
			if (onlyLocal && (ExecutionEnvironment.getEnvironment().getEnvironmentID() == 0 || androidRootMode)) {
				receiver = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"));
				try {
					ipv6Receiver = new DatagramSocket(port, InetAddress.getByName("::1"));
				} catch (IOException ignored) {
					ipv6Receiver = null; // IPv6 loopback may be unavailable or disabled
				}
			} else {
				receiver = new DatagramSocket(port);
			}

			ExecutionEnvironment.getEnvironment().protectSocket(receiver, 1);
			if (ipv6Receiver != null) {
				ExecutionEnvironment.getEnvironment().protectSocket(ipv6Receiver, 1);
			}

		} catch (IOException eio) {
			Logger.getLogger().logLine("Exception:Cannot open DNS port " + port + "!" + eio.getMessage());
			return;
		}
		Logger.getLogger().logLine("DNSFilterProxy running on port " + port + "!");

		final boolean enforceLocalCheck = onlyLocal && ExecutionEnvironment.getEnvironment().getEnvironmentID() == 1 && !androidRootMode;
		if (ipv6Receiver != null) {
			final DatagramSocket v6Sock = ipv6Receiver;
			new Thread(new Runnable() {
				@Override
				public void run() {
					receiveLoop(v6Sock, max_resolvers, enforceLocalCheck);
				}
			}).start();
		}

		receiveLoop(receiver, max_resolvers, enforceLocalCheck);
		Logger.getLogger().logLine("DNSFilterProxy stopped!");
	}


	public synchronized void stop() {
		stopped = true;
		if (receiver != null)
			receiver.close();
		if (ipv6Receiver != null)
			ipv6Receiver.close();
	}


}
