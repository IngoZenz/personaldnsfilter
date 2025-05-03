package dnsfilter.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.io.IOException;

import dnsfilter.DNSFilterManager;
import util.Logger;

/**
 * Quick Settings Tile Service for toggling DNS filtering
 */
public class DNSFilterTileService extends TileService {

    private static final String TAG = "DNSFilterTileService";
    
    // Instance reference for static access
    private static DNSFilterTileService INSTANCE;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile added");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        
        // Toggle filtering state
        try {
            if (DNSFilterService.INSTANCE != null) {
                DNSFilterService.INSTANCE.pause_resume();
                updateTile();
            } else {
                // Service isn't running, we need to start it
                Intent startIntent = new Intent(this, DNSProxyActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityAndCollapse(startIntent);
            }
        } catch (IOException e) {
            Logger.getLogger().logLine("Error toggling DNS filtering state: " + e.getMessage());
            Log.e(TAG, "Error toggling DNS filtering state", e);
        }
    }

    /**
     * Update the tile status from outside the service
     * This should be called whenever the filtering state changes
     */
    public static void requestTileUpdate(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ComponentName componentName = new ComponentName(context, DNSFilterTileService.class);
                TileService.requestListeningState(context, componentName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to request tile update", e);
            }
        } else if (INSTANCE != null) {
            INSTANCE.updateTile();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile != null) {
            boolean active = false;
            
            // Check if service is running and get filtering status
            if (DNSFilterService.INSTANCE != null) {
                active = DNSFilterService.INSTANCE.isFilterActive();
            }

            // Update tile state and icon
            if (active) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.setIcon(Icon.createWithResource(this, R.drawable.icon));
                tile.setLabel(getResources().getString(R.string.notificationActive));
            } else {
                tile.setState(Tile.STATE_INACTIVE); 
                tile.setIcon(Icon.createWithResource(this, R.drawable.icon_disabled));
                tile.setLabel(getResources().getString(R.string.notificationPaused));
            }
            
            // Update the tile
            tile.updateTile();
        }
    }
} 