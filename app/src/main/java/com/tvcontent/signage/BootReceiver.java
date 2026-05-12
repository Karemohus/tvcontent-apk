package com.tvcontent.signage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && (
                action.equals(Intent.ACTION_BOOT_COMPLETED) ||
                action.equals("android.intent.action.QUICKBOOT_POWERON") ||
                action.equals("com.htc.intent.action.QUICKBOOT_POWERON"))) {

            // Wait 10 seconds for system to be fully ready (wifi, etc.)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                     Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launchIntent);
            }, 10000);
        }
    }
}
