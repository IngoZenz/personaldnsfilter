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

import util.ExecutionEnvironment;
import util.Logger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectionChangeReceiver extends BroadcastReceiver implements Runnable {

	private static ConnectionChangeReceiver instance = new ConnectionChangeReceiver();

	public static ConnectionChangeReceiver getInstance() {
		return instance;
	}

	@Override
	public synchronized void onReceive(Context context, Intent intent) {

		try {
			if (ExecutionEnvironment.getEnvironment().debug())
				Logger.getLogger().logLine("Received Network Connection Event: " + intent.getAction());
			DNSFilterService.possibleNetworkChange();

			//some devices send only 1 network change event when the connection is closed but not when the network is back!
			//therefore we check again for the new DNS after some seconds (in own thread) and hope connection is available then! 
			new Thread(this).start();
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}

	@Override
	public void run() {
		try {
			Thread.sleep(10000);
			DNSFilterService.possibleNetworkChange();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			Logger.getLogger().logException(e);
		}

	}


}
