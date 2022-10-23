package dnsfilter.android.dnsserverconfig.widget.listitem;

public class DNSServerConfigCommentedEntry extends DNSServerConfigBaseEntry {
    private String comment;

    public DNSServerConfigCommentedEntry(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return CHAR_LINE_COMMENTED + comment;
    }
}
