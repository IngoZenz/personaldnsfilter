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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import util.Logger;
import util.LoggerInterface;

public class DNSResponsePatcher {

	private static final int MAX_POINTER_JUMPS = 64;
	private static final int MAX_DOMAIN_LENGTH = 255;

	private static volatile Set FILTER = null;
	private static volatile LoggerInterface TRAFFIC_LOG = null;

	protected static byte[] ipv4_blocked;
	protected static byte[] ipv6_blocked;

	private static final AtomicLong okCnt = new AtomicLong(0);
	private static final AtomicLong filterCnt = new AtomicLong(0);
	private static volatile boolean checkIP = false;
	private static volatile boolean checkCNAME = true;


	static {
		loadBlockedAddresses();
	}

	private static void loadBlockedAddresses() {
		try {
			ipv4_blocked = InetAddress.getByName(ConfigurationAccess.getLocal().getConfig().getProperty("ipV4BlockedHost","127.0.0.1")).getAddress();
			ipv6_blocked = InetAddress.getByName(ConfigurationAccess.getLocal().getConfig().getProperty("ipV6BlockedHost","::1")).getAddress();
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}

	public static void init(Set filter, LoggerInterface trafficLogger) {
		FILTER = filter;
		TRAFFIC_LOG = trafficLogger;
		loadBlockedAddresses();
		try {
			checkIP = Boolean.parseBoolean(ConfigurationAccess.getLocal().getConfig().getProperty("checkResolvedIP","false"));
			checkCNAME = Boolean.parseBoolean(ConfigurationAccess.getLocal().getConfig().getProperty("checkCNAME","false"));
		} catch (IOException e) {
			Logger.getLogger().logException(e);
		}
	}

	public static long getFilterCount() {
		return filterCnt.get();
	}

	public static long getOkCount() {
		return okCnt.get();
	}


	public static byte[] patchResponse(String client, byte[] response, int offs) throws IOException {

		try {
			ByteBuffer buf = ByteBuffer.wrap(response, offs, response.length - offs);
			if (buf.remaining() < 12)
				throw new IOException("Truncated DNS message header!");

			String queryHost = "";

			buf.getShort(); // ID
			buf.getShort(); // Flags
			int questCount = buf.getShort() & 0xFFFF;
			int answerCount = buf.getShort() & 0xFFFF;
			buf.getShort(); // auths
			buf.getShort(); // additional

			boolean filter = false;

			for (int i = 0; i < questCount; i++) {

				queryHost = readDomainName(buf, offs);
				if (buf.remaining() < 4)
					throw new IOException("Truncated DNS question!");
				short type = buf.getShort(); // query type

				if (type == 1 || type == 28)
					filter = filter || filter(queryHost, true);

				short clss = buf.getShort(); // query class

				trafficLog(client, clss, type, queryHost,null,0);
			}

			for (int i = 0; i < answerCount; i++) {
				String host = readDomainName(buf, offs);
				if (buf.remaining() < 10)
					throw new IOException("Truncated DNS answer RR header!");
				short type = buf.getShort(); // type
				short clss = buf.getShort(); // class
				buf.getInt(); // TTL
				int len = buf.getShort() & 0xFFFF; // len
				if (len > buf.remaining())
					throw new IOException("DNS answer RDLENGTH out of bounds: " + len);

				boolean filtered = false;

				if (type == 5 && !filter && checkCNAME) {
					int rdataPos = buf.position();
					String cnameTarget = readDomainName(buf, offs);
					if (!cnameTarget.isEmpty() && !cnameTarget.equalsIgnoreCase(queryHost)) {
						filter = filter || filter(cnameTarget, true);
						queryHost = cnameTarget;
					}
					buf.position(rdataPos);
				}

				if (type == 1 || type == 28) {
					if (!filter && checkCNAME && !host.equalsIgnoreCase(queryHost)) { //avoid duplicate checking same hosts
						filter = filter || filter(host, true);  //Handle CNAME Cloaking!
						queryHost = host;
					}
					if (filter) {
						if (type == 1 && len == 4 && ipv4_blocked != null && ipv4_blocked.length == 4) {
							filtered = true;
							buf.put(ipv4_blocked);
						} else if (type == 28 && len == 16 && ipv6_blocked != null && ipv6_blocked.length == 16) {
							filtered = true;
							buf.put(ipv6_blocked);
						}
					} else if (checkIP && ((type == 1 && len == 4) || (type == 28 && len == 16))) { //check if resolved IP is filtered
						byte[] answer = new byte[len];
						buf.get(answer);
						buf.position(buf.position() - len);
						String ip = InetAddress.getByAddress(answer).getHostAddress();
						if (filterIP(ip)) {
							if (type == 1 && ipv4_blocked != null && ipv4_blocked.length == 4) {
								filtered = true;
								buf.put(ipv4_blocked);
							} else if (type == 28 && ipv6_blocked != null && ipv6_blocked.length == 16) {
								filtered = true;
								buf.put(ipv6_blocked);
							}
						}
					}
				}
				if (!filtered)
					buf.position(buf.position() + len); // go ahead

				//log answer
				if (TRAFFIC_LOG != null) {
					byte[] answer = new byte[len];
					String answerStr = null;
					buf.position(buf.position() - len);

					if (type == 5) {
						int posAfterRdata = buf.position() + len;
						answerStr = readDomainName(buf, offs);
						buf.position(posAfterRdata);
					} else {
						buf.get(answer);

						if ((type == 1 && len == 4) || (type == 28 && len == 16))
							answerStr = InetAddress.getByAddress(answer).getHostAddress();
						else
							answerStr = getReadableStringFromBinary(answer,0,answer.length);
					}
					trafficLog(client, clss, type, host, answerStr, len);
				}
			}
			return buf.array();
		} catch (IOException eio) {
			throw eio;
		} catch (Exception e){
			throw new IOException ("Invalid DNS response message structure", e);
		}
	}

	protected static boolean filter(String host, boolean log) {
		boolean result;
		Set currentFilter = FILTER;

		if (currentFilter == null)
			result = false;
		else
			result = currentFilter.contains(host);

		if (log)
			logNstats(result, host);

		return result;
	}


	protected static void trafficLog(String client, short clss, short type, String host, String answer, int length) {
		LoggerInterface logger = TRAFFIC_LOG;
		if (logger == null)
			return;
		if (answer != null)
			logger.logLine(client + ", "+ clss + ", A-" + type + ", " + host + ", " + answer + ", /Length:" + length);
		else
			logger.logLine(client + ", "+ clss + ", Q-" + type + ", " + host + ", " + "<empty>");

	}

	protected static void logNstats(boolean result, String host) {
		if (result == true)
			Logger.getLogger().logLine("FILTERED:" + host);
		else
			Logger.getLogger().logLine("ALLOWED:" + host);

		if (result == false)
			okCnt.incrementAndGet();
		else
			filterCnt.incrementAndGet();
	}


	private static boolean filterIP(String ip) {
		boolean result;
		Set currentFilter = FILTER;

		if (currentFilter == null)
			result = false;
		else
			result = currentFilter.contains("%IP%"+ip);

		if (result)
			Logger.getLogger().logLine("FILTERED:" + ip);

		if (!result)
			okCnt.incrementAndGet();
		else
			filterCnt.incrementAndGet();

		return result;
	}


	protected static String readDomainName(ByteBuffer buf, int offs) throws IOException {

		byte[] substr = new byte[64];

		int count = -1;
		String dot = "";
		StringBuilder result = new StringBuilder();
		int ptrJumpPos = -1;
		int jumps = 0;

		while (count != 0) {
			if (!buf.hasRemaining())
				throw new IOException("Truncated DNS domain name!");
			count = buf.get() & 0xFF;
			if (count != 0) {
				int tag = count & 0xC0;
				if (tag == 0) {
					if (count > buf.remaining())
						throw new IOException("Truncated DNS domain label of length " + count);
					buf.get(substr, 0, count);
					if (result.length() + dot.length() + count > MAX_DOMAIN_LENGTH)
						throw new IOException("DNS domain name exceeds maximum length!");
					result.append(dot).append(new String(substr, 0, count));
					dot = ".";
				} else if (tag == 0xC0) { // pointer
					if (!buf.hasRemaining())
						throw new IOException("Truncated DNS compression pointer!");
					buf.position(buf.position() - 1);
					int pointer = offs + (buf.getShort() & 0x3fff);
					if (pointer < offs || pointer >= buf.limit())
						throw new IOException("DNS compression pointer out of bounds: " + pointer);
					if (++jumps > MAX_POINTER_JUMPS)
						throw new IOException("DNS compression pointer loop detected!");
					if (ptrJumpPos == -1)
						ptrJumpPos = buf.position();
					buf.position(pointer);
				} else {
					throw new IOException("Invalid DNS label type flags: 0x" + Integer.toHexString(count));
				}
			} else {
				if (ptrJumpPos != -1)
					buf.position(ptrJumpPos);
			}
		}
		return result.toString();
	}

	public static String getReadableStringFromBinary(byte[] b, int offs, int r) {
		StringBuilder result = new StringBuilder();
		int end = Math.min(b.length, offs + r);
		for (int i = offs; i < end; i++) {
			if (!((b[i] < 64 && b[i] > 32) || (b[i] < 91 && b[i] > 64) || (b[i] < 123 && b[i] > 96)))  {
				result.append((char) 46);
			}
			else if (b[i] != 44 && b[i] != 59)
				result.append((char) b[i]); //no comma or semicolon as it might serve as seperator
			else
				result.append((char) 46);
		}
		return result.toString();
	}
}
