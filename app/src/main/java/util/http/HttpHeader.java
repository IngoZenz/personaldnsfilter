/* 
PersonalHttpProxy 1.5
Copyright (C) 2013-2015 Ingo Zenz

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

Find the latest version at http://www.zenz-solutions.de/personalhttpproxy
Contact:i.z@gmx.net 
 */

package util.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import util.Utils;

public class HttpHeader {

	private static final String[] reqparamSequence = { "Cache-Control", "Connection", "Date", "Pragma", "Trailer", "Transfer-Encoding", "Upgrade", "Via", "Warning", "Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language", "Authorization",
			"Expect", "From", "Host", "If-Match", "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Max-Forwards", "Proxy-Authorization", "Range", "Referer", "TE", "User-Agent", "Allow", "Content-Encoding",
			"Content-Language", "Content-Length", "Content-Location", "Content-MD5", "Content-Range", "Content-Type", "Expires", "Last-Modified", "extension-header" };

	public static final int REQUEST_HEADER = 1;
	public static final int RESPONSE_HEADER = 2;
	
	public final static int HTTP=1;
	public final static int HTTPS=2;
	public final static int OTHER=3;
	public final static int UNKNOWN=4;

	// Http Request Params
	public String remote_host_name = "";
	public String hostEntry;
	public String url;
	public String method;
	public int protocoll=UNKNOWN;
	public int remote_port = 0;

	public boolean tunnelMode = false;
	public int responsecode = -1;

	// parsed HTTP Header
	private String _first;
	private Vector _keys = null;
	private HashMap _mapping = null;

	private int type;

	public HttpHeader(InputStream in, int type) throws IOException {		

		_keys = new Vector();
		_mapping = new HashMap();

		if (type != REQUEST_HEADER && type != RESPONSE_HEADER)
			throw new IOException("INVALID TYPE!");

		this.type = type;

		_first = Utils.readLineFromStream(in,true);
		if (type == REQUEST_HEADER) {
			parseURI();
			if (hostEntry != null)
				setValue("Host", hostEntry);
		} else {
			if (!(_first.length() >= 12))
				throw new IOException("Invalid Response Header:" + _first);

			String _first12 = _first.substring(0, 12).toLowerCase();
			if (_first12.startsWith("http/")) {
				try {
					responsecode = Integer.parseInt(_first.substring(9, 12));
					_first = "HTTP/1.1 " + responsecode + _first.substring(12);
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
			} else
				throw new IOException("Invalid Response Header:" + _first);
		}

		String next = Utils.readLineFromStream(in,true);
		String key;
		String value;
		while (!next.equals("")) {
			int index = next.indexOf(": ");
			if (index == -1) {
				index = next.indexOf(":"); // fallback ": without blank
				if (index == -1)
					throw new IOException("Invalid header:" + next);
				else {
					key = next.substring(0, index).trim();
					value = next.substring(index + 1).trim();
				}
			} else {
				key = next.substring(0, index).trim();
				value = next.substring(index + 2).trim();
			}

			String keyUpper = key.toUpperCase();

			String curVal = (String) _mapping.get(keyUpper);
			if (curVal == null) {
				_keys.add(key);
				_mapping.put(keyUpper, value);
			} else {
				if (!keyUpper.equals("CONTENT-LENGTH")) {
					if (!keyUpper.equals("HOST"))
						_mapping.put(keyUpper, curVal + "_,_" + value); // multiple values per key separated by "_,_" as "," only doesn't work for Set-Cookie expires setting
				} else if (!curVal.equals(value))
					throw new IOException("Invalid Header! Duplicated Content-Length with different values:" + curVal + "<>" + value + "!");
			}
			next = Utils.readLineFromStream(in,true);
		}
		if (hostEntry == null && type == REQUEST_HEADER) {
			hostEntry = getValue("Host");
			if (hostEntry != null) {
				parseHostEntry();
			} else
				throw new IOException("Bad Request - No Host specified!");
		}
	}

	public HttpHeader(int type) {
		_keys = new Vector();
		_mapping = new HashMap();
		this.type = type;
	}

	public HttpHeader(String headerStr, int type) throws IOException {
		this(new ByteArrayInputStream(headerStr.getBytes()), type);
	}

	private HttpHeader() {
		// TODO Auto-generated constructor stub
	}

	public HttpHeader clone() {

		HttpHeader clone = new HttpHeader();

		clone.remote_host_name = remote_host_name;
		clone.hostEntry = hostEntry;
		clone.url = url;
		clone.method = method;
		clone.remote_port = remote_port;

		clone.tunnelMode = tunnelMode;
		clone.responsecode = responsecode;

		clone._first = _first;
		clone._keys = (Vector) _keys.clone();
		clone._mapping = (HashMap) _mapping.clone();
		clone.type = type;

		return clone;

	}

	public void setRequest(String request) throws IOException {
		_first = request;
		parseURI();
		setValue("Host", hostEntry);
	}

	public String getResponseMessage() {
		if (type != RESPONSE_HEADER)
			throw new IllegalStateException(this + " is not a ResonseHeader!");
		return _first;
	}

	public int getResponseCode() {
		if (type != RESPONSE_HEADER)
			throw new IllegalStateException(this + " is not a ResonseHeader!");
		return responsecode;
	}

	public String getValue(String key) {
		return (String) _mapping.get(key.toUpperCase());
	}
	
	public String removeValue(String key) {
		_keys.remove(key);
		return (String) _mapping.remove(key.toUpperCase());		
	}

	public void appendValueToHeaderString(StringBuffer headerString, String key, String value) {
		String[] tokens = value.split("_,_"); // multiple values per key!
		for (int i = 0; i < tokens.length; i++)
			headerString.append(key + ": " + tokens[i] + "\r\n");
	}

	public String getHeaderString() {

		StringBuffer headerString = new StringBuffer(_first + "\r\n");
		Iterator keyIt = _keys.iterator();
		while (keyIt.hasNext()) {
			String key = (String) keyIt.next();
			String value = (String) _mapping.get(key.toUpperCase());
			appendValueToHeaderString(headerString, key, value);
		}
		headerString.append("\r\n");
		return headerString.toString();
	}

	public String getServerRequestHeader(boolean useProxy) {

		String firstLn = _first;

		if (!tunnelMode) {
			if (useProxy)
				firstLn = method + " http://" + hostEntry + url + " HTTP/1.1";
			else
				firstLn = method + " " + url + " HTTP/1.1";
		}
		StringBuffer headerString = new StringBuffer(firstLn + "\r\n");
		HashMap mapping = (HashMap) _mapping.clone();

		for (int i = 0; i < reqparamSequence.length; i++) {
			String key = reqparamSequence[i];
			String value = (String) mapping.remove(key.toUpperCase());
			if (value != null) {
				if (useProxy && key.toUpperCase().equals("CONNECTION"))
					key = "Proxy-Connection";
				if (value.length() > 0) {
					appendValueToHeaderString(headerString, key, value);
				} else
					headerString.append(key + ":\r\n");
			}
		}
		Iterator keyIt = _keys.iterator();
		while (keyIt.hasNext()) {
			String key = (String) keyIt.next();
			String value = (String) mapping.remove(key.toUpperCase());
			if (value != null) {
				if (!useProxy && key.toUpperCase().equals("PROXY-CONNECTION"))
					key = "Connection";
				if (value.length() > 0) {
					appendValueToHeaderString(headerString, key, value);
					;
				} else
					headerString.append(key + ":\r\n");
			}
		}
		headerString.append("\r\n");
		return headerString.toString();
	}

	public String getServerRequestHeader() {
		return getServerRequestHeader(false);
	}

	public long getContentLength() {

		if (responsecode == 304 || responsecode == 204)
			return 0;

		String val = getValue("Content-Length");
		if (val != null) {
			return Long.parseLong(val);
		}

		else
			return -1;
	}

	public boolean getConnectionClose() {
		String value = getValue("Connection");
		if (value == null)
			return false;
		else if (value.equalsIgnoreCase("close"))
			return true;
		else
			return false;

	}

	public boolean chunkedTransfer() {
		Object val = getValue("Transfer-Encoding");
		if (val != null)
			return ((String) val).equalsIgnoreCase("chunked");
		else
			return false;
	}

	public void setValue(String key, String value) {
		if (getValue(key) == null) {
			_keys.add(key);
		}
		_mapping.put(key.toUpperCase(), value);
	}
	
	public void setHostEntry(String hostEntry) throws IOException {
		this.hostEntry= hostEntry;
		setValue("Host",hostEntry);	
		parseHostEntry();
	}

	private void parseURI() throws IOException {

		// format: <method> <protocol>//<hostentry>/<url> HTTP<version>
		int idx = _first.indexOf(' ');
		int idx2 = _first.lastIndexOf(' ');
		if (idx == -1 || idx == idx2)
			throw new IOException("Bad Request:" + _first);

		method = _first.substring(0, idx);
		url = _first.substring(idx + 1, idx2);
		tunnelMode = (method.equalsIgnoreCase("CONNECT"));
		if (!tunnelMode) {
			idx = url.indexOf("://");
			if (idx != -1) {				
				String prot = url.substring(0,idx).toLowerCase();
				if (prot.equals("http"))
					protocoll =  HTTP;
				else if (prot.equals("https"))
					protocoll =  HTTPS;
				else 
					protocoll =  OTHER;
				url = url.substring(idx+3);
				idx = url.indexOf('/');
				if (idx == -1)
					idx = url.length();
				hostEntry = url.substring(0, idx);
				if (idx == url.length())
					url = "/";
				else
					url = url.substring(idx);
			}
			
		} else
			hostEntry = url;

		if (hostEntry != null)
			parseHostEntry();
	}

	private void parseHostEntry() throws IOException {
		if (protocoll == HTTP)
			remote_port = 80;
		else if (protocoll == HTTPS)
			remote_port = 443; 
		else 
			remote_port = -1;
		
		remote_host_name = hostEntry;

		int idx = hostEntry.lastIndexOf(":"); // check for the port number
		// take IPV6 into account!!! http://[2a00:1450:400a:804::1010]:80 HTTP/1.1

		if (idx != -1 && !hostEntry.endsWith("]")) {
			try {
				remote_port = Integer.parseInt(hostEntry.substring(idx + 1));
			} catch (NumberFormatException nfe) {
				throw new IOException("Bad Request - Cannot parse port to int:" + _first);
			}
			remote_host_name = hostEntry.substring(0, idx);
		}	

		// IPV6 e.g. [2a00:1450:400a:804::1010]
		if (remote_host_name.startsWith("[") && remote_host_name.endsWith("]")) {
			remote_host_name = remote_host_name.substring(1, remote_host_name.length() - 1);
		}

	}
}
