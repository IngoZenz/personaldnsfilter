package dnsfilter.android;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DNSProxyActivity_Test extends Activity implements View.OnClickListener {

    protected static ImageButton reset_button;
    protected static ImageButton settings_button;
    protected static LinearLayout power_button_layout;
    protected static ImageButton power_button;
    protected static TextView power_status;
    protected static Boolean isPowerOn = true;
    protected static TextView live_log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_main_activity);

        reset_button = (ImageButton) findViewById(R.id.reset_button);
        reset_button.setOnClickListener(this);
        settings_button = (ImageButton) findViewById(R.id.settings_button);
        settings_button.setOnClickListener(this);
        power_button_layout = (LinearLayout) findViewById(R.id.power_icon_layout);
        power_button = (ImageButton) findViewById(R.id.power_button);
        power_button.setOnClickListener(this);
        power_status = (TextView) findViewById(R.id.power_status);
        live_log = (TextView) findViewById(R.id.live_log);
        live_log.setMovementMethod(new ScrollingMovementMethod());

    }

    @Override
    public void onClick(View v) {


        switch (v.getId()) {


            case R.id.reset_button:
                Toast.makeText(this, "reset", Toast.LENGTH_LONG).show();
                break;

            case R.id.settings_button:
                Toast.makeText(this, "settings", Toast.LENGTH_LONG).show();
                break;

            case R.id.power_button:
                if (isPowerOn) {
                    isPowerOn = false;
                    power_button_layout.setBackgroundResource(R.drawable.power_button_border);
                    power_button.setImageResource(R.drawable.power_icon);
                    power_status.setText("ON");
                } else {
                    isPowerOn = true;
                    power_button_layout.setBackgroundResource(R.drawable.power_button_border_white);
                    power_button.setImageResource(R.drawable.power_icon_white);
                    power_status.setText("OFF");
                }
                break;


        }

    }
}
