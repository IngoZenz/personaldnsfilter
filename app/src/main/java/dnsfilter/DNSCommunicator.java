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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Date;

import util.ExecutionEnvironment;
import util.Logger;
import util.Utils;

public class DNSCommunicator {

	private static DNSCommunicator INSTANCE = new DNSCommunicator();

	private static int TIMEOUT = 12000;
	DNSServer[] dnsServers = new DNSServer[0];
	DNSServer[] currentCheckingDNServers;
	int curDNS = -1;
	volatile String lastDNS = "";


	public static DNSCommunicator getInstance() {
		return INSTANCE;
	}

	public synchronized void setDNSServers(DNSServer[] newDNSServers) throws IOException {

		if (newDNSServers.length > 20)
			throw new IOException ("Too many DNS servers configured - Add max 20!");

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
			Logger.getLogger().logLine("Using updated DNS servers!");
	}

	private void setFastestDNSFromServers(final boolean acceptCurrent)  {
		final DNSServer[] dnsServersCopy;
		final int[] curDNSCopy = new int[1];
		final FileOutputStream[] dnsPerfOut = new FileOutputStream[1];
		final File dnsPerfFile = new File(ExecutionEnvironment.getEnvironment().getWorkDir()+"/dnsperf.info");

		synchronized (this) {
			if (dnsServers.length == 0) {
				curDNS = -1;
				return;
			}
			if (dnsServers.length == 1) {
				curDNS = 0;
				lastDNS = dnsServers[0].toString();
				try {
					FileOutputStream out = new FileOutputStream(dnsPerfFile);
					try {
						out.write(("#DNS Response Times\r\n#Started: " + new Date() + "\r\n\r\n" + dnsServers[0] + ": single server configured\r\n\r\n#Terminated: " + new Date() + "\r\n\r\n").getBytes());
						out.flush();
					} finally {
						out.close();
					}
				} catch (IOException eio) {
					Logger.getLogger().logLine("Can't create dnsperf.info file!\n"+eio);
				}
				return; //no alternative!
			}
			try {

				if (currentCheckingDNServers != null && Utils.arrayEqual(currentCheckingDNServers, this.dnsServers))
					return; // already triggered!

				if (!dnsPerfFile.exists() || dnsPerfFile.delete()) {
					dnsPerfOut[0] = new FileOutputStream(dnsPerfFile);
					dnsPerfOut[0].write(("#DNS Response Times\r\n#Started: " + new Date() + "\r\n\r\n").getBytes());
				} else
					throw new IOException("Current file cannot be overwritten!");
			} catch (IOException eio) {
				Logger.getLogger().logLine("Can't create dnsperf.info file!\n"+eio);
			}
			curDNSCopy[0] = curDNS;
			dnsServersCopy = new DNSServer[this.dnsServers.length];
			for (int i = 0; i < this.dnsServers.length; i++)
				dnsServersCopy[i]= this.dnsServers[i];

			currentCheckingDNServers = dnsServersCopy;
		}


		new Thread(new Runnable() {

			boolean fastestFound = false;
			boolean allReady = false;
			boolean go = false;
			final Object monitor = new Object();
			int cnt = 0;


			public void terminated(boolean first) {
				synchronized (monitor) {
					cnt++;
					if (cnt == dnsServersCopy.length)
						allReady = true;
					if (first || allReady) {
						fastestFound = true;
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

						public void writeDNSPerfInfo(String txt) {
							try {
								synchronized (monitor) {
									if (dnsPerfOut[0] != null)
										dnsPerfOut[0].write(txt.getBytes());
								}
							} catch (IOException eio) {
								Logger.getLogger().logLine("Can't write dnsperf.info file!\n"+eio);
							}
						}

						@Override
						public void run() {
							synchronized (monitor) {
								while (!go) {
									try {
										monitor.wait();
									} catch (InterruptedException e) {
										Thread.currentThread().interrupt();
										return;
									}
								}
							}
							try {
								long result = dnsServer.testDNS(5);

								synchronized (monitor) {
									writeDNSPerfInfo(dnsServer+": "+result+"ms\r\n");

									if (!fastestFound) {
										if (acceptCurrent || dnsIdx != curDNSCopy[0]) {
											curDNSCopy[0] = dnsIdx;
											terminated(true);
										} else {
											Logger.getLogger().logLine(dnsServer+" already set! Preferring different one!");
											terminated(false);
										}
									} else terminated(false);
								}
							} catch (IOException eio) {
								writeDNSPerfInfo(dnsServer+"; "+eio.toString()+"\r\n");
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

					while (!fastestFound) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}

					// Take over result in case no other DNS Serverlist change was in between
					synchronized (DNSCommunicator.this) {

						if (Utils.arrayEqual(dnsServersCopy, dnsServers)) {

							currentCheckingDNServers = null;

							curDNS = curDNSCopy[0];

							if (curDNS >= 0 && curDNS < dnsServersCopy.length) {
								Logger.getLogger().logLine("Selected DNS: (" + dnsServersCopy[curDNS].lastPerformance + "ms) " + dnsServersCopy[curDNS]);
								lastDNS = dnsServers[curDNS].toString();
							}
						}
					}

					// Wait for all Threads to finish
					while (!allReady) {
						try {
							monitor.wait();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}

					//close dnsperf.info file

					if (dnsPerfOut[0] != null) {
						try {
							dnsPerfOut[0].write(("\r\n#Terminated: " + new Date() + "\r\n\r\n").getBytes());
							dnsPerfOut[0].flush();
						} catch (IOException eio) {
							Logger.getLogger().logLine("Can't write dnsperf.info trailer!\n"+eio);
						} finally {
							try {
								dnsPerfOut[0].close();
							} catch (IOException eio) {
								Logger.getLogger().logLine("Can't close dnsperf.info file!\n"+eio);
							}
						}
						try {
							boolean dumpDNSPerf = Boolean.parseBoolean(ConfigurationAccess.getLocal().getConfig().getProperty("dumpDNSPerfInfo", "false"));
							if (dumpDNSPerf && dnsPerfFile.exists()) {
								FileInputStream in = new FileInputStream(dnsPerfFile);
								try {
									Logger.getLogger().logLine(new String(Utils.readFully(in, 1024)));
								} finally {
									in.close();
								}
							}
						} catch (Exception eio){
							Logger.getLogger().logException(eio);
						}
					}
				}
			}
		}).start();
	}

	private synchronized void switchDNSServer(DNSServer current) throws IOException {
		if (current == getCurrentDNS()) {  //might have been switched by other thread already
			setFastestDNSFromServers(false);
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine("Switched DNS server to:" + getCurrentDNS().getAddress().getHostAddress());
		}
	}

	public synchronized DNSServer getCurrentDNS() throws IOException {
		if (dnsServers.length == 0 || curDNS < 0 || curDNS >= dnsServers.length)
			throw new IOException("No DNS server initialized!");
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
