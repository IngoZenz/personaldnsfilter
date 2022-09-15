package dnsfilter.android.widget;


import android.content.Context;
import android.widget.EditText;

import java.util.Map;

import dnsfilter.android.R;

public class DNSConfigEntryValidator {

    public DNSServerConfigEntryValidationResult validate(DNSServerConfigEntry dnsEntry, Context context) {
        DNSServerConfigEntryValidationResult result = new DNSServerConfigEntryValidationResult();
        result.setIpError(checkIp(dnsEntry.getIp(), context));
        result.setPortError(checkPort(dnsEntry.getPort(), context));
        return result;
    }

    private String checkIp(String ip, Context context) {
        String result = null;
        if (ip.trim().isEmpty()) {
            result = context.getString(R.string.ipEmptyError);
        }
        return result;
    }

    private String checkPort(String port, Context context) {
        String result = null;
        if (port.trim().isEmpty()) {
            result = context.getString(R.string.portEmptyError);
        }
        return result;
    }
}

