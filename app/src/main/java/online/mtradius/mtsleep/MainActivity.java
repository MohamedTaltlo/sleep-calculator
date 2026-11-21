package online.mtradius.mtsleep;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class MainActivity extends Activity {
    private static final int REQUEST_MIC=21;
    private static final int REQUEST_NOTIF=22;
    private static final int REQUEST_RINGTONE=23;
    private int targetHour=8,targetMinute=0,bedHour=0,bedMinute=0;
    private int cycleMinutes=90,onsetMinutes=15,earlyMinutes=45;
    private boolean nowMode=false,builtin=true,challenge=true,boost=true;
    private long nowBed=0L;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prefs=AlarmScheduler.pref(this);
        AlarmScheduler.channels(this);
        targetHour=prefs.getInt("hour",8);
        targetMinute=prefs.getInt("minute",0);
        cycleMinutes=prefs.getInt("cycle",90);
        onsetMinutes=prefs.getInt("onset",15);
        earlyMinutes=prefs.getInt("early",45);
        builtin=prefs.getBoolean("builtin",true);
        challenge=prefs.getBoolean("challenge",true);
        boost=prefs.getBoolean("boost",true);
        Calendar now=Calendar.getInstance();
        long bedtime=Planner.suggestBedtime(System.currentTimeMillis(),targetHour,targetMinute,5,cycleMinutes,onsetMinutes);
        Calendar b=Calendar.getInstance();b.setTimeInMillis(bedtime);
        bedHour=prefs.getInt("bed_hour",b.get(Calendar.HOUR_OF_DAY));
        bedMinute=prefs.getInt("bed_minute",b.get(Calendar.MINUTE));
        nowMode=now.get(Calendar.HOUR_OF_DAY)>=21 || now.get(Calendar.HOUR_OF_DAY)<7;
        nowBed=System.currentTimeMillis();
        render();
    }
    @Override protected void onResume(){
        super.onResume();
        if(prefs!=null)render();
    }
    private void save(){
        prefs.edit().putInt("hour",targetHour).putInt("minute",targetMinute)
            .putInt("bed_hour",bedHour).putInt("bed_minute",bedMinute)
            .putInt("cycle",cycleMinutes).putInt("onset",onsetMinutes)
            .putInt("early",earlyMinutes).putBoolean("builtin",builtin)
            .putBoolean("challenge",challenge).putBoolean("boost",boost).apply();
    }
    private String fmt(long time){return new SimpleDateFormat("HH:mm",Locale.US).format(new Date(time));}
    private String clock(int h,int m){return String.format(Locale.US,"%02d:%02d",h,m);}
    private String minutes(int min) {
        if(min<60)return min+" دقيقة";
        if(min%60==0)return (min/60)+" ساعة";
        return (min/60)+" ساعة و"+(min%60)+" دقيقة";
    }
    private long selectedBed(){
        if(nowMode)return nowBed;
        Calendar b=Calendar.getInstance();
        b.set(Calendar.HOUR_OF_DAY,bedHour);
        b.set(Calendar.MINUTE,bedMinute);
        b.set(Calendar.SECOND,0);
        b.set(Calendar.MILLISECOND,0);
        if(b.getTimeInMillis()<System.currentTimeMillis()-60000L)b.add(Calendar.DAY_OF_YEAR,1);
        return b.getTimeInMillis();
    }
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void rowButtons(LinearLayout parent,Button a,Button b){
        LinearLayout row=Ui.row(this);
        LinearLayout.LayoutParams pa=new LinearLayout.LayoutParams(0,Ui.dp(this,53),1f);
        pa.setMargins(0,0,Ui.dp(this,5),0);
        row.addView(a,pa);
        LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(0,Ui.dp(this,53),1f);
        pb.setMargins(Ui.dp(this,5),0,0,0);
        row.addView(b,pb);
        Ui.add(parent,row,9);
    }
    private void chooseTarget(){
        new TimePickerDialog(this,(picker,h,m)->{
            targetHour=h;targetMinute=m;save();render();
        },targetHour,targetMinute,true).show();
    }
    private void chooseBed(){
        new TimePickerDialog(this,(picker,h,m)->{
            bedHour=h;bedMinute=m;nowMode=false;save();render();
        },bedHour,bedMinute,true).show();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root=Ui.column(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,24),Ui.dp(this,17),Ui.dp(this,38));
        scroll.addView(root);
        Ui.add(root,Ui.text(this,"MT-SLEEP",32,Ui.CYAN,true),2);
        Ui.add(root,Ui.text(this,"ليلتك محسوبة على إيقاعك",14,Ui.MUTED,false),18);

        LinearLayout targetCard=Ui.card(this);
        Ui.add(targetCard,Ui.text(this,"الساعة اللي بدك تصحى فيها",15,Ui.MUTED,false),4);
        TextView targetTime=Ui.text(this,clock(targetHour,targetMinute),58,Ui.TEXT,true);
        targetTime.setGravity(Gravity.CENTER);
        Ui.add(targetCard,targetTime,3);
        Button targetPick=Ui.button(this,"تغيير موعد الاستيقاظ",false);
        targetPick.setOnClickListener(v->chooseTarget());
        Ui.add(targetCard,targetPick,0);
        Ui.add(root,targetCard,0);

        LinearLayout bedCard=Ui.card(this);
        Ui.add(bedCard,Ui.text(this,"متى رح تدخل بالفراش؟",17,Ui.TEXT,true),9);
        TextView bedLabel=Ui.text(this,nowMode?"الآن · "+fmt(nowBed):
            "الساعة "+clock(bedHour,bedMinute),20,Ui.CYAN,true);
        Ui.add(bedCard,bedLabel,12);
        Button now=Ui.button(this,nowMode?"✓ رح نام الآن":"رح نام الآن",nowMode);
        now.setOnClickListener(v->{nowMode=true;nowBed=System.currentTimeMillis();render();});
        Button manual=Ui.button(this,nowMode?"تحديد ساعة النوم":"✓ وقت نوم محدد",!nowMode);
        manual.setOnClickListener(v->chooseBed());
        rowButtons(bedCard,now,manual);
        Ui.add(bedCard,Ui.text(this,"نحسب بداية النوم بعد "+onsetMinutes+" دقيقة تقريباً من دخول الفراش.",13,Ui.MUTED,false),0);
        Ui.add(root,bedCard,0);

        Planner.Plan plan=Planner.calculate(selectedBed(),targetHour,targetMinute,
            cycleMinutes,onsetMinutes,earlyMinutes,0);
        LinearLayout result=Ui.card(this);
        Ui.add(result,Ui.text(this,"الرنين الأنسب وفق الدورات",15,Ui.MUTED,false),5);
        TextView wake=Ui.text(this,fmt(plan.wakeMillis),57,Ui.CYAN,true);
        wake.setGravity(Gravity.CENTER);
        Ui.add(result,wake,4);
        String variance=plan.cycleAligned?
            (plan.differenceMinutes==0?"على الموعد المطلوب بالضبط":
             "قبل موعدك بـ "+(-plan.differenceMinutes)+" دقيقة"):
             "ما في نهاية دورة مناسبة ضمن الهامش؛ حافظنا على موعدك";
        TextView diff=Ui.text(this,variance,14,plan.cycleAligned?Ui.YELLOW:Ui.MUTED,true);
        diff.setGravity(Gravity.CENTER);
        Ui.add(result,diff,18);
        LinearLayout stats=Ui.row(this);
        stats.setGravity(Gravity.CENTER);
        TextView cycles=Ui.text(this,plan.completeCycles+" دورات",19,Ui.TEXT,true);
        TextView length=Ui.text(this,"  •  "+minutes(plan.sleepMinutes),17,Ui.MUTED,false);
        stats.addView(cycles);stats.addView(length);
        Ui.add(result,stats,11);
        if(!plan.cycleAligned)Ui.add(result,Ui.text(this,
            "النتيجة تقديرية: الرنين ثابت على الموعد، وليس على نهاية دورة.",13,Ui.MUTED,false),7);
        if(plan.sleepMinutes<360)Ui.add(result,Ui.text(this,
            "⚠ مدة النوم قصيرة. انتظام النوم والنوم الكافي أهم من مطابقة الدورات.",14,Ui.RED,true),12);
        else if(plan.sleepMinutes>660)Ui.add(result,Ui.text(this,
            "مدة النوم طويلة نسبياً. تأكد أنك اخترت وقت دخول الفراش الصحيح.",13,Ui.YELLOW,false),10);
        Ui.add(result,Ui.text(this,"الاستيقاظ محسوب رياضياً؛ الهاتف لا يقيس مراحل النوم الحقيقية.",12,Ui.MUTED,false),15);

        long armed=prefs.getLong("armed_wake",0);
        if(armed>System.currentTimeMillis()){
            Ui.add(result,Ui.text(this,"✓ المنبه المفعّل: "+fmt(armed),15,Ui.YELLOW,true),8);
        }
        Button arm=Ui.button(this,"تفعيل المنبه على "+fmt(plan.wakeMillis),true);
        arm.setOnClickListener(v->{
            if(!AlarmScheduler.canExact(this)){
                requestExact();
                toast("اسمح بالتنبيهات الدقيقة، ثم ارجع وفعل المنبه");
                return;
            }
            if(!notificationsOk()){
                requestNotifications();
                toast("فعّل الإشعارات أولاً لضمان ظهور شاشة التنبيه");
                return;
            }
            if(!fullScreenOk()){
                requestFullScreen();
                toast("فعّل التنبيه بملء الشاشة ثم ارجع وفعل المنبه");
                return;
            }
            if(AlarmScheduler.arm(this,plan.wakeMillis)){
                toast("تم ضبط المنبه على "+fmt(plan.wakeMillis));
                render();
            }else toast("تعذّر ضبط المنبه. تأكد أن الوقت بالمستقبل");
        });
        Ui.add(result,arm,10);
        if(armed>System.currentTimeMillis()){
            Button cancel=Ui.button(this,"إلغاء المنبه الحالي",false);
            cancel.setOnClickListener(v->new AlertDialog.Builder(this)
                .setMessage("هل تريد إلغاء المنبه المحفوظ؟")
                .setNegativeButton("تراجع",null)
                .setPositiveButton("إلغاء", (d,which)->{AlarmScheduler.cancel(this);render();}).show());
            Ui.add(result,cancel,0);
        }
        Ui.add(root,result,0);

        LinearLayout recommended=Ui.card(this);
        Ui.add(recommended,Ui.text(this,"متى تنام لتفيق "+clock(targetHour,targetMinute)+"؟",17,Ui.TEXT,true),8);
        long nowEpoch=System.currentTimeMillis();
        for(int n=6;n>=4;n--){
            long bedtime=Planner.suggestBedtime(nowEpoch,targetHour,targetMinute,n,cycleMinutes,onsetMinutes);
            String line=n+" دورات  •  "+fmt(bedtime)+"  •  "+minutes(n*cycleMinutes);
            if(bedtime<nowEpoch)line+="  (فات الوقت)";
            TextView t=Ui.text(this,line,15,n==5?Ui.CYAN:Ui.MUTED,n==5);
            Ui.add(recommended,t,9);
        }
        Ui.add(recommended,Ui.text(this,"هذه أوقات دخول الفراش، وتشمل مدة الاستغراق بالنوم.",12,Ui.MUTED,false),0);
        Ui.add(root,recommended,0);

        LinearLayout settingsCard=Ui.card(this);
        Ui.add(settingsCard,Ui.text(this,"ضبط الحساب",18,Ui.TEXT,true),14);
        adjustment(settingsCard,"مدة دورة النوم",cycleMinutes," دقيقة",60,120,5,value->{cycleMinutes=value;save();render();});
        adjustment(settingsCard,"وقت الاستغراق بالنوم",onsetMinutes," دقيقة",0,60,5,value->{onsetMinutes=value;save();render();});
        adjustment(settingsCard,"التبكير المسموح عن الموعد",earlyMinutes," دقيقة",0,90,5,value->{earlyMinutes=value;save();render();});
        Ui.add(root,settingsCard,0);

        LinearLayout toneCard=Ui.card(this);
        Ui.add(toneCard,Ui.text(this,"صوت المنبه",18,Ui.TEXT,true),12);
        Button harsh=Ui.button(this,(builtin?"✓ ":"")+"نغمة قوية من MT-SLEEP",builtin);
        harsh.setOnClickListener(v->{builtin=true;save();render();});
        Button system=Ui.button(this,(!builtin?"✓ ":"")+"نغمات الهاتف",!builtin);
        system.setOnClickListener(v->openRingtonePicker());
        rowButtons(toneCard,harsh,system);
        Button loud=Ui.button(this,(boost?"☑ ":"☐ ")+"رفع صوت المنبه لأعلى درجة عند الرنين",false);
        loud.setOnClickListener(v->{boost=!boost;save();render();});
        Ui.add(toneCard,loud,10);
        Button task=Ui.button(this,(challenge?"☑ ":"☐ ")+"حل 3 مسائل حسابية لإيقاف المنبه",false);
        task.setOnClickListener(v->{challenge=!challenge;save();render();});
        Ui.add(toneCard,task,10);
        Button testAlarm=Ui.button(this,"اختبار صوت المنبه بعد 15 ثانية",false);
        testAlarm.setOnClickListener(v->{
            if(!AlarmScheduler.canExact(this)){requestExact();return;}
            if(!notificationsOk()){requestNotifications();return;}
            if(!fullScreenOk()){requestFullScreen();return;}
            if(AlarmScheduler.test(this))toast("اختبار المنبه بعد 15 ثانية؛ لن يغيّر موعد منبهك المحفوظ");
            else toast("تعذر جدولة الاختبار");
        });
        Ui.add(toneCard,testAlarm,0);
        Ui.add(root,toneCard,0);

        LinearLayout micCard=Ui.card(this);
        Ui.add(micCard,Ui.text(this,"مراقبة أصوات الشخير",18,Ui.TEXT,true),6);
        Ui.add(micCard,Ui.text(this,
            "تقدير أولي للأصوات المتكررة فقط، دون تسجيل صوتي أو اتصال بالإنترنت. لا يحدد مراحل النوم.",13,Ui.MUTED,false),13);
        boolean monitoring=prefs.getBoolean("monitor_active",false);
        Button monitor=Ui.button(this,monitoring?"إيقاف مراقبة الميكروفون":"بدء مراقبة أصوات الليل",!monitoring);
        monitor.setOnClickListener(v->{if(monitoring){
            stopService(new Intent(this,SnoreService.class));prefs.edit().putBoolean("monitor_active",false).apply();render();
        }else startMonitor();});
        Ui.add(micCard,monitor,12);
        long started=prefs.getLong("snore_started",0);
        int count=prefs.getInt("snore_count",0);
        Ui.add(micCard,Ui.text(this,"مؤشرات صوتية محتملة: "+count,16,Ui.YELLOW,true),7);
        if(started>0)Ui.add(micCard,Ui.text(this,
            "آخر جلسة بدأت: "+new SimpleDateFormat("dd/MM HH:mm",Locale.US).format(new Date(started)),13,Ui.MUTED,false),7);
        String error=prefs.getString("monitor_error","");
        if(!error.isEmpty())Ui.add(micCard,Ui.text(this,"ملاحظة للمراقبة: "+error,12,Ui.RED,false),0);
        Ui.add(root,micCard,0);

        LinearLayout permissions=Ui.card(this);
        Ui.add(permissions,Ui.text(this,"صلاحيات ضرورية لعمل التطبيق",18,Ui.TEXT,true),11);
        addPermission(permissions,"المنبه الدقيق",AlarmScheduler.canExact(this),this::requestExact);
        addPermission(permissions,"إشعارات الهاتف",notificationsOk(),this::requestNotifications);
        addPermission(permissions,"المنبه بملء الشاشة",fullScreenOk(),this::requestFullScreen);
        PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        boolean battery=pm.isIgnoringBatteryOptimizations(getPackageName());
        addPermission(permissions,"عدم تقييد البطارية",battery,this::requestBattery);
        addPermission(permissions,"إذن الميكروفون",checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
            ()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC));
        Ui.add(root,permissions,0);

        Ui.add(root,Ui.text(this,
            "MT-SLEEP v1.0  •  دون إنترنت  •  حافظ على منبه احتياطي للمواعيد المهمة",12,Ui.MUTED,false),0);
        setContentView(scroll);
    }
    private void adjustment(LinearLayout parent,String name,int value,String suffix,
                            int min,int max,int step,IntConsumer change){
        Ui.add(parent,Ui.text(this,name,14,Ui.MUTED,false),4);
        LinearLayout r=Ui.row(this);
        Button minus=Ui.button(this,"−",false);
        Button plus=Ui.button(this,"+",false);
        TextView val=Ui.text(this,value+suffix,18,Ui.TEXT,true);
        val.setGravity(Gravity.CENTER);
        r.addView(minus,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        r.addView(val,new LinearLayout.LayoutParams(0,Ui.dp(this,48),3));
        r.addView(plus,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        minus.setEnabled(value>min);plus.setEnabled(value<max);
        minus.setOnClickListener(v->change.accept(Math.max(min,value-step)));
        plus.setOnClickListener(v->change.accept(Math.min(max,value+step)));
        Ui.add(parent,r,13);
    }
    private void addPermission(LinearLayout parent,String name,boolean granted,Runnable request){
        Button b=Ui.button(this,(granted?"✓ ":"! ")+name+(granted?" · مفعّل":" · اضغط للتفعيل"),false);
        b.setTextColor(granted?Ui.CYAN:Ui.YELLOW);
        if(!granted)b.setOnClickListener(v->request.run());
        Ui.add(parent,b,8);
    }
    private boolean notificationsOk(){
        if(Build.VERSION.SDK_INT<33)return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean fullScreenOk(){
        if(Build.VERSION.SDK_INT<34)return true;
        NotificationManager m=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        return m.canUseFullScreenIntent();
    }
    private void requestNotifications(){
        if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIF);
    }
    private void requestExact(){
        if(Build.VERSION.SDK_INT>=31){
            try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:"+getPackageName())));}
            catch(Exception ex){toast("افتح الإعدادات > المنبهات والتذكيرات > MT-SLEEP");}
        }
    }
    private void requestFullScreen(){
        if(Build.VERSION.SDK_INT>=34){
            try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:"+getPackageName())));}
            catch(Exception ex){toast("افتح إعدادات التطبيق واسمح بالتنبيه بملء الشاشة");}
        }
    }
    private void requestBattery(){
        try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:"+getPackageName())));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
    }
    private void openRingtonePicker(){
        Intent intent=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"اختر نغمة MT-SLEEP");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
        String saved=prefs.getString("tone_uri","");
        if(!saved.isEmpty())intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(saved));
        try{startActivityForResult(intent,REQUEST_RINGTONE);}
        catch(Exception e){toast("الجهاز لا يدعم اختيار النغمة من هذه الشاشة");}
    }
    private void startMonitor(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);return;
        }
        try{
            prefs.edit().remove("monitor_error").apply();
            Intent i=new Intent(this,SnoreService.class);
            startForegroundService(i);
            toast("بدأت مراقبة الأصوات محلياً؛ اترك الهاتف على الشاحن");
            render();
        }catch(RuntimeException ex){toast("تعذر تشغيل الميكروفون: "+ex.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
        super.onRequestPermissionsResult(req,permissions,results);
        if(req==REQUEST_MIC && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startMonitor();
        else render();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_RINGTONE && result==RESULT_OK && data!=null){
            Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            prefs.edit().putString("tone_uri",uri==null?"":uri.toString()).apply();
            builtin=false;save();render();
        }
    }
}
