package dnsfilter.android.dnsserverconfig.widget.listitem;

public class DNSServerConfigCommentedEntry extends DNSServerConfigBaseEntry {
    private String comment;
    private boolean isVisible;

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    public DNSServerConfigCommentedEntry(String comment) {
        this.comment = comment;
        this.isVisible = true;
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
