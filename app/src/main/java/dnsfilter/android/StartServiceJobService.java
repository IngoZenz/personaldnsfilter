package dnsfilter.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.StrictMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import util.ExecutionEnvironment;
import util.Logger;

public class StartServiceJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {

        if (DNSFilterService.SERVICE != null){
            Logger.getLogger().logLine("Service is already running! Exit job execution!");
            jobFinished(params, false);
            return false;
        }

        Context context = getApplicationContext();

        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().build());
            Intent serviceIntent = new Intent(context, DNSFilterService.class);
            context.startForegroundService(serviceIntent);
        } catch (Exception e) {
            // Retry once if system still blocks FGS start
            jobFinished(params, true);
            return false;
        }

        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // If the job is stopped prematurely, request a retry
        return true;
    }
}
