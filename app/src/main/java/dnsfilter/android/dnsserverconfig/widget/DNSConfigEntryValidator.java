package dnsfilter.android.dnsserverconfig.widget;


import android.content.Context;

import java.net.MalformedURLException;
import java.net.URL;

import dnsfilter.android.R;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSConfigEntryValidator {

    private final String emptyIpErrorResult;
    private final String invalidIpErrorResult;
    private final String emptyPortErrorResult;
    private final String invalidPortErrorResult;
    private final String emptyEndpointErrorResult;
    private final String invalidEndpointErrorResult;

    public DNSConfigEntryValidator(Context context) {
        this(
                context.getString(R.string.ipEmptyError),
                context.getString(R.string.ipInvalidError),
                context.getString(R.string.portEmptyError),
                context.getString(R.string.portInvalidError),
                context.getString(R.string.endpointEmptyError),
                context.getString(R.string.endpointInvalidError)
        );
    }

    public DNSConfigEntryValidator(
            String emptyIpErrorResult,
            String invalidIpErrorResult,
            String emptyPortErrorResult,
            String invalidPortErrorResult,
            String emptyEndpointErrorResult,
            String invalidEndpointErrorResult
    ) {
        this.emptyIpErrorResult = emptyIpErrorResult;
        this.invalidIpErrorResult = invalidIpErrorResult;
        this.emptyPortErrorResult = emptyPortErrorResult;
        this.invalidPortErrorResult = invalidPortErrorResult;
        this.emptyEndpointErrorResult = emptyEndpointErrorResult;
        this.invalidEndpointErrorResult = invalidEndpointErrorResult;
    }

    public DNSServerConfigEntryValidationResult validate(DNSServerConfigEntry dnsEntry) {
        DNSServerConfigEntryValidationResult result = new DNSServerConfigEntryValidationResult();
        if (dnsEntry == null) {
            result.setIpError(emptyIpErrorResult);
            result.setPortError(emptyPortErrorResult);
            return result;
        }
        result.setIpError(checkIp(dnsEntry.getIp()));
        result.setPortError(checkPort(dnsEntry.getPort()));
        result.setEndpointError(checkEndpoint(dnsEntry.getProtocol(), dnsEntry.getEndpoint()));
        return result;
    }

    private String checkIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return emptyIpErrorResult;
        }
        String trimmed = ip.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (!isValidIPv4(trimmed) && !isValidIPv6(trimmed)) {
            return invalidIpErrorResult;
        }
        return null;
    }

    private String checkPort(String port) {
        if (port == null || port.trim().isEmpty()) {
            return emptyPortErrorResult;
        }
        String trimmed = port.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                return invalidPortErrorResult;
            }
        }
        try {
            int p = Integer.parseInt(trimmed);
            if (p < 1 || p > 65535) {
                return invalidPortErrorResult;
            }
        } catch (NumberFormatException e) {
            return invalidPortErrorResult;
        }
        return null;
    }

    private String checkEndpoint(DNSType protocol, String endpoint) {
        String trimmed = endpoint == null ? "" : endpoint.trim();
        if (protocol == DNSType.DOH) {
            if (trimmed.isEmpty()) {
                return emptyEndpointErrorResult;
            }
            try {
                URL url = new URL(trimmed);
                String proto = url.getProtocol();
                String host = url.getHost();
                if ((!"https".equalsIgnoreCase(proto) && !"http".equalsIgnoreCase(proto))
                        || host == null || host.trim().isEmpty()) {
                    return invalidEndpointErrorResult;
                }
            } catch (MalformedURLException e) {
                return invalidEndpointErrorResult;
            }
        } else if (protocol == DNSType.DOT && !trimmed.isEmpty()) {
            for (int i = 0; i < trimmed.length(); i++) {
                if (Character.isWhitespace(trimmed.charAt(i))) {
                    return invalidEndpointErrorResult;
                }
            }
            if (trimmed.contains("://")) {
                return invalidEndpointErrorResult;
            }
        }
        return null;
    }

    static boolean isValidIPv4(String ip) {
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            String oct = octets[i];
            if (oct.isEmpty() || oct.length() > 3) {
                return false;
            }
            for (int j = 0; j < oct.length(); j++) {
                char c = oct.charAt(j);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            if (oct.length() > 1 && oct.charAt(0) == '0') {
                return false;
            }
            int val = Integer.parseInt(oct);
            if (val < 0 || val > 255) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidIPv6(String ip) {
        int zoneIdx = ip.indexOf('%');
        if (zoneIdx != -1) {
            if (zoneIdx == 0 || zoneIdx == ip.length() - 1) {
                return false;
            }
            ip = ip.substring(0, zoneIdx);
        }
        if (ip.isEmpty() || !ip.contains(":")) {
            return false;
        }
        int doubleColon = ip.indexOf("::");
        if (doubleColon != -1 && ip.indexOf("::", doubleColon + 2) != -1) {
            return false;
        }

        String left;
        String right;
        if (doubleColon != -1) {
            left = ip.substring(0, doubleColon);
            right = ip.substring(doubleColon + 2);
        } else {
            left = ip;
            right = null;
        }

        int totalGroups = 0;
        boolean hasIPv4Tail = false;

        if (!left.isEmpty()) {
            String[] parts = left.split(":", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].contains(".")) {
                    if (right != null || i != parts.length - 1 || !isValidIPv4(parts[i])) {
                        return false;
                    }
                    totalGroups += 2;
                    hasIPv4Tail = true;
                } else {
                    if (!isHexGroup(parts[i])) {
                        return false;
                    }
                    totalGroups++;
                }
            }
        }

        if (right != null && !right.isEmpty()) {
            String[] parts = right.split(":", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].contains(".")) {
                    if (i != parts.length - 1 || !isValidIPv4(parts[i])) {
                        return false;
                    }
                    totalGroups += 2;
                    hasIPv4Tail = true;
                } else {
                    if (!isHexGroup(parts[i])) {
                        return false;
                    }
                    totalGroups++;
                }
            }
        }

        if (doubleColon == -1) {
            return totalGroups == 8;
        }
        int maxCompressedGroups = hasIPv4Tail ? 7 : 7;
        return totalGroups <= maxCompressedGroups;
    }

    private static boolean isHexGroup(String group) {
        if (group.isEmpty() || group.length() > 4) {
            return false;
        }
        for (int i = 0; i < group.length(); i++) {
            char c = group.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
