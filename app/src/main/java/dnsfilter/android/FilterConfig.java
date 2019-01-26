package dnsfilter.android;

import android.app.Dialog;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.View.OnClickListener;

import java.net.MalformedURLException;
import java.net.URL;

import util.Logger;


public class FilterConfig implements OnClickListener, DialogInterface.OnKeyListener {

	static String NEW_ITEM = "<new>";
	static String INVALID_URL = "<invalid URL!>";


	TableLayout configTable;
	FilterConfigEntry[] filterEntries;
	boolean loaded = false;

	TableRow editedRow;
	Button editOk;
	Button editDelete;
	Button editCancel;
	Dialog editDialog;
	Button categoryUp;
	Button categoryDown;
	TextView categoryField;

	@Override
	public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)
			dialog.dismiss();

		return false;
	}

	public void cleanUp() {
		//ToDo: Clear References!
	}


	public static class FilterConfigEntry {
		boolean active;
		String category;
		String id;
		String url;

		public FilterConfigEntry(boolean active, String category, String id, String url) {
			this.active = active;
			this.category = category;
			this.id = id;
			this.url = url;
		}
	}

	public FilterConfig(TableLayout table, Button categoryUp, Button categoryDn, TextView categoryField ) {

		configTable = table;
		editDialog = new Dialog(table.getContext(), R.style.Theme_dialog_TitleBar);

		editDialog.setOnKeyListener(this);
		editDialog.setContentView(R.layout.filterentryeditdialog);
		editDialog.setTitle(table.getContext().getResources().getString(R.string.editFilterDialogTitle));
		editOk = editDialog.findViewById(R.id.filterEditOkBtn);
		editDelete = editDialog.findViewById(R.id.filterEditDelBtn);
		editCancel = editDialog.findViewById(R.id.filterEditCancelBtn);
		editOk.setOnClickListener(this);
		editDelete.setOnClickListener(this);
		editCancel.setOnClickListener(this);
		
		this.categoryUp = categoryUp;
		this.categoryDown = categoryDn;
		this.categoryField = categoryField;
		categoryDown.setOnClickListener(this);
		categoryUp.setOnClickListener(this);
	}

	private View[] getContentCells(TableRow row) {

		View[] result = new View[5];
		for (int i = 0; i < 5; i++)
			result[i] = row.getChildAt(i);

		result[2] = ((ViewGroup) result[2]).getChildAt(0); // element 1 is a scrollview with nested TextView
		result[3] = ((ViewGroup) result[3]).getChildAt(0); // element 2 is a scrollview with nested TextView

		return result;
	}

	private void addItem(FilterConfigEntry entry) {
		TableRow row = (TableRow) LayoutInflater.from(configTable.getContext()).inflate(R.layout.filterconfigentry, null);
		configTable.addView(row);
		View[] cells = getContentCells(row);
		((CheckBox) cells[0]).setChecked(entry.active);
		((TextView) cells[1]).setText(entry.category);
		((TextView) cells[2]).setText(entry.id);
		((TextView) cells[3]).setText(entry.url);
		cells[4].setOnClickListener(this);
	}


	public void setEntries(FilterConfigEntry[] entries) {
		this.filterEntries = entries;
	}

	public void load() {
		if (loaded) return;

		for (int i = 0; i < filterEntries.length; i++)
			addItem(filterEntries[i]);
		addEmptyEndItem();

		loaded = true;
	}

	private void addEmptyEndItem() {
		addItem(new FilterConfigEntry(false, NEW_ITEM, NEW_ITEM, NEW_ITEM));
	}

	public String getCurrentCategory() {
		return categoryField.getText().toString();
	}

	public void setCurrentCategory(String category) {
		categoryField.setText(category);
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
				result[i] = new FilterConfigEntry(((CheckBox) rowContent[0]).isChecked(),((TextView) rowContent[1]).getText().toString().trim(), ((TextView) rowContent[2]).getText().toString().trim(), ((TextView) rowContent[3]).getText().toString().trim());
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
		if (v == editOk || v == editDelete || v == editCancel)
			handleEditDialogEvent(v);
		else if (v==categoryUp || v == categoryDown)
			handleCategoryChange((Button) v);
		else
			showEditDialog((TableRow) v.getParent());
	}

	private void handleCategoryChange(Button v) {
	}

	private void showEditDialog(TableRow row) {
		editedRow = row;
		View[] currentContent = getContentCells(editedRow);
		((CheckBox)editDialog.findViewById(R.id.activeChk)).setChecked(((CheckBox)currentContent[0]).isChecked());
		((TextView)editDialog.findViewById(R.id.filterCategory)).setText(((TextView)currentContent[1]).getText().toString());
		((TextView)editDialog.findViewById(R.id.filterName)).setText(((TextView)currentContent[2]).getText().toString());
		((TextView)editDialog.findViewById(R.id.filterUrl)).setText(((TextView)currentContent[3]).getText().toString());
		editDialog.show();
		WindowManager.LayoutParams lp = editDialog.getWindow().getAttributes();
		lp.width = (int)(configTable.getContext().getResources().getDisplayMetrics().widthPixels*1.00);;
		editDialog.getWindow().setAttributes(lp);
	}

	private void handleEditDialogEvent(View v) {
		if (v == editCancel) {
			editDialog.dismiss();
			return;
		}

		View[] currentContent = getContentCells(editedRow);
		boolean newItem = ((TextView)currentContent[2]).getText().toString().equals(NEW_ITEM);
		Logger.getLogger().logLine(newItem+", "+((TextView)currentContent[2]).getText().toString()+","+NEW_ITEM);

		if (v == editDelete) {
			if (!newItem) {
				editedRow.getChildAt(3).setOnClickListener(null);
				configTable.removeView(editedRow);
			}
			editDialog.dismiss();
		}
		else if (v == editOk) {
			boolean active = ((CheckBox)editDialog.findViewById(R.id.activeChk)).isChecked();
			String category = ((TextView)editDialog.findViewById(R.id.filterCategory)).getText().toString();
			String name = ((TextView)editDialog.findViewById(R.id.filterName)).getText().toString();
			String url = ((TextView)editDialog.findViewById(R.id.filterUrl)).getText().toString();

			((CheckBox)currentContent[0]).setChecked(active);
			((TextView)currentContent[1]).setText(category);
			((TextView)currentContent[2]).setText(name);
			((TextView)currentContent[3]).setText(url);

			if (newItem)
				newItem(editedRow);
			else
				handleRowChange(currentContent);

			editDialog.dismiss();
		}

	}

	private void newItem(TableRow row) {
		View[] cells = getContentCells(row);
		if (handleRowChange(cells)) {
			addEmptyEndItem();
		}
	}

	private boolean handleRowChange(View[] cells) {
		try {
			URL url = new URL(((TextView) cells[3]).getText().toString());
			String shortTxt = ((TextView) cells[2]).getText().toString().trim();
			if (shortTxt.equals(NEW_ITEM) || shortTxt.equals(""))
				((TextView) cells[1]).setText(url.getHost());
			return true;
		} catch (MalformedURLException e) {
			((TextView) cells[2]).setText(INVALID_URL);
			return false;
		}
	}
}
