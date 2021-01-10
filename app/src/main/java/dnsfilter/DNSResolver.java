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

import ip.IPPacket;
import ip.UDPPacket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Hashtable;

import util.ExecutionEnvironment;
import util.Logger;

public class DNSResolver implements Runnable {

	private static int THR_COUNT = 0;
	private static Object CNT_SYNC = new Object();
	private static boolean IO_ERROR=false;


	//for android usage based on IP packages from the VPN Interface
	private UDPPacket udpRequestPacket;
	private OutputStream responseOut;

	//for non android usage
	private DatagramPacket dataGramRequest;
	private DatagramSocket replySocket;

	private boolean datagramPacketMode = false;


	private static boolean enableLocalResolver = false;
	private static int localResolverTTL = 0;
	private static Hashtable <String, byte[]> customIPMappings = null;

	public static void initLocalResolver(Hashtable<String, byte[]> customMappings, boolean enabled, int ttl){
		customIPMappings = customMappings;
		localResolverTTL= ttl;
		enableLocalResolver = enabled;
	}

	public DNSResolver(UDPPacket udpRequestPacket, OutputStream reponseOut) {

		this.udpRequestPacket = udpRequestPacket;
		this.responseOut = reponseOut;
	}

	//for non Android usage based on DatagramPacket
	public DNSResolver(DatagramPacket request, DatagramSocket replySocket) {
		datagramPacketMode = true;
		this.dataGramRequest = request;
		this.replySocket = replySocket;
	}



	public boolean resolveLocal(String client, DatagramPacket request, DatagramPacket response) throws IOException {

		if (!enableLocalResolver)
			return false;

		SimpleDNSMessage dnsQuery = null;
		try {
			dnsQuery = new SimpleDNSMessage(request.getData(), request.getOffset(), request.getLength());
		} catch (Exception e){
			File dump = new File(ExecutionEnvironment.getEnvironment().getWorkDir()+"dnsdump_"+System.currentTimeMillis());
			FileOutputStream dumpout = new FileOutputStream(dump);
			dumpout.write(request.getData(), request.getOffset(), request.getLength());
			dumpout.flush();
			dumpout.close();
			Logger.getLogger().logException(e);
			throw new IOException(e);
		}

		if (!dnsQuery.isStandardQuery())
			return false;

		Object[] info = dnsQuery.getQueryData();

		short type = (short) info[1];
		short clss = (short) info[2];

		if (type != 1 && type != 28)
			return false;

		String host = (String) info[0];
		byte[] ip = null;
		String prfx = ">4";
		byte[] filterIP = DNSResponsePatcher.ipv4_blocked;
		if (type == 28) {
			prfx = ">6";
			filterIP = DNSResponsePatcher.ipv6_blocked;
		}

		if (customIPMappings != null)
			ip = customIPMappings.get(prfx+host.toLowerCase());
		if (ip == null && DNSResponsePatcher.filter(host, false)) {
			DNSResponsePatcher.logNstats(true, host);
			ip = filterIP;
		}
		if (ip != null) {

			DNSResponsePatcher.trafficLog(client,clss,type,host,null,0);
			int length = dnsQuery.produceResponse(response.getData(), response.getOffset(), ip, localResolverTTL);
			response.setLength(length);

			String addrStr = InetAddress.getByAddress(ip).getHostAddress().toString();

			DNSResponsePatcher.trafficLog(client,clss,type,host, addrStr, ip.length);

			if (ip != filterIP)
				Logger.getLogger().logLine("MAPPED_CUSTOM_IP: "+host+"->"+addrStr);
			
			return true;
		} else
			return false;
	}


	private void processIPPackageMode() throws Exception {
		int ttl = udpRequestPacket.getTTL();
		int[] sourceIP = udpRequestPacket.getSourceIP();
		int[] destIP = udpRequestPacket.getDestIP();
		int sourcePort = udpRequestPacket.getSourcePort();
		int destPort = udpRequestPacket.getDestPort();
		int version = udpRequestPacket.getVersion();
		String clientID = IPPacket.int2ip(sourceIP).getHostAddress() + ":" + sourcePort;

		int hdrLen = udpRequestPacket.getHeaderLength();
		byte[] packetData = udpRequestPacket.getData();
		int ipOffs = udpRequestPacket.getIPPacketOffset();
		int offs = ipOffs + hdrLen;
		int len = udpRequestPacket.getIPPacketLength() - hdrLen;

		// build request datagram packet from UDP request packet
		DatagramPacket request = new DatagramPacket(packetData, offs, len);

		// we can reuse the request data array
		DatagramPacket response = new DatagramPacket(packetData, offs, packetData.length - offs);

		//forward request to DNS and receive response
		if (!resolveLocal(clientID, request, response)) {
			
			DNSCommunicator.getInstance().requestDNS(request, response);

			// patch the response by applying filter
			byte[] buf = DNSResponsePatcher.patchResponse(clientID, response.getData(), offs);
		}

		//create  UDP Header and update source and destination IP and port			
		UDPPacket udp = UDPPacket.createUDPPacket(response.getData(), ipOffs, hdrLen + response.getLength(), version);

		//for the response source and destination have to be switched
		udp.updateHeader(ttl, 17, destIP, sourceIP);
		udp.updateHeader(destPort, sourcePort);

		//finally return the response packet
		synchronized (responseOut) {
			responseOut.write(udp.getData(), udp.getIPPacketOffset(), udp.getIPPacketLength());
			responseOut.flush();
		}
	}

	private void processDatagramPackageMode() throws Exception {
		SocketAddress sourceAdr = dataGramRequest.getSocketAddress();
		String clientID = sourceAdr.toString();

		//we reuse the request data array
		byte[] data = dataGramRequest.getData();
		DatagramPacket response = new DatagramPacket(data, dataGramRequest.getOffset(), data.length - dataGramRequest.getOffset());

		if (!resolveLocal(clientID, dataGramRequest, response)) {
			//forward request to DNS and receive response
			DNSCommunicator.getInstance().requestDNS(dataGramRequest, response);

			// patch the response by applying filter
			DNSResponsePatcher.patchResponse(clientID, response.getData(), response.getOffset());
		}

		//finally return the response to the request source
		response.setSocketAddress(sourceAdr);
		replySocket.send(response);
	}

	@Override
	public void run() {
		try {
			synchronized (CNT_SYNC) {
				THR_COUNT++;
			}
			if (datagramPacketMode)
				processDatagramPackageMode();
			else
				processIPPackageMode();

			IO_ERROR=false;

		} catch (IOException e) {
			boolean hasNetwork = ExecutionEnvironment.getEnvironment().hasNetwork();
			if (!hasNetwork)
				Logger.getLogger().message("No network!");
			String msg = e.getMessage();
			if (e.getMessage()==null)
				msg = e.toString();
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine(msg);
			else if (!IO_ERROR && hasNetwork) {
				// a new IO Error while connected occured
				Logger.getLogger().logLine(msg+"\nIO Error occured! Check network or DNS config!");
				IO_ERROR= true; //prevent repeating error logs
			}
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		} finally {
			synchronized (CNT_SYNC) {
				THR_COUNT--;
			}
		}
	}

	public static int getResolverCount() {
		return THR_COUNT;
	}

}
