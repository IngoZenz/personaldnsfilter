package dnsfilter.android.dnsserverconfig.widget;


import android.content.Context;

import dnsfilter.android.R;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSConfigEntryValidator {

    private final String emptyIpErrorResult;
    private final String emptyPortErrorResult;

    public DNSConfigEntryValidator(Context context) {
        emptyIpErrorResult = context.getString(R.string.ipEmptyError);
        emptyPortErrorResult = context.getString(R.string.portEmptyError);
    }

    public DNSServerConfigEntryValidationResult validate(DNSServerConfigEntry dnsEntry) {
        DNSServerConfigEntryValidationResult result = new DNSServerConfigEntryValidationResult();
        result.setIpError(checkIp(dnsEntry.getIp()));
        result.setPortError(checkPort(dnsEntry.getPort()));
        return result;
    }

    private String checkIp(String ip) {
        String result = null;
        if (ip.trim().isEmpty()) {
            result = emptyIpErrorResult;
        }
        return result;
    }

    private String checkPort(String port) {
        String result = null;
        if (port.trim().isEmpty()) {
            result = emptyPortErrorResult;
        }
        return result;
    }
}

