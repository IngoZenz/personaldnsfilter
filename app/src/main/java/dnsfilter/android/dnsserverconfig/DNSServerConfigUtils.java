package dnsfilter.android.dnsserverconfig;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class DNSServerConfigUtils {
    static void hideKeyboard(View view) {
        view.clearFocus();
        InputMethodManager manager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    static String formatSerializedProperties(String fallbackDNSPropertyValue) {
        return fallbackDNSPropertyValue.replace(";", "\n");
    }
}
