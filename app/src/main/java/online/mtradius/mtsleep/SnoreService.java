package online.mtradius.mtsleep;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public final class SnoreService extends Service {
    public static final String ACTION_STOP="online.mtradius.mtsleep.STOP_MONITOR";
    private volatile boolean running=false;
    private Thread worker;
    private AudioRecord recorder;
    private PowerManager.WakeLock wakeLock;
    private static final int RATE=16000;
    private static final int ID=8307;

    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_STICKY;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stopSelf();return START_NOT_STICKY;
        }
        running=true;
        AlarmScheduler.channels(this);
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent activity=PendingIntent.getActivity(this,61,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,SnoreService.class).setAction(ACTION_STOP);
        PendingIntent stopPending=PendingIntent.getService(this,62,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,AlarmScheduler.MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_moon).setContentTitle("MT-SLEEP · مراقبة الصوت")
            .setContentText("الميكروفون يعمل محلياً · اضغط للإعدادات")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(activity).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"إيقاف",stopPending)
            .build();
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(ID,n);
        }catch(RuntimeException e){
            running=false;stopSelf();return START_NOT_STICKY;
        }
        AlarmScheduler.pref(this).edit()
            .putLong("snore_started",System.currentTimeMillis())
            .putInt("snore_count",0).putLong("snore_last",0)
            .putBoolean("monitor_active",true).apply();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MTSLEEP:audioMonitoring");
        wakeLock.acquire(12L*60L*60L*1000L);
        worker=new Thread(this::runMonitor,"mtsleep-audio");
        worker.start();
        return START_NOT_STICKY;
    }

    private void runMonitor(){
        int count=0;
        try{
            int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("No mic buffer");
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,8192));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone unavailable");
            recorder.startRecording();
            short[] samples=new short[4096];
            double ambient=-55.0;
            int calibration=0;
            int burst=0,quiet=0;
            long previousBurst=0;
            long lastWrite=System.currentTimeMillis();
            while(running){
                int n=recorder.read(samples,0,samples.length);
                if(n<0)throw new IllegalStateException("Microphone read error: "+n);
                if(n==0)continue;
                double energy=0;int crosses=0;
                short previous=samples[0];
                for(int i=0;i<n;i++){
                    double v=samples[i]/32768.0;
                    energy+=v*v;
                    if(i>0 && ((samples[i]>=0)!=(previous>=0)))crosses++;
                    previous=samples[i];
                }
                double rms=Math.sqrt(energy/n);
                double db=20.0*Math.log10(Math.max(1E-7,rms));
                double zcr=(double)crosses/n;
                if(calibration<20){
                    if(calibration==0)ambient=db;
                    else ambient=ambient*0.90+db*0.10;
                    calibration++;
                    continue;
                }
                // A lightweight, local heuristic. Loud low/mid pitched repeated breaths
                // may be snore-like; it does NOT identify REM or clinical snoring.
                boolean event=db>ambient+8.0 && db>-45.0 && zcr>0.005 && zcr<0.21;
                if(event){
                    burst++;quiet=0;
                }else{
                    quiet++;
                    if(quiet>=2 && burst>0){
                        if(burst>=2 && burst<=16){
                            long now=System.currentTimeMillis();
                            if(previousBurst>0 && now-previousBurst>=1700 && now-previousBurst<=12000){
                                count++;
                                AlarmScheduler.pref(this).edit()
                                    .putInt("snore_count",count).putLong("snore_last",now).apply();
                            }
                            previousBurst=now;
                        }
                        burst=0;
                    }
                    ambient=ambient*0.995 + db*0.005;
                }
                long now=System.currentTimeMillis();
                if(now-lastWrite>20000){
                    AlarmScheduler.pref(this).edit().putLong("monitor_heartbeat",now).apply();
                    lastWrite=now;
                }
            }
        }catch(Exception ex){
            AlarmScheduler.pref(this).edit().putString("monitor_error",
                ex.getClass().getSimpleName()).apply();
        }finally{
            running=false;
            try{if(recorder!=null){if(recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)recorder.stop();recorder.release();}}catch(RuntimeException ignored){}
            recorder=null;
            AlarmScheduler.pref(this).edit().putBoolean("monitor_active",false)
                .putLong("snore_ended",System.currentTimeMillis()).apply();
            if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();
            stopSelf();
        }
    }

    @Override public void onDestroy(){
        running=false;
        if(worker!=null)worker.interrupt();
        if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();
        AlarmScheduler.pref(this).edit().putBoolean("monitor_active",false).apply();
        super.onDestroy();
    }
}
