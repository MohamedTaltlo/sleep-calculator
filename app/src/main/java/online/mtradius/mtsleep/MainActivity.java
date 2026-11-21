package online.mtradius.mtsleep;

import android.Manifest;
import android.app.Activity;
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
import java.util.Date;
import java.util.Locale;

/**
 * Everyday flow: wake-up goal -> three cycle-based wake choices -> arm alarm.
 * Falling asleep in 15 minutes is assumed automatically. Detailed preferences
 * remain accessible under one collapsed section.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_MIC=21;
    private static final int REQUEST_NOTIF=22;
    private static final int REQUEST_RINGTONE=23;

    private SharedPreferences prefs;
    private ScrollView activeScroll;
    private int hour=8, minute=0, choiceMode=-1;
    private long planBase;
    private boolean advanced=false;
    private boolean builtin=true, challenge=true, boost=true, monitorEnabled=true;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        prefs=AlarmScheduler.pref(this);
        AlarmScheduler.channels(this);
        hour=prefs.getInt("hour",8);
        minute=prefs.getInt("minute",0);
        builtin=prefs.getBoolean("builtin",true);
        challenge=prefs.getBoolean("challenge",true);
        boost=prefs.getBoolean("boost",true);
        monitorEnabled=prefs.getBoolean("monitor_enabled",true);
        planBase=System.currentTimeMillis();
        if(state!=null){
            choiceMode=state.getInt("choice",-1);
            advanced=state.getBoolean("advanced",false);
            planBase=state.getLong("base",planBase);
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putInt("choice",choiceMode);
        out.putBoolean("advanced",advanced);
        out.putLong("base",planBase);
    }

    @Override protected void onResume(){
        super.onResume();
        if(prefs==null)return;
        // A stale calculation must not be used as if the user were going to sleep now.
        if(System.currentTimeMillis()-planBase>120000L){
            planBase=System.currentTimeMillis();
            choiceMode=-1;
        }
        render();
    }

    private void save(){
        prefs.edit().putInt("hour",hour).putInt("minute",minute)
            .putBoolean("builtin",builtin).putBoolean("challenge",challenge)
            .putBoolean("boost",boost).putBoolean("monitor_enabled",monitorEnabled).apply();
    }

    private String fmt(long timestamp){
        return new SimpleDateFormat("HH:mm",Locale.US).format(new Date(timestamp));
    }
    private String formatMinutes(int value){
        int absolute=Math.abs(value);
        if(absolute<60)return absolute+" دقيقة";
        int h=absolute/60,m=absolute%60;
        return m==0?h+" ساعة":h+" ساعة و"+m+" دقيقة";
    }
    private void notifyUser(String value){
        Toast.makeText(this,value,Toast.LENGTH_LONG).show();
    }
    private void chooseTime(){
        new TimePickerDialog(this,(picker,h,m)->{
            hour=h;minute=m;planBase=System.currentTimeMillis();
            choiceMode=-1;save();render();
        },hour,minute,true).show();
    }
    private void twoButtons(LinearLayout parent,Button a,Button b){
        LinearLayout line=Ui.row(this);
        LinearLayout.LayoutParams pa=new LinearLayout.LayoutParams(0,Ui.dp(this,54),1);
        pa.setMargins(0,0,Ui.dp(this,5),0);
        line.addView(a,pa);
        LinearLayout.LayoutParams pb=new LinearLayout.LayoutParams(0,Ui.dp(this,54),1);
        pb.setMargins(Ui.dp(this,5),0,0,0);
        line.addView(b,pb);
        Ui.add(parent,line,9);
    }

    private void addOption(LinearLayout parent,WakeChoices.Options options,
                           int mode,int selected,String caption){
        long when=options.timeFor(mode);
        int difference=options.offsetMinutes(mode);
        int sleep=options.sleepMinutesFor(mode);
        String detail=mode==WakeChoices.EXACT?"بالوقت اللي طلبته":
            (difference<0?"قبل بـ "+formatMinutes(difference):
                "بعد بـ "+formatMinutes(difference));
        boolean chosen=mode==selected;
        String recommended=mode==options.recommended?"  ★ الأنسب حسب الحساب":"";
        String text=caption+"  "+fmt(when)+"\n"+detail+
            "  •  "+formatMinutes(sleep)+" نوم"+recommended;
        Button b=Ui.button(this,text,chosen);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this,68));
        b.setOnClickListener(v->{choiceMode=mode;render();});
        Ui.add(parent,b,10);
    }

    private void render(){
        final int previousScroll=activeScroll==null?0:activeScroll.getScrollY();
        final WakeChoices.Options options=WakeChoices.plan(planBase,hour,minute);
        final int selected=choiceMode<0?options.recommended:choiceMode;
        final long wakeAt=options.timeFor(selected);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);
        root.setPadding(Ui.dp(this,17),Ui.dp(this,25),Ui.dp(this,17),Ui.dp(this,36));
        scroll.addView(root);

        Ui.add(root,Ui.text(this,"MT-SLEEP",31,Ui.CYAN,true),2);
        Ui.add(root,Ui.text(this,"منبّه دورات النوم",14,Ui.MUTED,false),19);

        LinearLayout goal=Ui.card(this);
        Ui.add(goal,Ui.text(this,"إيمتى بدك تفيق؟",19,Ui.TEXT,true),4);
        TextView clock=Ui.text(this,String.format(Locale.US,"%02d:%02d",hour,minute),
            58,Ui.CYAN,true);
        clock.setGravity(Gravity.CENTER);
        Ui.add(goal,clock,9);
        Button change=Ui.button(this,"تغيير ساعة الاستيقاظ",false);
        change.setOnClickListener(v->chooseTime());
        Ui.add(goal,change,8);
        Ui.add(goal,Ui.text(this,
            "رح تنام هلأ · حسبنا 15 دقيقة لتغفى · كل دورة 90 دقيقة",
            13,Ui.MUTED,false),0);
        Ui.add(root,goal,0);

        LinearLayout choices=Ui.card(this);
        Ui.add(choices,Ui.text(this,"اختار الوقت اللي بناسبك",18,Ui.TEXT,true),5);
        Ui.add(choices,Ui.text(this,"الاقتراحات محسوبة من هاللحظة، مو قياس فعلي لمراحل النوم.",
            12,Ui.MUTED,false),13);
        if(options.beforeAt>0){
            addOption(choices,options,WakeChoices.BEFORE,selected,"قبل الموعد");
        }
        addOption(choices,options,WakeChoices.EXACT,selected,"بالضبط");
        addOption(choices,options,WakeChoices.AFTER,selected,"بعد الموعد");
        Ui.add(root,choices,0);

        LinearLayout armCard=Ui.card(this);
        Ui.add(armCard,Ui.text(this,"المنبّه المختار",14,Ui.MUTED,false),5);
        TextView chosenClock=Ui.text(this,fmt(wakeAt),47,Ui.CYAN,true);
        chosenClock.setGravity(Gravity.CENTER);
        Ui.add(armCard,chosenClock,7);
        String sleepNote="النوم المتوقع: "+formatMinutes(options.sleepMinutesFor(selected));
        if(options.sleepMinutesFor(selected)<360){
            sleepNote+="  •  تنبيه: نوم أقل من 6 ساعات";
        }
        TextView total=Ui.text(this,sleepNote,14,
            options.sleepMinutesFor(selected)<360?Ui.YELLOW:Ui.MUTED,false);
        total.setGravity(Gravity.CENTER);
        Ui.add(armCard,total,14);

        long armed=prefs.getLong("armed_wake",0);
        if(armed>System.currentTimeMillis()){
            Ui.add(armCard,Ui.text(this,"✓ المنبه الحالي: "+fmt(armed),
                15,Ui.YELLOW,true),11);
        }
        Button arm=Ui.button(this,"تفعيل المنبّه على "+fmt(wakeAt),true);
        arm.setOnClickListener(v->armSelected(selected));
        Ui.add(armCard,arm,9);
        if(armed>System.currentTimeMillis()){
            Button cancel=Ui.button(this,"إلغاء المنبّه الحالي",false);
            cancel.setOnClickListener(v->new AlertDialog.Builder(this)
                .setMessage("متأكد بدك تلغي المنبه؟")
                .setNegativeButton("تراجع",null)
                .setPositiveButton("إلغاء",(dialog,which)->{
                    AlarmScheduler.cancel(this);render();
                }).show());
            Ui.add(armCard,cancel,0);
        }
        Ui.add(root,armCard,2);

        Button more=Ui.button(this,advanced?"إخفاء الإعدادات الإضافية":"⚙ إعدادات إضافية",false);
        more.setOnClickListener(v->{advanced=!advanced;render();});
        Ui.add(root,more,13);

        if(advanced)renderAdvanced(root);

        Ui.add(root,Ui.text(this,
            "حساب الدورات تقديري. للأمور المهمة استخدم منبه احتياطي.",
            12,Ui.MUTED,false),0);
        setContentView(scroll);
        activeScroll=scroll;
        if(advanced && previousScroll>0){
            scroll.post(()->scroll.scrollTo(0,previousScroll));
        }
    }

    private void armSelected(int mode){
        if(System.currentTimeMillis()-planBase>120000L){
            planBase=System.currentTimeMillis();
            choiceMode=-1;
            render();
            notifyUser("مر وقت من فتح التطبيق، حدّثنا الحساب حسب هاللحظة. اختار مجدداً.");
            return;
        }
        if(!AlarmScheduler.canExact(this)){
            requestExact();
            notifyUser("فعّل المنبهات الدقيقة أولاً ثم اضغط تفعيل");
            return;
        }
        if(!notificationsOk()){
            requestNotifications();
            notifyUser("اسمح للإشعارات مشان يرن المنبه ويظهر على الشاشة");
            return;
        }
        if(!fullScreenOk()){
            requestFullScreen();
            notifyUser("اسمح بفتح المنبه فوق شاشة القفل");
            return;
        }
        WakeChoices.Options fresh=WakeChoices.plan(planBase,hour,minute);
        long when=fresh.timeFor(mode);
        if(!AlarmScheduler.arm(this,when)){
            notifyUser("تعذر حفظ موعد المنبه؛ جرّب مرة تانية");
            return;
        }
        notifyUser("تم ضبط المنبّه على "+fmt(when));
        if(monitorEnabled){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
                startMonitor();
            } else if(!prefs.getBoolean("monitor_permission_prompted",false)){
                prefs.edit().putBoolean("monitor_permission_prompted",true).apply();
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);
            }
        }
        render();
    }

    private void renderAdvanced(LinearLayout root){
        LinearLayout settings=Ui.card(this);
        Ui.add(settings,Ui.text(this,"نغمة المنبّه",18,Ui.TEXT,true),12);
        Button loud=Ui.button(this,(builtin?"✓ ":"")+"نغمة قوية",builtin);
        loud.setOnClickListener(v->{builtin=true;save();render();});
        Button phone=Ui.button(this,(!builtin?"✓ ":"")+"نغمات الهاتف",!builtin);
        phone.setOnClickListener(v->openRingtonePicker());
        twoButtons(settings,loud,phone);

        Button maxVolume=Ui.button(this,(boost?"☑ ":"☐ ")+"أعلى صوت للمنبّه",false);
        maxVolume.setOnClickListener(v->{boost=!boost;save();render();});
        Ui.add(settings,maxVolume,8);
        Button maths=Ui.button(this,(challenge?"☑ ":"☐ ")+"3 مسائل لإيقاف المنبّه",false);
        maths.setOnClickListener(v->{challenge=!challenge;save();render();});
        Ui.add(settings,maths,10);

        Button test=Ui.button(this,"اختبار الرنين بعد 15 ثانية",false);
        test.setOnClickListener(v->{
            if(!AlarmScheduler.canExact(this)){requestExact();return;}
            if(!notificationsOk()){requestNotifications();return;}
            if(!fullScreenOk()){requestFullScreen();return;}
            if(AlarmScheduler.test(this))notifyUser("اختبار خلال 15 ثانية، من دون تعديل منبهك");
            else notifyUser("تعذر جدولة اختبار الصوت");
        });
        Ui.add(settings,test,0);
        Ui.add(root,settings,0);

        LinearLayout mic=Ui.card(this);
        Ui.add(mic,Ui.text(this,"مراقبة الشخير",18,Ui.TEXT,true),8);
        Button enabled=Ui.button(this,
            (monitorEnabled?"☑ ":"☐ ")+"تشغيل الميكروفون عند تفعيل المنبّه",false);
        enabled.setOnClickListener(v->{
            monitorEnabled=!monitorEnabled;save();
            if(!monitorEnabled){
                stopService(new Intent(this,SnoreService.class));
                prefs.edit().putBoolean("monitor_active",false).apply();
            }
            render();
        });
        Ui.add(mic,enabled,10);
        boolean monitoring=prefs.getBoolean("monitor_active",false);
        Button recording=Ui.button(this,monitoring?"إيقاف مراقبة الصوت":"بدء مراقبة الصوت الآن",false);
        recording.setOnClickListener(v->{
            if(monitoring){
                stopService(new Intent(this,SnoreService.class));
                prefs.edit().putBoolean("monitor_active",false).apply();
                render();
            } else startMonitor();
        });
        Ui.add(mic,recording,8);
        Ui.add(mic,Ui.text(this,
            "مؤشرات شخير محتملة: "+prefs.getInt("snore_count",0)+
            " · الحساب محلي دون حفظ تسجيلات",14,Ui.MUTED,false),0);
        Ui.add(root,mic,0);

        LinearLayout permissions=Ui.card(this);
        Ui.add(permissions,Ui.text(this,"صلاحيات الهاتف",18,Ui.TEXT,true),11);
        addPermission(permissions,"المنبه الدقيق",AlarmScheduler.canExact(this),this::requestExact);
        addPermission(permissions,"الإشعارات",notificationsOk(),this::requestNotifications);
        addPermission(permissions,"عرض التنبيه فوق القفل",fullScreenOk(),this::requestFullScreen);
        PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        addPermission(permissions,"عدم تقييد البطارية",
            pm.isIgnoringBatteryOptimizations(getPackageName()),this::requestBattery);
        addPermission(permissions,"الميكروفون",
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED,
            ()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC));
        Ui.add(root,permissions,0);
    }

    private void addPermission(LinearLayout parent,String label,boolean granted,Runnable request){
        Button button=Ui.button(this,(granted?"✓ ":"! ")+label+
            (granted?" · مفعّل":" · اضغط للتفعيل"),false);
        button.setTextColor(granted?Ui.CYAN:Ui.YELLOW);
        if(!granted)button.setOnClickListener(v->request.run());
        Ui.add(parent,button,8);
    }
    private boolean notificationsOk(){
        return Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean fullScreenOk(){
        if(Build.VERSION.SDK_INT<34)return true;
        NotificationManager n=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        return n.canUseFullScreenIntent();
    }
    private void requestNotifications(){
        if(Build.VERSION.SDK_INT>=33)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIF);
    }
    private void requestExact(){
        if(Build.VERSION.SDK_INT>=31){
            try{
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:"+getPackageName())));
            }catch(Exception ex){
                notifyUser("فعّل المنبهات والتذكيرات من إعدادات MT-SLEEP");
            }
        }
    }
    private void requestFullScreen(){
        if(Build.VERSION.SDK_INT>=34){
            try{
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:"+getPackageName())));
            }catch(Exception ex){
                notifyUser("اسمح بعرض المنبّه على شاشة القفل من الإعدادات");
            }
        }
    }
    private void requestBattery(){
        try{
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:"+getPackageName())));
        }catch(Exception e){
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
    private void openRingtonePicker(){
        Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"اختر نغمة MT-SLEEP");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);
        String saved=prefs.getString("tone_uri","");
        if(!saved.isEmpty())
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,Uri.parse(saved));
        try{startActivityForResult(i,REQUEST_RINGTONE);}
        catch(Exception e){notifyUser("اختيار نغمات الهاتف غير متاح على هالجهاز");}
    }
    private void startMonitor(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);
            return;
        }
        if(prefs.getBoolean("monitor_active",false))return;
        try{
            prefs.edit().remove("monitor_error").apply();
            startForegroundService(new Intent(this,SnoreService.class));
            notifyUser("بدأت مراقبة أصوات النوم");
            render();
        }catch(RuntimeException ex){
            notifyUser("تعذر تشغيل الميكروفون: "+ex.getClass().getSimpleName());
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==REQUEST_MIC && results.length>0 &&
            results[0]==PackageManager.PERMISSION_GRANTED &&
            monitorEnabled && prefs.getLong("armed_wake",0)>System.currentTimeMillis()){
            startMonitor();
        } else render();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_RINGTONE && result==RESULT_OK && data!=null){
            Uri u=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            prefs.edit().putString("tone_uri",u==null?"":u.toString()).apply();
            builtin=false;save();render();
        }
    }
}
