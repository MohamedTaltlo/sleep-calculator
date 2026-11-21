package online.mtradius.mtsleep;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!"online.mtradius.mtsleep.WAKE".equals(intent.getAction()))return;
        AlarmScheduler.pref(context).edit().remove("armed_wake").apply();
        context.stopService(new Intent(context,SnoreService.class));
        AlarmScheduler.channels(context);
        Intent ring=new Intent(context,AlarmSoundService.class);
        try {
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(ring);
            else context.startService(ring);
        }catch(RuntimeException ex) {
            // Fallback: show full-screen alarm notification even if foreground playback is blocked.
            Intent show=new Intent(context,AlarmActivity.class);
            show.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi=PendingIntent.getActivity(context,44,show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(context,AlarmScheduler.ALARM_CHANNEL)
                .setSmallIcon(R.drawable.ic_moon).setContentTitle("MT-SLEEP · وقت الاستيقاظ")
                .setContentText("اضغط لفتح المنبه").setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
                .setPriority(Notification.PRIORITY_MAX).setContentIntent(pi)
                .setFullScreenIntent(pi,true).build();
            try {((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(AlarmScheduler.NOTIFICATION_ID,n);}
            catch(SecurityException ignored){}
        }
    }
}
