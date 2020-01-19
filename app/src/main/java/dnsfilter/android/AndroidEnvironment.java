/*
 PersonalDNSFilter 1.5
 Copyright (C) 2017-2020 Ingo Zenz

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

import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import util.ExecutionEnvironment;
import util.ExecutionEnvironmentInterface;
import util.Logger;

public class AndroidEnvironment implements ExecutionEnvironmentInterface {

    private static Context ctx = null;
    private static AndroidEnvironment INSTANCE = new AndroidEnvironment();
    private static Stack wakeLooks = new Stack();

    static {
        ExecutionEnvironment.setEnvironment(INSTANCE);
    }


    public static void initEnvironment(Context context) {
        ctx = context;
    }

    @Override
    public void wakeLock(){
        WifiManager.WifiLock wifiLock = ((WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "personalHttpProxy");
        wifiLock.acquire();
        PowerManager.WakeLock wakeLock = ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalHttpProxy");
        wakeLock.acquire();
        wakeLooks.push(new Object[]{wifiLock, wakeLock});
        Logger.getLogger().logLine("Aquired WIFI lock and partial wake lock!");
    }

    @Override
    public void releaseWakeLock() {
        Object[] locks;
        try {
            locks = (Object[]) wakeLooks.pop();
        } catch (Exception e) {
            Logger.getLogger().logException(e);
            return;
        }
        WifiManager.WifiLock wifiLock = (WifiManager.WifiLock) locks[0];
        PowerManager.WakeLock wakeLock = (PowerManager.WakeLock) locks[1];
        wifiLock.release();
        wakeLock.release();
        Logger.getLogger().logLine("Released WIFI lock and partial wake lock!");
    }

    @Override
    public void releaseAllWakeLocks() {
        Object[] locks;
        while (!wakeLooks.isEmpty()) {
            try {
                locks = (Object[]) wakeLooks.pop();
            } catch (Exception e) {
                Logger.getLogger().logException(e);
                return;
            }
            WifiManager.WifiLock wifiLock = (WifiManager.WifiLock) locks[0];
            PowerManager.WakeLock wakeLock = (PowerManager.WakeLock) locks[1];
            wifiLock.release();
            wakeLock.release();
            Logger.getLogger().logLine("Released WIFI lock and partial wake lock!");
        }
    }

    @Override
    public String getWorkDir() {
        return DNSProxyActivity.WORKPATH+"/";
    }

    @Override
    public boolean debug() {
        return DNSProxyActivity.debug;
    }

    @Override
    public void onReload() throws IOException {
        DNSFilterService.onReload();
    }

    @Override
    public InputStream getAsset(String path) throws IOException {
        AssetManager assetManager = ctx.getAssets();
        return(assetManager.open(path));
    }

    @Override
    public boolean hasNetwork() {
        ConnectivityManager conMan= (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = conMan.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

}
