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
import util.Utils;

public class DNSCommunicator {

	private static DNSCommunicator INSTANCE = new DNSCommunicator();

	private static int TIMEOUT = 12000;
	DNSServer[] dnsServers = new DNSServer[0];
	int curDNS = -1;
	String lastDNS = "";


	public static DNSCommunicator getInstance() {
		return INSTANCE;
	}

	public synchronized void setDNSServers(DNSServer[] newDNSServers) throws IOException {

		if (newDNSServers.length > 20)
			throw new IOException ("To many DNS Servers configured - Max 20!");

		if (hasChanged(newDNSServers, dnsServers)) {
			dnsServers = newDNSServers;
			if (dnsServers.length > 0) {
				curDNS = 0;
				lastDNS = dnsServers[curDNS].toString();
				setFastestDNSFromServers(true);
			} else {
				lastDNS = "";
				curDNS = -1;
			}
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine("Using updated DNS Servers!");
		}
	}

	private boolean hasChanged(DNSServer[] newDNS, DNSServer[] curDNS) {
		return !Utils.arrayEqual(newDNS,curDNS);
	}


	private void setFastestDNSFromServers(final boolean acceptCurrent) {
		final DNSServer[] dnsServersCopy;
		final int[] curDNSCopy = new int[1];

		synchronized (this) {
			curDNSCopy[0] = curDNS;
			dnsServersCopy = new DNSServer[this.dnsServers.length];
			for (int i = 0; i < this.dnsServers.length; i++)
				dnsServersCopy[i]= this.dnsServers[i];
		}


		new Thread(new Runnable() {

			boolean ready = false;
			boolean go = false;
			Object monitor = new Object();
			int cnt = 0;

			public void terminated(boolean found) {
				synchronized (monitor) {
					cnt++;
					if (found || cnt == dnsServersCopy.length) {
						ready = true;
						monitor.notifyAll();
					}
				}
			}

			@Override
			public void run() {

				//Prepare Test Thread per DNS Server

				for (int i = 0; i < dnsServersCopy.length; i++) {
					final int finalI = i;
					new Thread(new Runnable() {
						DNSServer dnsServer = dnsServersCopy[finalI];
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
										if (acceptCurrent || dnsIdx != curDNSCopy[0]) {
											curDNSCopy[0] = dnsIdx;
											terminated(true);
										} else {
											Logger.getLogger().logLine(dnsServer+" already set! Preferring different one!");
											terminated(false);
										}
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

					// Trigger the Test Threads to start testing

					go = true;
					monitor.notifyAll();

					// Wait for the first successfull Thread to finish

					while (!ready) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Take over result in case no other DNS Serverlist change was in between
					synchronized (this) {

						if (Utils.arrayEqual(dnsServersCopy, dnsServers)) {

							curDNS = curDNSCopy[0];

							if (curDNS != -1) {
								Logger.getLogger().logLine("Selected DNS: (" + dnsServersCopy[curDNS].lastPerformance + "ms) " + dnsServersCopy[curDNS]);
								lastDNS = dnsServers[curDNS].toString();
							}
						}
					}
				}
			}
		}).start();
	}

	private synchronized void switchDNSServer(DNSServer current) throws IOException {
		if (current == getCurrentDNS()) {  //might have been switched by other thread already
			//curDNS = (curDNS + 1) % dnsServers.length;
			setFastestDNSFromServers(false);
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
