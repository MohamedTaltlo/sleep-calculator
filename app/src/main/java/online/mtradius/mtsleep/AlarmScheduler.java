package online.mtradius.mtsleep;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public final class AlarmScheduler {
    public static final String PREFS="mtsleep_settings";
    public static final String ALARM_CHANNEL="wake_alarm_v1";
    public static final String MONITOR_CHANNEL="sleep_audio_v1";
    public static final int ALARM_ID=8301;
    public static final int NOTIFICATION_ID=7301;
    private AlarmScheduler() {}

    public static SharedPreferences pref(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static PendingIntent receiver(Context c) {
        Intent i=new Intent(c,AlarmReceiver.class);
        i.setAction("online.mtradius.mtsleep.WAKE");
        return PendingIntent.getBroadcast(c,ALARM_ID,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void channels(Context c) {
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel a=new NotificationChannel(ALARM_CHANNEL,"منبه الاستيقاظ",NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("تنبيهات المنبه وإظهار شاشة الاستيقاظ");
        a.setSound(null,null); a.enableVibration(true);
        a.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        m.createNotificationChannel(a);
        NotificationChannel s=new NotificationChannel(MONITOR_CHANNEL,"مراقبة أصوات النوم",NotificationManager.IMPORTANCE_LOW);
        s.setDescription("الميكروفون يعمل أثناء مراقبة النوم");
        s.setSound(null,null);
        m.createNotificationChannel(s);
    }
    public static boolean canExact(Context c){
        AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT<31 || m.canScheduleExactAlarms();
    }
    public static boolean arm(Context c,long epochMillis) {
        if(epochMillis<=System.currentTimeMillis()+1000 || !canExact(c))return false;
        AlarmManager m=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent show=new Intent(c,MainActivity.class);
        PendingIntent showPending=PendingIntent.getActivity(c,33,show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        m.setAlarmClock(new AlarmManager.AlarmClockInfo(epochMillis,showPending),receiver(c));
        pref(c).edit().putLong("armed_wake",epochMillis).apply();
        return true;
    }
    /** Short isolated alarm test; does not overwrite the user's saved wake alarm. */
    public static boolean test(Context c){
        if(!canExact(c))return false;
        Intent i=new Intent(c,AlarmReceiver.class);
        i.setAction("online.mtradius.mtsleep.TEST");
        PendingIntent pi=PendingIntent.getBroadcast(c,ALARM_ID+1,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE))
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+15000L,pi);
            return true;
        }catch(SecurityException e){return false;}
    }
    public static void cancel(Context c){
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(receiver(c));
        pref(c).edit().remove("armed_wake").apply();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
    }
    public static void rearm(Context c){
        long saved=pref(c).getLong("armed_wake",0L);
        if(saved>System.currentTimeMillis()+1000L && canExact(c))arm(c,saved);
        else if(saved>0 && saved<=System.currentTimeMillis())pref(c).edit().remove("armed_wake").apply();
    }
}
