package dnsfilter.android.widget;

import static dnsfilter.android.widget.DNSRecord.CHAR_LINE_COMMENTED;
import static dnsfilter.android.widget.DNSRecord.IP_V6_END_BRACER;
import static dnsfilter.android.widget.DNSRecord.IP_V6_START_BRACER;
import static dnsfilter.android.widget.DNSRecord.RECORD_PARTS_SEPARATOR;

public class DNSRecordSerializer {

    public DNSRecord deserialize(String record) {

        if (record == null || record.isEmpty()) {
            return new DNSRecord();
        }

        DNSRecord newRecord;

        try {
            boolean isActive = !record.startsWith(CHAR_LINE_COMMENTED);
            if (!isActive) {
                record = record.replace(CHAR_LINE_COMMENTED, "");
            }

            String shortIpV6 = getShortIPV6(record);
            String[] splittedRecord;
            if (shortIpV6 == null) {
                splittedRecord = record.split(RECORD_PARTS_SEPARATOR, 4);
            } else {
                String[] partWithoutIP = record
                        .substring(record.indexOf(IP_V6_END_BRACER))
                        .split(RECORD_PARTS_SEPARATOR);
                if (partWithoutIP.length > 1) {
                    splittedRecord = new String[1 + partWithoutIP.length - 1];
                    splittedRecord[0] = shortIpV6;
                    System.arraycopy(partWithoutIP, 1, splittedRecord, 1, partWithoutIP.length - 1);
                } else {
                    splittedRecord = new String[1];
                    splittedRecord[0] = shortIpV6;
                }
            }

            if (splittedRecord.length == 1) {
                newRecord = new DNSRecord(splittedRecord[0], isActive);
            } else if (splittedRecord.length == 2) {
                newRecord = new DNSRecord(splittedRecord[0], splittedRecord[1], isActive);
            } else if (splittedRecord.length == 3) {
                newRecord = new DNSRecord(splittedRecord[0], splittedRecord[1], DNSType.valueOf(splittedRecord[2]), isActive);
            } else {
                newRecord = new DNSRecord(splittedRecord[0], splittedRecord[1], DNSType.valueOf(splittedRecord[2]), splittedRecord[3], isActive);
            }
        } catch (RuntimeException e) {
            newRecord = new DNSRecord();
        }

        return newRecord;
    }

    private String getShortIPV6(String record) {
        if (record.contains(IP_V6_START_BRACER)) {
            int ipv6BracesStart = record.indexOf(IP_V6_START_BRACER);
            int ip6BracesEnd = record.indexOf(IP_V6_END_BRACER);
            return record.substring(ipv6BracesStart + 1, ip6BracesEnd);
        } else {
            return null;
        }
    }
}
