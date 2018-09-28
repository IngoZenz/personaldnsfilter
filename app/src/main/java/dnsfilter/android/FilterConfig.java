package dnsfilter.android;

import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.View.OnClickListener;

import java.net.MalformedURLException;
import java.net.URL;


public class FilterConfig implements OnClickListener {

	static String NEW_ITEM = "<new>";
	static String INVALID_URL = "<invalid URL!>";
	static String ADD_ITEM = "+";
	static String DEL_ITEM = "-";

	TableLayout configTable;
	FilterConfigEntry[] filterEntries;
	boolean loaded = false;

	public static class FilterConfigEntry {
		boolean active;
		String id;
		String url;

		public FilterConfigEntry(boolean active, String id, String url) {
			this.active = active;
			this.id = id;
			this.url = url;
		}
	}

	public FilterConfig(TableLayout table) {
		configTable = table;
	}

	private View[] getContentCells(TableRow row) {

		View[] result = new View[4];
		for (int i = 0; i < 4; i++)
			result[i] = row.getChildAt(i);

		result[1] = ((ViewGroup) result[1]).getChildAt(0); // element 1 is a scrollview with nested EditText
		result[2] = ((ViewGroup) result[2]).getChildAt(0); // element 2 is a scrollview with nested EditText

		return result;
	}

	private void addItem(FilterConfigEntry entry, String lastColumnValue) {
		TableRow row = (TableRow) LayoutInflater.from(configTable.getContext()).inflate(R.layout.filterconfigentry, null);
		configTable.addView(row);
		View[] cells = getContentCells(row);
		((CheckBox) cells[0]).setChecked(entry.active);
		((EditText) cells[1]).setText(entry.id);
		((EditText) cells[2]).setText(entry.url);
		((TextView) cells[3]).setText(lastColumnValue);
		cells[3].setOnClickListener(this);
	}


	public void setEntries(FilterConfigEntry[] entries) {
		this.filterEntries = entries;
	}

	public void load() {
		if (loaded) return;

		for (int i = 0; i < filterEntries.length; i++)
			addItem(filterEntries[i], DEL_ITEM);
		addEmptyEndItem();

		loaded = true;
	}

	private void addEmptyEndItem() {
		addItem(new FilterConfigEntry(false, NEW_ITEM, NEW_ITEM), ADD_ITEM);
	}

	public FilterConfigEntry[] getFilterEntries() {
		if (!loaded)
			return filterEntries;

		int count = configTable.getChildCount() - 2;
		FilterConfigEntry[] result = new FilterConfigEntry[count];
		for (int i = 0; i < count; i++) {
			View[] rowContent = getContentCells((TableRow) configTable.getChildAt(i + 1));
			if (!handleRowChange(rowContent))
				return filterEntries;
			else {
				result[i] = new FilterConfigEntry(((CheckBox) rowContent[0]).isChecked(), ((EditText) rowContent[1]).getText().toString().trim(), ((EditText) rowContent[2]).getText().toString().trim());
			}
		}
		return result;
	}

	public void clear() {
		filterEntries = getFilterEntries();
		int count = configTable.getChildCount() - 1;
		for (int i = count; i > 0; i--)
			configTable.removeViewAt(i);

		loaded = false;
	}

	@Override
	public void onClick(View v) {
		if (((TextView) v).getText().toString().equals(DEL_ITEM)) {
			v.setOnClickListener(null);
			configTable.removeView((View) v.getParent());
		} else {
			newItem((TableRow) v.getParent());
		}
	}

	private void newItem(TableRow row) {
		View[] cells = getContentCells(row);
		if (handleRowChange(cells)) {
			((TextView) cells[3]).setText(DEL_ITEM);
			addEmptyEndItem();
		}
	}

	private boolean handleRowChange(View[] cells) {
		try {
			URL url = new URL(((EditText) cells[2]).getText().toString());
			String shortTxt = ((EditText) cells[1]).getText().toString().trim();
			if (shortTxt.equals(NEW_ITEM) || shortTxt.equals(""))
				((EditText) cells[1]).setText(url.getHost());
			return true;
		} catch (MalformedURLException e) {
			((EditText) cells[2]).setText(INVALID_URL);
			return false;
		}
	}
}
