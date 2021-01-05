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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dnsfilter.ConfigurationAccess;
import util.Logger;

public class NotificationReceiver extends BroadcastReceiver {


	private static NotificationReceiver instance = new NotificationReceiver();

	public static NotificationReceiver getInstance() {
		return instance;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		try {
			String passwd = ConfigurationAccess.getLocal().getConfig().getProperty("passcode","").trim();
			if (!passwd.equals("")) {
				Logger.getLogger().logLine("Notification action not allowed when passcode protected!");
				Logger.getLogger().message("Not permitted - Passcode protected!");
				return;
			}
			DNSFilterService instance = DNSFilterService.INSTANCE;
			if (instance != null)
				DNSFilterService.INSTANCE.pause_resume();
		} catch (Exception e) {
			Logger.getLogger().logException(e);
		}
	}
}

