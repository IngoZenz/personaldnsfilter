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

import util.Logger;

public class DNSCommunicator {
	
	private static DNSCommunicator INSTANCE = new DNSCommunicator();
	
	private static int TIMEOUT=12000;
	InetAddress[] dnsServers=new InetAddress[0];
	int curDNS=-1;
	String lastDNS="";	
	
	
	public static DNSCommunicator getInstance() {
		return INSTANCE;
	}
	
	public synchronized void setDNSServers(InetAddress[] newDNSServers) {
		if (hasChanged(newDNSServers, dnsServers)) {			
			dnsServers = newDNSServers;
			if (dnsServers.length >0) {
				lastDNS=dnsServers[0].getHostAddress();
				curDNS=0;
			} else {
				lastDNS="";
				curDNS=-1;
			}
			Logger.getLogger().logLine("Using updated DNS Servers!");
		}
	}
	
	private boolean hasChanged(InetAddress[] newDNS, InetAddress[]curDNS) {
		
		if (newDNS.length != curDNS.length)
			return true;
		
		for (int i = 0; i < newDNS.length; i++)
			if (!newDNS[i].equals(curDNS[i]))
				return true;
		
		return false;		
	}

	private synchronized void switchDNSServer(InetAddress current) throws IOException {	
		if (current == getCurrentDNS()) {  //might have been switched by other thread already
			curDNS = (curDNS+1) % dnsServers.length;			
			Logger.getLogger().logLine("Switched DNS Server to:"+getCurrentDNS().getHostAddress() );
		}
	}
		
	public synchronized InetAddress getCurrentDNS() throws IOException {
		if (dnsServers.length ==0)
			throw new IOException("No DNS Server initialized!");
		else {
			lastDNS=dnsServers[curDNS].getHostAddress();
			return dnsServers[curDNS];
		}
	}
	
	public String getLastDNSAddress() {
		return lastDNS;
	}	
	
	public void requestDNS (DatagramSocket socket,  DatagramPacket request, DatagramPacket response) throws IOException {
		InetAddress dns = getCurrentDNS();
		request.setAddress(dns);
		request.setPort(53);
		socket.setSoTimeout(TIMEOUT);
		try {
			socket.send(request);
		} catch (IOException eio){
			switchDNSServer(dns);
			throw new IOException("Cannot reach "+dns.getHostAddress()+"!"+eio.getMessage());			
		}
		try {
			socket.receive(response);			
		} catch (IOException eio){			
			switchDNSServer(dns);		
			throw new IOException ("No DNS Response from "+dns.getHostAddress());
		}
		
	}
	
	

}
