package online.mtradius.mtsleep;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public final class AlarmActivity extends Activity {
    private int done=0, correct=0;
    private EditText answer;
    private TextView question,status;
    private final Random random=new Random();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD|
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        render();
    }
    private void stopRing(){
        stopService(new Intent(this,AlarmSoundService.class));
        finish();
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Ui.BG);
        LinearLayout root=Ui.column(this);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(Ui.dp(this,23),Ui.dp(this,25),Ui.dp(this,23),Ui.dp(this,30));
        scroll.addView(root);
        TextView moon=Ui.text(this,"☀",65,Ui.YELLOW,true);
        moon.setGravity(Gravity.CENTER);
        Ui.add(root,moon,14);
        TextView title=Ui.text(this,"حان وقت الاستيقاظ",29,Ui.TEXT,true);
        title.setGravity(Gravity.CENTER); Ui.add(root,title,12);
        TextView clock=Ui.text(this,new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),66,Ui.CYAN,true);
        clock.setGravity(Gravity.CENTER); Ui.add(root,clock,13);
        TextView sub=Ui.text(this,"MT-SLEEP  •  صباح الخير!",16,Ui.MUTED,false);
        sub.setGravity(Gravity.CENTER); Ui.add(root,sub,32);

        if(AlarmScheduler.pref(this).getBoolean("challenge",true)){
            LinearLayout card=Ui.card(this);
            Ui.add(card,Ui.text(this,"حل 3 مسائل لإيقاف المنبه",17,Ui.TEXT,true),12);
            status=Ui.text(this,"تم حل 0 من 3",14,Ui.MUTED,false);
            Ui.add(card,status,10);
            question=Ui.text(this,"",27,Ui.CYAN,true);
            question.setGravity(Gravity.CENTER);
            Ui.add(card,question,12);
            answer=new EditText(this);
            answer.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
            answer.setTextColor(Ui.TEXT);answer.setHintTextColor(Ui.MUTED);
            answer.setSingleLine(true);
            answer.setTextSize(24); answer.setGravity(Gravity.CENTER);
            answer.setHint("النتيجة");
            Ui.add(card,answer,14);
            Button check=Ui.button(this,"تحقق من الحل",true);
            check.setOnClickListener(v->check());
            Ui.add(card,check,4);
            Ui.add(root,card,18);
            next();
        }else{
            Button stop=Ui.button(this,"إيقاف المنبه",true);
            stop.setOnClickListener(v->stopRing());
            Ui.add(root,stop,20);
        }
        Button snooze=Ui.button(this,"غفوة 5 دقائق",false);
        snooze.setOnClickListener(v->{
            if(AlarmScheduler.arm(this,System.currentTimeMillis()+5*60000L))stopRing();
            else android.widget.Toast.makeText(this,"تعذر جدولة الغفوة: فعّل المنبهات الدقيقة",android.widget.Toast.LENGTH_LONG).show();
        });
        Ui.add(root,snooze,14);
        Button urgent=Ui.button(this,"إيقاف مباشر (اضغط مطولاً)",false);
        urgent.setOnLongClickListener(v->{stopRing();return true;});
        Ui.add(root,urgent,8);
        TextView hint=Ui.text(this,"في الحالات الطارئة يمكنك إيقاف التنبيه بالضغط المطول.",12,Ui.MUTED,false);
        hint.setGravity(Gravity.CENTER); Ui.add(root,hint,8);
        setContentView(scroll);
    }
    private void next(){
        int a=15+random.nextInt(65);
        int b=10+random.nextInt(60);
        if(random.nextBoolean()){correct=a+b;question.setText(a+" + "+b+" = ؟");}
        else{if(a<b){int t=a;a=b;b=t;}correct=a-b;question.setText(a+" − "+b+" = ؟");}
        answer.setText("");
        status.setText("تم حل "+done+" من 3");
    }
    private void check(){
        try{
            int response=Integer.parseInt(answer.getText().toString().trim());
            if(response==correct){
                done++;
                if(done>=3){stopRing();return;}
                next();
            }else{
                answer.setError("الجواب غير صحيح");
                answer.selectAll();
            }
        }catch(NumberFormatException e){answer.setError("اكتب النتيجة أولاً");}
    }
    @Override public void onBackPressed(){
        android.widget.Toast.makeText(this,"استخدم الغفوة أو الإيقاف",android.widget.Toast.LENGTH_SHORT).show();
    }
}
