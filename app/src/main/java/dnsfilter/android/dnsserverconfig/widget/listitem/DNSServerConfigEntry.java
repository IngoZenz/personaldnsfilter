package dnsfilter.android.dnsserverconfig.widget.listitem;


import dnsfilter.android.dnsserverconfig.widget.DNSServerConfigEntryValidationResult;
import dnsfilter.android.dnsserverconfig.widget.DNSServerConfigTestResult;
import dnsfilter.android.dnsserverconfig.widget.DNSType;

public class DNSServerConfigEntry extends DNSServerConfigBaseEntry {

    private static final String DEFAULT_IP = "";
    private static final Byte DEFAULT_DNS_SELECTION = 0;
    private static final String DEFAULT_PORT = "53";
    private static final String DEFAULT_ENDPOINT = "";
    private static final Boolean DEFAULT_IS_ACTIVE = true;
    public static final String CHAR_ENTRY_INACTIVE = "~";
    public static final String EMPTY_STRING = "";
    public static final String ENTRY_PARTS_SEPARATOR = "::";
    public static final String SHORTER_IP_V6_SEPARATOR = "::";
    public static final String IP_START_BRACER = "[";
    public static final String IP_END_BRACER = "]";

    private String ip;
    private String port;
    private DNSType protocol;
    private String endpoint;
    private DNSServerConfigTestResult testResult;
    private DNSServerConfigEntryValidationResult validationResult;

    public DNSServerConfigEntryValidationResult getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(DNSServerConfigEntryValidationResult validationResult) {
        this.validationResult = validationResult;
    }

    public void setTestResult(DNSServerConfigTestResult testState) {
        this.testResult = testState;
    }

    public DNSServerConfigTestResult getTestResult() {
        return testResult;
    }

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

    public DNSServerConfigEntry(String ip, String port, DNSType protocol, String endpoint, Boolean isActive) {
        this.ip = ip.trim();
        this.port = port.trim();
        this.protocol = protocol;
        this.endpoint = endpoint.trim();
        this.isActive = isActive;
        this.testResult = new DNSServerConfigTestResult();
        this.validationResult = new DNSServerConfigEntryValidationResult();
    }

    public DNSServerConfigEntry() {
        this(DEFAULT_IP, DEFAULT_PORT, getDefaultDNSType(), DEFAULT_ENDPOINT, DEFAULT_IS_ACTIVE);
    }

    public DNSServerConfigEntry(String ip, boolean isActive) {
        this(ip, DEFAULT_PORT, getDefaultDNSType(), DEFAULT_ENDPOINT, isActive);
    }

    public DNSServerConfigEntry(String ip, String port, boolean isActive) {
        this(ip, port, getDefaultDNSType(), DEFAULT_ENDPOINT, isActive);
    }

    public DNSServerConfigEntry(String ip, String port, DNSType protocol, boolean isActive) {
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
        return getIsActiveAsString(this.isActive)
                + getBracedIP(this.ip)
                + ENTRY_PARTS_SEPARATOR
                + port
                + ENTRY_PARTS_SEPARATOR
                + protocol.toString()
                + getEndpointAsString(endpoint);
    }

    private static String getBracedIP(String ip) {
        return IP_START_BRACER + ip + IP_END_BRACER;
    }

    private static String getEndpointAsString(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return ENTRY_PARTS_SEPARATOR + endpoint;
        }
    }

    public Boolean getIsActive() {
        return isActive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerConfigEntry dnsServerConfigEntry = (DNSServerConfigEntry) o;
        return ip.equals(dnsServerConfigEntry.ip)
                && port.equals(dnsServerConfigEntry.port)
                && protocol == dnsServerConfigEntry.protocol
                && endpoint.equals(dnsServerConfigEntry.endpoint)
                && isActive.equals(dnsServerConfigEntry.isActive)
                && testResult.equals(dnsServerConfigEntry.testResult)
                && validationResult.equals(dnsServerConfigEntry.validationResult);
/*
        return Objects.equals(ip, dnsServerConfigEntry.ip)
                && Objects.equals(port, dnsServerConfigEntry.port)
                && protocol == dnsServerConfigEntry.protocol
                && Objects.equals(endpoint, dnsServerConfigEntry.endpoint)
                && Objects.equals(isActive, dnsServerConfigEntry.isActive)
                && Objects.equals(testResult, dnsServerConfigEntry.testResult)
                && Objects.equals(validationResult, dnsServerConfigEntry.validationResult);
*/
    }

    @Override
    public int hashCode() {
        //return Objects.hash(ip, port, protocol, endpoint, isActive, testResult, validationResult);
        Object[] objects = new Object[] {ip, port, protocol, endpoint, isActive, testResult, validationResult};
        int hash = 0;
        for (int i = 0; i < objects.length; i++)
            hash = 31*hash+objects[i].hashCode();

        return hash;
    }

    public static String getIsActiveAsString(boolean isActive) {
        if (isActive) {
            return EMPTY_STRING;
        } else {
            return CHAR_ENTRY_INACTIVE;
        }
    }

    private static DNSType getDefaultDNSType() {
        return DNSType.values()[DEFAULT_DNS_SELECTION];
    }
}
