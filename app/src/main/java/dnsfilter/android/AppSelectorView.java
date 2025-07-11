package dnsfilter.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;
import java.util.TreeSet;

import util.Logger;


public class AppSelectorView extends LinearLayout implements View.OnClickListener {

	private PackageManager pm = this.getContext().getPackageManager();
	private boolean loaded = false;
	private String selectedApps = "";
	private static int iconSizePx = 0;
	private static float iconSizeDP = 96;
	private AsyncLoader runningUpdate = null;

	private ComparableAppInfoWrapper[] wrappers = null;
	private View searchView;
	private View emptyResult;

	@Override
	public void onClick(View v) {
		//search clicked
		if (!loaded || runningUpdate != null)
			return ;

		String searchStr = ((EditText)searchView.findViewById(R.id.searchString)).getText().toString().toLowerCase();

		ComparableAppInfoWrapper[] allwrappers = wrappers;
		int count = 0;
		emptyResult.setVisibility(View.GONE);

		for (int i = 0; i < allwrappers.length; i++) {
			boolean visible = allwrappers[i].checkBox.getText().toString().toLowerCase().indexOf(searchStr) != -1;
			if (visible)
				allwrappers[i].checkBox.setVisibility(View.VISIBLE);
			else
				allwrappers[i].checkBox.setVisibility(View.GONE);

			if (visible)
				count++;
		}
		if (count == 0)
			emptyResult.setVisibility(View.VISIBLE);
		Logger.getLogger().logLine("Found: "+count+" apps!");

	}

	private class ComparableAppInfoWrapper implements Comparable<ComparableAppInfoWrapper> {

		private String appName = null;
		private ApplicationInfo wrapped = null;
		CheckBox checkBox = null;

		private ComparableAppInfoWrapper(ApplicationInfo wrapped, CheckBox checkBox) {
			appName = checkBox.getText().toString();
			this.wrapped = wrapped;
			this.checkBox = checkBox;
		}

		@Override
		public int compareTo(ComparableAppInfoWrapper o) {
			return appName.toUpperCase().compareTo(o.appName.toUpperCase());
		}
	}

	private class UIUpdate implements Runnable {

		private CheckBox checkBox;
		private Drawable icon;
		AsyncLoader update;

		private UIUpdate(CheckBox checkBox, Drawable icon, AsyncLoader update) {
			this.checkBox = checkBox;
			this.icon = icon;
			this.update = update;
		}

		@Override
		public void run() {
			if (update.abort)
				return;
			checkBox.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
			addView(checkBox);
		}
	}

	private class AsyncLoader implements Runnable {

		private boolean abort = false;

		@Override
		public synchronized void run() {

			if (iconSizePx == 0) {
				float scale = getResources().getDisplayMetrics().density;
				iconSizePx = (int) (iconSizeDP * scale + 0.5f);
			}

			if (abort)
				return;

			try {
				//set 'Loading apps...' info
				final TextView infoText = new TextView(getContext());
				infoText.setTextColor(Color.BLACK);
				infoText.setText("Loading apps...");

				post(new Runnable() {
					@Override
					public void run() {
						addView(infoText);
					}
				});

				String selectedapppackages = ("," + selectedApps + ",").replace(" ", "");

				ApplicationInfo[] packages = pm.getInstalledApplications(PackageManager.GET_META_DATA).toArray(new ApplicationInfo[0]);

				Set<ComparableAppInfoWrapper> sortedWrappers = new TreeSet<ComparableAppInfoWrapper>();
				for (int i = 0; i < packages.length && !abort; i++) {
					CheckBox entry = (CheckBox) LayoutInflater.from(getContext()).inflate(R.layout.appselectorcheckbox, null);
					entry.setChecked(selectedapppackages.contains("," + packages[i].packageName + ","));
					entry.setText(packages[i].loadLabel(pm) + "\n" + packages[i].packageName);
					sortedWrappers.add(new ComparableAppInfoWrapper(packages[i], entry));
				}

				//remove 'Loading apps...' info, add searchView
				post(new Runnable() {
					@Override
					public void run() {
						removeView(infoText);
						addView(searchView);
						addView(emptyResult);
					}
				});

				wrappers = sortedWrappers.toArray(new ComparableAppInfoWrapper[0]);

				for (int i = 0; (i < wrappers.length && !abort); i++) {
					Drawable icon = (wrappers[i].wrapped.loadIcon(pm));
					icon = resizeDrawable(icon, iconSizePx);
					post(new UIUpdate(wrappers[i].checkBox, icon, this));
				}
				loaded = !abort;

			} finally {
				runningUpdate = null;
				notifyAll();
			}
		}

		public void abort() {
			abort = true;
			synchronized (this) {
				while (runningUpdate != null) {
					try {
						wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	public AppSelectorView(Context context) {
		super(context);
	}

	public AppSelectorView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AppSelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public AppSelectorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr);
	}

	public void loadAppList() {

		searchView = LayoutInflater.from(getContext()).inflate(R.layout.appselectorsearch, null);
		searchView.findViewById(R.id.searchBtn).setOnClickListener(this);
		emptyResult = LayoutInflater.from(getContext()).inflate(R.layout.emptyresult, null);
		emptyResult.setVisibility(View.GONE);

		if (loaded || runningUpdate != null)
			return;

		runningUpdate = new AsyncLoader();
		new Thread(runningUpdate).start();
	}

	private Drawable resizeDrawable(Drawable drawable, int size) {
		Bitmap mutableBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(mutableBitmap);
		drawable.setBounds(0, 0, size, size);
		drawable.draw(canvas);
		return new BitmapDrawable(mutableBitmap);
	}

	public void clear() {

		//first abort a potentially running icon update
		AsyncLoader asyncLoader = runningUpdate;
		if (asyncLoader != null) {
			asyncLoader.abort();
			loaded = false;
			wrappers = null;
		}
		//store selected App Status
		selectedApps = getSelectedAppPackages();

		//clear
		wrappers = null;
		if (searchView!= null)
			searchView.setOnClickListener(null);
		this.removeAllViews();
		loaded = false;
	}


	public void setSelectedApps(String selectedApps) {
		this.selectedApps = selectedApps;
	}

	public String getSelectedAppPackages() {

		if (!loaded || runningUpdate != null)
			return selectedApps;

		ComparableAppInfoWrapper[] allwrappers = wrappers;
		String result = "";
		String delim = "";

		for (int i = 0; i < allwrappers.length; i++) {
			if (allwrappers[i].checkBox.isChecked()) {
				result = result + delim + allwrappers[i].wrapped.packageName;
				delim = ", ";
			}
		}
		return result;
	}
}
