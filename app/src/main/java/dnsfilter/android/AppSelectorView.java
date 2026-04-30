package dnsfilter.android;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Set;
import java.util.TreeSet;

import util.ExecutionEnvironment;
import util.Logger;


public class AppSelectorView extends LinearLayout implements View.OnClickListener, TextWatcher {

	private PackageManager pm = this.getContext().getPackageManager();
	private boolean loaded = false;
	private String selectedApps = "";
	private static int iconSizePx = 0;
	private static float iconSizeDP = 96;
	private AsyncLoader runningUpdate = null;

	private ComparableAppInfoWrapper[] wrappers = null;
	private View searchView;
	private EditText searchStringField;
	private View emptyResult;

	@Override
	public void onClick(View v) {
		if (!loaded || runningUpdate != null)
			return ;

		String searchStr = searchStringField.getText().toString().toUpperCase().trim();

		ComparableAppInfoWrapper[] allwrappers = wrappers;
		int count = 0;
		emptyResult.setVisibility(View.GONE);

		for (int i = 0; i < allwrappers.length; i++) {
			boolean visible = allwrappers[i].appString.indexOf(searchStr) != -1;
			if (visible)
				allwrappers[i].checkBox.setVisibility(View.VISIBLE);
			else
				allwrappers[i].checkBox.setVisibility(View.GONE);

			if (visible)
				count++;
		}
		if (count == 0)
			emptyResult.setVisibility(View.VISIBLE);
		if (ExecutionEnvironment.getEnvironment().debug())
			Logger.getLogger().logLine("Found: "+count+" apps!");
	}

	@Override
	public void afterTextChanged(Editable s) {
		onClick(searchStringField);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	private class ComparableAppInfoWrapper implements Comparable<ComparableAppInfoWrapper> {
		
		private ApplicationInfo wrapped = null;
		CheckBox checkBox = null;
		private String appString = null;

		private ComparableAppInfoWrapper(ApplicationInfo wrapped, CheckBox checkBox, String appString) {
			this.appString = appString.toUpperCase();
			this.wrapped = wrapped;
			this.checkBox = checkBox;
		}

		@Override
		public int compareTo(ComparableAppInfoWrapper o) {
			return appString.compareTo(o.appString);
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
				final TextView infoText = new TextView(getContext());

				int uimode = getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
				if (uimode == Configuration.UI_MODE_NIGHT_YES)
					infoText.setTextColor(Color.WHITE);
				else infoText.setTextColor(Color.BLACK);

				infoText.setText("Loading apps...");

				post(new Runnable() {
					@Override
					public void run() {
						addView(infoText);
					}
				});

				String selectedapppackages = ("," + selectedApps + ",").replace(" ", "");

				ApplicationInfo[] packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
						.toArray(new ApplicationInfo[0]);

				Set<ComparableAppInfoWrapper> sortedWrappers = new TreeSet<>();

				for (int i = 0; i < packages.length && !abort; i++) {
					CheckBox entry = (CheckBox) LayoutInflater.from(getContext())
							.inflate(R.layout.appselectorcheckbox, null);
					entry.setChecked(selectedapppackages.contains("," + packages[i].packageName + ","));

					String appName = packages[i].loadLabel(pm).toString();
					String pkgName = packages[i].packageName;

					String shortApp = appName.length() > 32 ? appName.substring(0, 32) + "…" : appName;
					String shortPkg = pkgName.length() > 38 ? pkgName.substring(0, 38) + "…" : pkgName;
					String combined = shortApp + "\n" + shortPkg;
					android.text.SpannableString spannable = new android.text.SpannableString(combined);

					int start = shortApp.length() + 1;
					int end = combined.length();
					spannable.setSpan(new android.text.style.RelativeSizeSpan(0.85f), start, end, 0);

					entry.setText(spannable);

					sortedWrappers.add(new ComparableAppInfoWrapper(packages[i], entry, appName+":"+pkgName));
				}

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
		searchStringField = (EditText) searchView.findViewById(R.id.searchString);
		searchStringField.addTextChangedListener(this);
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

		AsyncLoader asyncLoader = runningUpdate;
		if (asyncLoader != null) {
			asyncLoader.abort();
			loaded = false;
			wrappers = null;
		}

		selectedApps = getSelectedAppPackages();

		wrappers = null;
		if (searchView!= null) {
			searchView.findViewById(R.id.searchBtn).setOnClickListener(null);
			searchStringField.removeTextChangedListener(this);
		}
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
