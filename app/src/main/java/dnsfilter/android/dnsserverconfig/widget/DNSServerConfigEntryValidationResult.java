package dnsfilter.android.dnsserverconfig.widget;

public class DNSServerConfigEntryValidationResult {

    private String ipError;
    private String portError;
    private String endpointError;

    public boolean hasError() {
        return ipError != null || portError != null || endpointError != null;
    }

    public String getIpError() {
        return ipError;
    }

    public void setIpError(String ipError) {
        this.ipError = ipError;
    }

    public String getPortError() {
        return portError;
    }

    public void setPortError(String portError) {
        this.portError = portError;
    }

    public String getEndpointError() {
        return endpointError;
    }

    public void setEndpointError(String endpointError) {
        this.endpointError = endpointError;
    }

    public DNSServerConfigEntryValidationResult() {
        this.ipError = null;
        this.portError = null;
        this.endpointError = null;
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    private static int safeHash(Object a) {
        return a != null ? a.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerConfigEntryValidationResult that = (DNSServerConfigEntryValidationResult) o;
        return safeEquals(ipError, that.ipError)
                && safeEquals(portError, that.portError)
                && safeEquals(endpointError, that.endpointError);
    }

    @Override
    public int hashCode() {
        int result = safeHash(ipError);
        result = 31 * result + safeHash(portError);
        result = 31 * result + safeHash(endpointError);
        return result;
    }

}
