package dnsfilter.android.dnsserverconfig.widget;

public enum DNSType {
    UDP(53),
    DOT(853),
    DOH(443);

    public final int defaultPort;

    DNSType(int defaultPort) {
        this.defaultPort = defaultPort;
    }
}
