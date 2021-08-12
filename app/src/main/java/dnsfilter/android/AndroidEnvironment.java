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
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import util.ExecutionEnvironment;
import util.ExecutionEnvironmentInterface;
import util.Logger;
import util.Utils;

public class AndroidEnvironment implements ExecutionEnvironmentInterface {

    private static Context ctx = null;
    private static AndroidEnvironment INSTANCE = new AndroidEnvironment();
    private static String WORKDIR = null;
    private static Stack wakeLooks = new Stack();

    static {
        ExecutionEnvironment.setEnvironment(INSTANCE);
    }


    public static void initEnvironment(Context context) {
        ctx = context;
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            context.getExternalFilesDirs(null); //Seems on some devices this has to be called once before accessing Files...
            File dir = context.getExternalFilesDir(null);
            if (dir != null)
                WORKDIR = dir.getAbsolutePath() + "/PersonalDNSFilter";

            String backwardcompWorkdir = "/storage/emulated/0/Android/data/dnsfilter.android/files/PersonalDNSFilter";
            try {
                if (WORKDIR == null || !new File(WORKDIR).exists()) {
                    if (new File(backwardcompWorkdir).exists())
                        WORKDIR = backwardcompWorkdir;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else
            WORKDIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter";
    }

    private void waitForStorage() {
        File dir = null;

        if (android.os.Build.VERSION.SDK_INT >= 19) {

            for (int i = 0; i < 15; i++) {

                Logger.getLogger().log("WAITING FOR STORAGE!");

                dir = ctx.getExternalFilesDir(null);

                if (dir != null) {
                    WORKDIR = dir.getAbsolutePath() + "/PersonalDNSFilter";
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        boolean pathExists = new File("/storage/emulated/0/Android/data/dnsfilter.android/files/PersonalDNSFilter").exists();
        throw new IllegalStateException("Cannot get external storage!"+pathExists);
    }

    @Override
    public int getEnvironmentID() {
        return 1;
    }

    @Override
    public String getEnvironmentVersion() {
        return ""+android.os.Build.VERSION.SDK_INT;
    }

    @Override
    public void wakeLock(){
        WifiManager.WifiLock wifiLock = ((WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "personalHttpProxy");
        wifiLock.acquire();
        PowerManager.WakeLock wakeLock = ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalDNSfilter:wakelock");
        wakeLock.acquire();
        wakeLooks.push(new Object[]{wifiLock, wakeLock});
        Logger.getLogger().logLine("Acquired WIFI lock and partial wake lock!");
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
        if (WORKDIR == null)
            waitForStorage();
        return WORKDIR;
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

    @Override
    public boolean protectSocket(Object socket, int type) {
        return DNSFilterService.protectSocket(socket, type);
    }

    @Override
    public void migrateConfig() throws IOException {

        //TO BE DELETED ONCE ON TARGET 11! MIGRATION OF CONFIG DATA TO EXTERNAL USER FOLDER

        boolean storagePermission = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermission = false;
                //requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                //Logger.getLogger().logLine("Need storage permissions to start!");
            }
        }

        //TO BE DELETED ONCE ON TARGET 11! MIGRATION OF CONFIG DATA TO EXTERNAL USER FOLDER
        File F_WORKDIR = new File(WORKDIR);
        if (!F_WORKDIR.exists() && storagePermission) {
            File OLDPATH = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/PersonalDNSFilter");
            if (OLDPATH.exists() && !OLDPATH.equals(F_WORKDIR)) {
                try {
                    Utils.moveFileTree(OLDPATH, F_WORKDIR);
                    Logger.getLogger().logLine("MIGRATED old config location to app storage!");
                    Logger.getLogger().logLine("NEW FOLDER: "+F_WORKDIR);
                } catch (IOException eio) {
                    Logger.getLogger().logLine("Migration of old config location has failed!");
                    Logger.getLogger().logException(eio);
                }
            }
        }

    }

}
