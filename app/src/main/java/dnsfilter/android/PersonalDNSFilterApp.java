package dnsfilter.android;

import android.app.Application;

import dnsfilter.android.AndroidEnvironment;

public class PersonalDNSFilterApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidEnvironment.initEnvironment(this);
    }
}
