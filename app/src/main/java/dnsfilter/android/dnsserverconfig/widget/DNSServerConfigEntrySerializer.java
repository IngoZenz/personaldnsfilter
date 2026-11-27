package dnsfilter.android.dnsserverconfig.widget;

import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry.CHAR_LINE_COMMENTED;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.CHAR_ENTRY_INACTIVE;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.EMPTY_STRING;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.ENTRY_PARTS_SEPARATOR;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.IP_END_BRACER;
import static dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry.IP_START_BRACER;

import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigBaseEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigCommentedEntry;
import dnsfilter.android.dnsserverconfig.widget.listitem.DNSServerConfigEntry;

public class DNSServerConfigEntrySerializer {

    private DNSServerConfigBaseEntry deserializeImpl(String entry) {

        if (entry == null) {
            return new DNSServerConfigEntry();
        }
        entry = entry.trim();
        if (entry.isEmpty()) {
            return new DNSServerConfigEntry();
        }

        if (entry.startsWith(CHAR_LINE_COMMENTED)) {
            return new DNSServerConfigCommentedEntry(entry.substring(CHAR_LINE_COMMENTED.length()));
        }

        DNSServerConfigEntry newEntry;
        boolean isActive = !entry.startsWith(CHAR_ENTRY_INACTIVE);
        if (!isActive) {
            entry = entry.substring(CHAR_ENTRY_INACTIVE.length()).trim();
        }

        String[] splittedEntry;
        if (entry.startsWith(IP_START_BRACER)) {
            int ip6BracesEnd = entry.indexOf(IP_END_BRACER);
            if (ip6BracesEnd <= 0) {
                throw new IllegalArgumentException("Unclosed IPv6 bracket in entry: " + entry);
            }
            String bracedIP = entry.substring(1, ip6BracesEnd);
            String remainder = entry.substring(ip6BracesEnd + 1).trim();
            if (remainder.isEmpty()) {
                splittedEntry = new String[]{bracedIP};
            } else if (!remainder.startsWith(ENTRY_PARTS_SEPARATOR)) {
                throw new IllegalArgumentException("Invalid separator after IPv6 address: " + entry);
            } else {
                String[] partWithoutIP = remainder.substring(ENTRY_PARTS_SEPARATOR.length()).split(ENTRY_PARTS_SEPARATOR, 3);
                splittedEntry = new String[1 + partWithoutIP.length];
                splittedEntry[0] = bracedIP;
                System.arraycopy(partWithoutIP, 0, splittedEntry, 1, partWithoutIP.length);
            }
        } else {
            splittedEntry = entry.split(ENTRY_PARTS_SEPARATOR, 4);
        }

        if (splittedEntry.length == 1) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], isActive);
        } else if (splittedEntry.length == 2) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], isActive);
        } else if (splittedEntry.length == 3) {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], DNSType.valueOf(splittedEntry[2].trim().toUpperCase()), isActive);
        } else {
            newEntry = new DNSServerConfigEntry(splittedEntry[0], splittedEntry[1], DNSType.valueOf(splittedEntry[2].trim().toUpperCase()), splittedEntry[3], isActive);
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
}
