package dnsfilter.android.dnsserverconfig.widget;

import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.CHAR_ENTRY_INACTIVE;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.EMPTY_STRING;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.ENTRY_PARTS_SEPARATOR;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.IP_V6_END_BRACER;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.IP_V6_START_BRACER;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry.CHAR_LINE_COMMENTED;

import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigCommentedEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry;

public class DNSServerConfigEntrySerializer {

    private DNSServerConfigBaseEntry deserializeImpl(String entry) {

        if (entry == null || entry.isEmpty()) {
            return new DNSServerConfigEntry();
        }

        if (entry.startsWith(CHAR_LINE_COMMENTED)) {
            return new DNSServerConfigCommentedEntry(entry.replaceFirst(CHAR_LINE_COMMENTED, EMPTY_STRING));
        }

        DNSServerConfigEntry newEntry;
        boolean isActive = !entry.startsWith(CHAR_ENTRY_INACTIVE);
        if (!isActive) {
            entry = entry.replaceFirst(CHAR_ENTRY_INACTIVE, EMPTY_STRING);
        }

        String shortIpV6 = getShortIPV6(entry);
        String[] splittedEntry;
        if (shortIpV6 == null) {
            splittedEntry = entry.split(ENTRY_PARTS_SEPARATOR, 4);
        } else {
            String[] partWithoutIP = entry
                    .substring(entry.indexOf(IP_V6_END_BRACER))
                    .split(ENTRY_PARTS_SEPARATOR);
            if (partWithoutIP.length > 1) {
                splittedEntry = new String[1 + partWithoutIP.length - 1];
                splittedEntry[0] = shortIpV6;
                System.arraycopy(partWithoutIP, 1, splittedEntry, 1, partWithoutIP.length - 1);
            } else {
                splittedEntry = new String[1];
                splittedEntry[0] = shortIpV6;
            }
        }

        if (splittedEntry.length == 1) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], isActive);
        } else if (splittedEntry.length == 2) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], isActive);
        } else if (splittedEntry.length == 3) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], DNSType.valueOf(splittedEntry[2].toUpperCase()), isActive);
        } else {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], DNSType.valueOf(splittedEntry[2].toUpperCase()), splittedEntry[3], isActive);
        }

        return newEntry;
    }

    public DNSServerConfigBaseEntry deserializeSafe(String entry) {
        DNSServerConfigBaseEntry newEntry;
        try {
            newEntry = deserializeImpl(entry);
        } catch (RuntimeException e) {
            newEntry = new DNSServerConfigEntry();
        }

        return newEntry;
    }

    public DNSServerConfigBaseEntry deserialize(String entry) throws NotDeserializableException {
        DNSServerConfigBaseEntry newEntry;
        try {
            newEntry = deserializeImpl(entry);
        } catch (RuntimeException e) {
            throw new NotDeserializableException("Not possibly to deserialize " + entry);
        }

        return newEntry;
    }

    private String getShortIPV6(String entry) {
        if (entry.contains(IP_V6_START_BRACER)) {
            int ipv6BracesStart = entry.indexOf(IP_V6_START_BRACER);
            int ip6BracesEnd = entry.indexOf(IP_V6_END_BRACER);
            return entry.substring(ipv6BracesStart + 1, ip6BracesEnd);
        } else {
            return null;
        }
    }
}
