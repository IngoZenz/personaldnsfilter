/*
PersonalHttpProxy 1.5
PersonalDNSfilter 1.5
Copyright (C) 2013-2019 Ingo Zenz

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
package util.conpool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;

import util.http.HttpHeader;

public class HttpProxy extends Proxy {
	private String authString;
	private InetSocketAddress proxyAdr;	

	public HttpProxy(InetSocketAddress adr, String authString) {
		super(Proxy.Type.HTTP, adr);
		this.proxyAdr = adr;
		this.authString= authString;		
	}
	
	public HttpProxy(InetSocketAddress adr) {
		this(adr,null);
	}
	
	public void setProxyAuth(String authString){
		this.authString= authString;	
	}

	public Socket openTunnel(InetSocketAddress adr,  int conTimeout) throws IOException {
		
		String host;
		
		if (!adr.getAddress().getHostAddress().equals("0.0.0.0")) 
			host = adr.getAddress().getHostAddress(); //IP is already resolved
		else
			host = adr.getHostName();	//IP will be resolved by Proxy	
			
		HttpHeader header = new HttpHeader(HttpHeader.REQUEST_HEADER);	
		header.setRequest("CONNECT "+host+":"+adr.getPort()+" HTTP/1.1");
		if (authString != null)
			header.setValue("Proxy-Authorization", authString);
		
		String request = header.getServerRequestHeader();		
		
		//get address with name of final host but IP of the proxy for SSL hostname check consistency		
		InetSocketAddress conAdr = new InetSocketAddress(InetAddress.getByAddress(adr.getHostName(), proxyAdr.getAddress().getAddress()), proxyAdr.getPort());
		
		Socket proxyCon = new Socket();
		proxyCon.connect(conAdr, conTimeout);
		proxyCon.setSoTimeout(conTimeout);
		proxyCon.getOutputStream().write(request.getBytes());
		proxyCon.getOutputStream().flush();
		
		header = new HttpHeader (proxyCon.getInputStream(), HttpHeader.RESPONSE_HEADER);
		if (header.responsecode != 200){
			proxyCon.shutdownInput();
			proxyCon.shutdownOutput();
			proxyCon.close();
			throw new IOException ("Proxy refused Tunnel\n"+header.getResponseMessage());
		}
		proxyCon.setSoTimeout(0); 
		return proxyCon;		
	}

}
