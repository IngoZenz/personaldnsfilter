package dnsfilter.android.widget;


import java.util.Objects;

public class DNSRecord {

    private static final String DEFAULT_IP = "127.0.0.1";
    private static final Byte DEFAULT_DNS_SELECTION = 0;
    private static final String DEFAULT_PORT = "53";
    private static final String DEFAULT_ENDPOINT = "";
    private static final Boolean DEFAULT_IS_ACTIVE = true;
    public static final String CHAR_LINE_COMMENTED = "#";
    private static final String EMPTY_STRING = "";
    public static final String RECORD_PARTS_SEPARATOR = "::";
    public static final String SHORTER_IP_V6_SEPARATOR = "::";
    public static final String IP_V6_START_BRACER = "[";
    public static final String IP_V6_END_BRACER = "]";

    private String ip;
    private String port;
    private DNSType protocol;
    private String endpoint;

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setProtocol(DNSType protocol) {
        this.protocol = protocol;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    private Boolean isActive;

    public DNSRecord(String ip, String port, DNSType protocol, String endpoint, Boolean isActive) {
        this.ip = ip;
        this.port = port;
        this.protocol = protocol;
        this.endpoint = endpoint;
        this.isActive = isActive;
    }

    public DNSRecord() {
        this(DEFAULT_IP, DEFAULT_PORT, getDefaultDNSType(), DEFAULT_ENDPOINT, DEFAULT_IS_ACTIVE);
    }

    public DNSRecord(String ip, boolean isActive) {
        this(ip, DEFAULT_PORT, getDefaultDNSType(), DEFAULT_ENDPOINT, isActive);
    }

    public DNSRecord(String ip, String port, boolean isActive) {
        this(ip, port, getDefaultDNSType(), DEFAULT_ENDPOINT, isActive);
    }

    public DNSRecord(String ip, String port, DNSType protocol, boolean isActive) {
        this(ip, port, protocol, DEFAULT_ENDPOINT, isActive);
    }

    public String getIp() {
        return ip;
    }

    public String getPort() {
        return port;
    }

    public DNSType getProtocol() {
        return protocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return getIsActiveAsCommented(this.isActive)
                + highlightShorterIPv6(this.ip)
                + RECORD_PARTS_SEPARATOR
                + port
                + RECORD_PARTS_SEPARATOR
                + protocol.toString()
                + getEndpointAsString(endpoint);
    }

    private static String highlightShorterIPv6(String ip) {
        if (ip.contains(SHORTER_IP_V6_SEPARATOR)) {
            return IP_V6_START_BRACER + ip + IP_V6_END_BRACER;
        } else {
            return ip;
        }
    }

    private static String getEndpointAsString(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return RECORD_PARTS_SEPARATOR + endpoint;
        }
    }

    public Boolean getIsActive() {
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSRecord dnsRecord = (DNSRecord) o;
        return Objects.equals(ip, dnsRecord.ip) && Objects.equals(port, dnsRecord.port) && protocol == dnsRecord.protocol && Objects.equals(endpoint, dnsRecord.endpoint) && Objects.equals(isActive, dnsRecord.isActive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port, protocol, endpoint, isActive);
    }

    public static String getIsActiveAsCommented(boolean isActive) {
        if (isActive) {
            return EMPTY_STRING;
        } else {
            return CHAR_LINE_COMMENTED;
        }
    }

    private static DNSType getDefaultDNSType() {
        return DNSType.values()[DEFAULT_DNS_SELECTION];
    }
}
