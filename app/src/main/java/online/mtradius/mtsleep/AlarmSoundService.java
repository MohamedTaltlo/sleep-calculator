package online.mtradius.mtsleep;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

public final class AlarmSoundService extends Service {
    public static final String ACTION_STOP="online.mtradius.mtsleep.STOP_RING";
    private MediaPlayer player;
    private AudioTrack track;
    private Vibrator vibrator;
    private AudioManager audio;
    private int previousVolume=-1;
    private PowerManager.WakeLock wakeLock;
    private boolean playing=false;

    @Override public IBinder onBind(Intent i){return null;}

    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null && ACTION_STOP.equals(i.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        if(playing)return START_STICKY;
        playing=true;
        AlarmScheduler.channels(this);
        Intent open=new Intent(this,AlarmActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent screen=PendingIntent.getActivity(this,44,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,AlarmSoundService.class).setAction(ACTION_STOP);
        PendingIntent stopAction=PendingIntent.getService(this,45,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,AlarmScheduler.ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle("MT-SLEEP · حان وقت الاستيقاظ")
            .setContentText("المنبه يعمل الآن")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MAX)
            .setContentIntent(screen)
            .setFullScreenIntent(screen,true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"إيقاف",stopAction)
            .build();
        try {
            if(Build.VERSION.SDK_INT>=29)startForeground(AlarmScheduler.NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(AlarmScheduler.NOTIFICATION_ID,notification);
        }catch(RuntimeException ex) {
            playing=false; stopSelf(); return START_NOT_STICKY;
        }
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MTSLEEP:alarmRinging");
        wakeLock.acquire(30L*60L*1000L);
        startAudio();
        return START_STICKY;
    }

    private void startAudio(){
        audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if(AlarmScheduler.pref(this).getBoolean("boost",true)){
            try{
                previousVolume=audio.getStreamVolume(AudioManager.STREAM_ALARM);
                audio.setStreamVolume(AudioManager.STREAM_ALARM,audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),0);
            }catch(RuntimeException ignored){}
        }
        vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if(vibrator!=null && vibrator.hasVibrator()){
            try{vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,750,250,750,350,900},0));}
            catch(RuntimeException ignored){}
        }
        if(!AlarmScheduler.pref(this).getBoolean("builtin",true)){
            String selected=AlarmScheduler.pref(this).getString("tone_uri","");
            Uri uri=selected.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):Uri.parse(selected);
            if(playUri(uri))return;
            if(playUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)))return;
        }
        startSynth();
    }

    private boolean playUri(Uri uri){
        if(uri==null)return false;
        MediaPlayer temp=new MediaPlayer();
        try{
            temp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            temp.setDataSource(this,uri); temp.prepare(); temp.setLooping(true); temp.start();
            player=temp; return true;
        }catch(Exception ex){try{temp.release();}catch(Exception ignored){} return false;}
    }

    private void startSynth(){
        // A looping distinctive two-tone siren with short silent periods, PCM-16 mono.
        try{
            final int sr=22050, frames=sr*2;
            byte[] data=new byte[frames*2];
            for(int i=0;i<frames;i++){
                double t=(double)i/sr;
                double amp=(t%0.70<0.53)?0.75:0.0;
                double chirp=980.0 + 400.0*(t%0.53)/0.53;
                short sample=(short)(Math.sin(2.0*Math.PI*chirp*t)*amp*Short.MAX_VALUE);
                data[2*i]=(byte)(sample&255);
                data[2*i+1]=(byte)((sample>>8)&255);
            }
            track=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.length)
                .setTransferMode(AudioTrack.MODE_STATIC).build();
            track.write(data,0,data.length);
            track.setLoopPoints(0,frames,-1);
            track.play();
        }catch(RuntimeException ex){if(track!=null){try{track.release();}catch(Exception ignored){}}track=null;}
    }

    @Override public void onDestroy(){
        playing=false;
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
        if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();track=null;}
        if(vibrator!=null)vibrator.cancel();
        if(audio!=null && previousVolume>=0){
            try{audio.setStreamVolume(AudioManager.STREAM_ALARM,previousVolume,0);}catch(Exception ignored){}
        }
        if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();
        ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).cancel(AlarmScheduler.NOTIFICATION_ID);
        super.onDestroy();
    }
}
