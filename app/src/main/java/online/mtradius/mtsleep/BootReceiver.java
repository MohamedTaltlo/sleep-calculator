package online.mtradius.mtsleep;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        AlarmScheduler.rearm(c);
        // A microphone service MUST NOT be started from BOOT_COMPLETED on Android 14+.
    }
}
