package dnsfilter.android;

import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


public class FilterConfig {

    TableLayout configTable;


    public class FilterConfigEntry {
        boolean active;
        String id;
        String url;

        public FilterConfigEntry(boolean active, String id, String url) {
            this.active=active;
            this.id = id;
            this.url=url;
        }
    }

    public FilterConfig(TableLayout table) {
        configTable=table;
        addItem(new FilterConfigEntry(true,"adaway", "https://adaway.org/hosts.txt"));
        addItem(new FilterConfigEntry(false,"montanamenagerie", "http://www.montanamenagerie.org/hostsfile/hosts.txt"));
        addItem(new FilterConfigEntry(false,"pgl.yoyo.org", "http://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"));
    }

    private View[] getContentCells(TableRow row) {

        View[] result = new View[4];
        for (int i = 0; i < 4; i++)
            result[i] = row.getChildAt(i);

        result[1] = ((ViewGroup) result[1]).getChildAt(0); // element 1 is a scrollview with nested EditText
        result[2] = ((ViewGroup) result[2]).getChildAt(0); // element 2 is a scrollview with nested EditText

        return result;
    }

    private void addItem(FilterConfigEntry entry) {
        TableRow row = (TableRow) LayoutInflater.from(configTable.getContext()).inflate(R.layout.filterconfigentry, null);
        configTable.addView(row);
        View[] cells = getContentCells(row);
        ((CheckBox) cells[0]).setChecked(entry.active);
        ((EditText) cells[1]).setText(entry.id);
        ((EditText) cells[2]).setText(entry.url);
        ((TextView) cells[3]).setText("X");
    }

    public void load(FilterConfigEntry[] entries) {
        for (int i = 0; i < entries.length; i++)
            addItem(entries[i]);
    }

    public FilterConfigEntry[] getFilterEntries() {
        return new FilterConfigEntry[0];
    }

    public void clear() {
    }
}
