package dnsfilter.android;

import java.util.Set;
import java.util.TreeSet;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;


public class AppSelectorView extends LinearLayout {

    private PackageManager pm = this.getContext().getPackageManager();
    Set<ComparableAppInfoWrapper> sortedWrappers = new TreeSet<ComparableAppInfoWrapper>();

    private class ComparableAppInfoWrapper implements Comparable<ComparableAppInfoWrapper> {

        private String appName =null;
        private ApplicationInfo wrapped=null;
        CheckBox checkBox = null;

        private ComparableAppInfoWrapper (ApplicationInfo wrapped, CheckBox checkBox) {
            appName = checkBox.getText().toString();
            this.wrapped = wrapped;
            this.checkBox=checkBox;
        }

        @Override
        public int compareTo(ComparableAppInfoWrapper o) {
            return appName.toUpperCase().compareTo(o.appName.toUpperCase());
        }
    }

    public AppSelectorView(Context context) {
        super(context);
        loadAppList("dnsfilter.android, android");
    }

    public AppSelectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadAppList("dnsfilter.android, android");
    }

    public AppSelectorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadAppList("dnsfilter.android, android");
    }

    public AppSelectorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        loadAppList("dnsfilter.android, android");
    }

    public void loadAppList(String selectedapppackages) {
        selectedapppackages = (","+selectedapppackages+",").replace(" ","");
        ApplicationInfo [] packages = pm.getInstalledApplications(PackageManager.GET_META_DATA).toArray(new ApplicationInfo[0]);

        for (int i = 0; i < packages.length; i++) {

            CheckBox entry = (CheckBox) LayoutInflater.from(this.getContext()).inflate(R.layout.appselectorcheckbox, null);
            entry.setChecked(selectedapppackages.contains(","+packages[i].packageName+","));
            entry.setCompoundDrawablesWithIntrinsicBounds(null, null,packages[i].loadIcon(pm),null);
            entry.setText(packages[i].loadLabel(pm)+"-"+packages[i].packageName);
            sortedWrappers.add(new ComparableAppInfoWrapper(packages[i], entry));
        }
        ComparableAppInfoWrapper[] allwrappers = sortedWrappers.toArray(new ComparableAppInfoWrapper[0]);

        for (int i = 0; i < allwrappers.length; i++) {
            this.addView(allwrappers[i].checkBox);
        }
    }


    public String getSelectedAppPackages() {

        ComparableAppInfoWrapper[] allwrappers = sortedWrappers.toArray(new ComparableAppInfoWrapper[0]);
        String result ="";
        String delim = "";

        for (int i = 0; i < allwrappers.length; i++) {
            if (allwrappers[i].checkBox.isChecked()) {
               result = result+delim+allwrappers[i].wrapped.packageName;
               delim = ", ";
            }
        }
        return result;
    }
}
