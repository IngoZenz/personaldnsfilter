package dnsfilter.android;

import android.app.Application;

public class PersonalDNSFilterApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidEnvironment.initEnvironment(this);
    }
}
