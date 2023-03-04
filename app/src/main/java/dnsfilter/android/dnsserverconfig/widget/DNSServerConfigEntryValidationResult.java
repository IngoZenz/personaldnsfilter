package dnsfilter.android.dnsserverconfig.widget;

public class DNSServerConfigEntryValidationResult {

    private String ipError;
    private String portError;

    public boolean hasError() {
        return ipError != null || portError != null;
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

    public DNSServerConfigEntryValidationResult() {
        this.ipError = null;
        this.portError = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerConfigEntryValidationResult that = (DNSServerConfigEntryValidationResult) o;
        //return Objects.equals(ipError, that.ipError) && Objects.equals(portError, that.portError);
        if ((ipError != null && portError == null)|| (ipError != null && portError == null))
            return false;
        if ((ipError == null && portError == null))
            return true;

        return ipError.equals(that.ipError) && portError.equals(that.portError);
    }

    @Override
    public int hashCode() {
        return 31*ipError.hashCode()+ portError.hashCode();
    }

}
