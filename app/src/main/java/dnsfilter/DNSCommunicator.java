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
package dnsfilter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import util.ExecutionEnvironment;
import util.Logger;

public class DNSCommunicator {

	private static DNSCommunicator INSTANCE = new DNSCommunicator();

	private static int TIMEOUT = 12000;
	DNSServer[] dnsServers = new DNSServer[0];
	int curDNS = -1;
	String lastDNS = "";


	public static DNSCommunicator getInstance() {
		return INSTANCE;
	}

	public synchronized void setDNSServers(DNSServer[] newDNSServers) {
		if (hasChanged(newDNSServers, dnsServers)) {
			dnsServers = newDNSServers;
			if (dnsServers.length > 0) {
				setFastestDNSFromServers();
				lastDNS = dnsServers[curDNS].toString();
			} else {
				lastDNS = "";
				curDNS = -1;
			}
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine("Using updated DNS Servers!");
		}
	}

	private boolean hasChanged(DNSServer[] newDNS, DNSServer[] curDNS) {

		if (newDNS.length != curDNS.length)
			return true;

		for (int i = 0; i < newDNS.length; i++)
			if (!newDNS[i].equals(curDNS[i]))
				return true;

		return false;
	}


	private synchronized void setFastestDNSFromServers() {
		new Thread(new Runnable() {

			boolean ready = false;
			boolean go = false;
			Object monitor = new Object();
			int cnt = 0;

			public void terminated(boolean found) {
				synchronized (monitor) {
					cnt++;
					if (found || cnt == dnsServers.length) {
						ready = true;
						monitor.notifyAll();
					}
				}
			}

			@Override
			public void run() {
				for (int i = 0; i < dnsServers.length; i++) {
					final int finalI = i;
					new Thread(new Runnable() {
						DNSServer dnsServer = dnsServers[finalI];
						int dnsIdx = finalI;
						@Override
						public void run() {
							synchronized (monitor) {
								while (!go) {
									try {
										monitor.wait();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							}
							try {
								long result = dnsServer.testDNS(5);
								Logger.getLogger().logLine(dnsServer+": "+result+"ms");
								synchronized (monitor) {
									if (!ready) {
										curDNS = dnsIdx;
										terminated(true);
									}
								}
							} catch (IOException eio) {
								Logger.getLogger().logLine(dnsServer+": "+eio.getMessage());
								terminated(false);
							}

						}
					}).start();
				}


				synchronized (monitor){

					go = true;
					monitor.notifyAll();

					while (!ready) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					Logger.getLogger().logLine("Selected DNS: ("+dnsServers[curDNS].lastPerformance+"ms)"+dnsServers[curDNS]);

				}
			}



		}).run();
	}

	private synchronized void switchDNSServer(DNSServer current) throws IOException {
		if (current == getCurrentDNS()) {  //might have been switched by other thread already
			//curDNS = (curDNS + 1) % dnsServers.length;
			setFastestDNSFromServers();
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine("Switched DNS Server to:" + getCurrentDNS().getAddress().getHostAddress());
		}
	}

	public synchronized DNSServer getCurrentDNS() throws IOException {
		if (dnsServers.length == 0)
			throw new IOException("No DNS Server initialized!");
		else {
			lastDNS = dnsServers[curDNS].toString();
			return dnsServers[curDNS];
		}
	}

	public String getLastDNSAddress() {
		return lastDNS;
	}

	public void requestDNS(DatagramPacket request, DatagramPacket response) throws IOException {

		DNSServer dns = getCurrentDNS();

		try {
			//DNSServer.getInstance().createDNSServer(DNSServer.UDP,dns,53,TIMEOUT, null).resolve(request, response);
			dns.resolve(request, response);
		} catch (IOException eio) {
			if (ExecutionEnvironment.getEnvironment().hasNetwork())
				switchDNSServer(dns);
			//Logger.getLogger().logException(eio);
			throw eio;
		}

	}
}
