package dnsfilter.android.dnsserverconfig.widget;

import java.util.Objects;

public class DNSServerConfigTestResult {

    private static final DNSServerConfigEntryTestState DEFAULT_STATE = DNSServerConfigEntryTestState.NOT_STARTED;
    private static final String DEFAULT_MESSAGE = "";
    private static final long DEFAULT_PERFORMANCE = 0;

    private DNSServerConfigEntryTestState testState;
    private String message;
    private long perf;

    public DNSServerConfigTestResult(DNSServerConfigEntryTestState testState, String message, long perf) {
        this.testState = testState;
        this.message = message;
        this.perf = perf;
    }

    public DNSServerConfigTestResult(DNSServerConfigEntryTestState testState, String message) {
        this(testState, message, DEFAULT_PERFORMANCE);
    }

    public DNSServerConfigTestResult(DNSServerConfigEntryTestState testState, long perf) {
        this(testState, DEFAULT_MESSAGE, perf);
    }

    public DNSServerConfigTestResult(DNSServerConfigEntryTestState testState) {
        this(testState, DEFAULT_MESSAGE, DEFAULT_PERFORMANCE);
    }

    public DNSServerConfigTestResult() {
        this(DEFAULT_STATE, DEFAULT_MESSAGE, DEFAULT_PERFORMANCE);
    }

    public void setTestState(DNSServerConfigEntryTestState testState) {
        this.testState = testState;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setPerf(long perf) {
        this.perf = perf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerConfigTestResult that = (DNSServerConfigTestResult) o;
        return perf == that.perf && testState == that.testState && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testState, message, perf);
    }

    public DNSServerConfigEntryTestState getTestState() {
        return testState;
    }

    public String getMessage() {
        return message;
    }

    public long getPerf() {
        return perf;
    }
}
