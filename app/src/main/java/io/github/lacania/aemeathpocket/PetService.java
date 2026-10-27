package io.github.lacania.aemeathpocket;

import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.Window;
import android.view.ViewGroup;
import android.util.DisplayMetrics;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.Random;

/** 独立前台悬浮窗：Activity 关闭后继续运行，用户退出或权限撤销时清理所有窗口。 */
public final class PetService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String STOP="pet.stop", PLAY="pet.play", PASS="pet.pass", HOME="pet.home";
    public static volatile boolean running;
    private static final String CHANNEL="pet_presence";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private SharedPreferences prefs;
    private WindowManager windows;
    private WindowManager.LayoutParams layout;
    private PetView pet;
    private Dialog menu;
    private final PetGesture gesture=new PetGesture();
    private final ThrowMotion fling=new ThrowMotion();
    private final ThrowTrail trail=new ThrowTrail();
    private DisplayManager displays;
    private boolean receiverRegistered,paused,pressed,dragging;
    private int minX,minY,maxX,maxY;
    private float downX,downY;
    private int originX,originY,direction=1;
    private long nextAction,moveUntil,manualUntil,lastTick;
    private double motionX;
    private final String[] randomActions={"wave","jump","ponder","groove","happy","blush","look","daydream","nap","peek"};
    private final Runnable tick=new Runnable(){public void run(){
        if(pet==null||paused)return;
        if(!Settings.canDrawOverlays(PetService.this)){stopSelf();return;}
        long now=SystemClock.uptimeMillis();
        double dt=Math.min(.1,Math.max(0,(now-lastTick)/1000.0));lastTick=now;
        if(fling.isActive()){
            boolean moving=fling.step(dt);layout.x=fling.x();layout.y=fling.y();updateWindow();
            if(!moving){savePosition();pet.play("idle",true);nextAction=now+5000;}
            handler.postDelayed(this,16);return;
        }
        if(!pressed&&menu==null&&manualUntil!=0&&now>=manualUntil){manualUntil=0;pet.play("idle",true);}
        if(!pressed&&menu==null&&now>=manualUntil){
            if(moveUntil>now){
                motionX+=direction*dp(38)*(float)dt;
                int wanted=(int)Math.round(motionX);
                layout.x=Motion.clamp(wanted,minX,maxX);
                if(layout.x!=wanted){direction=-direction;motionX=layout.x;pet.play(direction>0?"walk-right":"walk-left",true);}
                updateWindow();
            }else if(moveUntil!=0){
                moveUntil=0;savePosition();pet.play("idle",true);nextAction=now+2500+random.nextInt(4000);
            }else if(now>=nextAction){
                nextAction=now+5000+random.nextInt(5000);
                if(prefs.getBoolean("roam",true)&&maxX>minX&&random.nextInt(3)==0){
                    direction=random.nextBoolean()?1:-1;motionX=layout.x;moveUntil=now+1600+random.nextInt(2000);
                    pet.play(direction>0?"walk-right":"walk-left",true);
                }else{
                    String id=randomActions[random.nextInt(randomActions.length)];
                    PetAssets.Action action=PetAssets.get(PetService.this).action(id);
                    manualUntil=now+Math.max(1800,action.total*2);pet.play(id,true);
                }
            }
        }
        handler.postDelayed(this,50);
    }};
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){if(pet!=null){moveUntil=0;applyLayout();}}
    };
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        public void onReceive(Context context,Intent intent){updateVisibility();}
    };
    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences("pet",MODE_PRIVATE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"桌宠运行",NotificationManager.IMPORTANCE_LOW));
        prefs.registerOnSharedPreferenceChangeListener(this);
        displays=getSystemService(DisplayManager.class);displays.registerDisplayListener(displayListener,handler);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenReceiver,filter);
        receiverRegistered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(STOP.equals(action)){
            prefs.edit().putBoolean("enabled",false).apply();stopSelf();return START_NOT_STICKY;
        }
        if(!prefs.getBoolean("enabled",false)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        startForeground(1,notification());
        try{
            if(pet==null){
                pet=new PetView(this);
                layout=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
                layout.gravity=Gravity.TOP|Gravity.LEFT;
                if(Build.VERSION.SDK_INT>=30)layout.setFitInsetsTypes(0);
                pet.setOnTouchListener(this::touch);
                pet.setOnLongClickListener(view->{showMenu();return true;});
                applyLayout();windows.addView(pet,layout);running=true;
                nextAction=SystemClock.uptimeMillis()+3500;
                updateVisibility();
            }
            if(PLAY.equals(action)){play(intent.getStringExtra("animation"));}
            else if(PASS.equals(action)){prefs.edit().putBoolean("pass",!prefs.getBoolean("pass",false)).apply();}
            else if(HOME.equals(action)){cancelThrow();prefs.edit().putFloat("x",.85f).putFloat("y",.65f).apply();moveUntil=0;applyLayout();}
        }catch(RuntimeException error){
            Toast.makeText(this,"桌宠暂时无法显示，请重新检查悬浮窗权限",Toast.LENGTH_LONG).show();stopSelf();return START_NOT_STICKY;
        }
        return START_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PetService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pass=PendingIntent.getService(this,2,new Intent(this,PetService.class).setAction(PASS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.pet_icon).setContentTitle("爱弥斯在陪着你")
            .setContentText(prefs.getBoolean("pass",false)?"点击穿透已开启，可从通知恢复触控":"原地长按后松手，选择动作或退出")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null,prefs.getBoolean("pass",false)?"恢复触控":"点击穿透",pass).build())
            .addAction(new Notification.Action.Builder(null,"收起桌宠",stop).build()).build();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void applyLayout(){
        if(pet==null||layout==null)return;
        cancelThrow();
        Rect area;int left=0,top=0,right=0,bottom=0;
        if(Build.VERSION.SDK_INT>=30){
            area=windows.getCurrentWindowMetrics().getBounds();
            Insets insets=windows.getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            left=insets.left;top=insets.top;right=insets.right;bottom=insets.bottom;
        }else{
            DisplayMetrics metrics=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(metrics);area=new Rect(0,0,metrics.widthPixels,metrics.heightPixels);
            int status=getResources().getIdentifier("status_bar_height","dimen","android"),nav=getResources().getIdentifier("navigation_bar_height","dimen","android");
            if(status>0)top=getResources().getDimensionPixelSize(status);if(nav>0)bottom=getResources().getDimensionPixelSize(nav);
        }
        minX=left+dp(4);minY=top+dp(4);
        int safeW=Math.max(1,area.width()-left-right-dp(8)),safeH=Math.max(1,area.height()-top-bottom-dp(8));
        int size=Math.min(dp(prefs.getInt("size",128)),Math.min(safeW,Math.round(safeH*200f/208)));
        layout.width=Math.max(1,size);layout.height=Math.max(1,Math.round(size*208f/200));
        maxX=Math.max(minX,area.width()-right-layout.width-dp(4));maxY=Math.max(minY,area.height()-bottom-layout.height-dp(4));
        layout.x=Motion.restore(prefs.getFloat("x",.85f),minX,maxX);layout.y=Motion.restore(prefs.getFloat("y",.65f),minY,maxY);motionX=layout.x;
        boolean pass=prefs.getBoolean("pass",false);
        if(pass)layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        // Android 12 起，不可触摸的跨应用悬浮窗需低于系统遮挡透明度阈值，才能把点击传给下层。
        layout.alpha=pass?.75f:1f;
        updateWindow();
        positionMenu();
    }
    private void updateWindow(){if(pet!=null&&pet.isAttachedToWindow()){try{windows.updateViewLayout(pet,layout);}catch(IllegalArgumentException|SecurityException error){stopSelf();}}}
    private void savePosition(){prefs.edit().putFloat("x",Motion.fraction(layout.x,minX,maxX)).putFloat("y",Motion.fraction(layout.y,minY,maxY)).apply();}
    private void cancelThrow(){
        if(fling.isActive()){fling.stop();if(layout!=null)savePosition();if(pet!=null)pet.play("idle",true);nextAction=SystemClock.uptimeMillis()+5000;}
    }
    private void recycleVelocity(){trail.clear();}
    private void trackVelocity(MotionEvent event){
        float density=getResources().getDisplayMetrics().density;
        float offsetX=event.getRawX()-event.getX(),offsetY=event.getRawY()-event.getY();
        for(int i=0;i<event.getHistorySize();i++)trail.add(event.getHistoricalEventTime(i),(event.getHistoricalX(i)+offsetX)/density,(event.getHistoricalY(i)+offsetY)/density);
        trail.add(event.getEventTime(),event.getRawX()/density,event.getRawY()/density);
    }
    private boolean touch(View view,MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                cancelThrow();recycleVelocity();trackVelocity(event);
                pressed=true;dragging=false;downX=event.getRawX();downY=event.getRawY();originX=layout.x;originY=layout.y;moveUntil=0;manualUntil=0;
                gesture.begin(event.getEventTime(),downX,downY,ViewConfiguration.get(this).getScaledTouchSlop());
                pet.play("idle",true);return true;
            case MotionEvent.ACTION_MOVE:
                if(!pressed)return true;
                trackVelocity(event);
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                gesture.move(event.getRawX(),event.getRawY());dragging=gesture.isDragging();
                if(dragging){layout.x=Motion.clamp(originX+Math.round(dx),minX,maxX);layout.y=Motion.clamp(originY+Math.round(dy),minY,maxY);updateWindow();}
                return true;
            case MotionEvent.ACTION_UP:
                boolean open=gesture.end(event.getEventTime(),event.getRawX(),event.getRawY());
                boolean wasDrag=pressed&&gesture.isDragging();
                if(wasDrag){
                    layout.x=Motion.clamp(originX+Math.round(event.getRawX()-downX),minX,maxX);
                    layout.y=Motion.clamp(originY+Math.round(event.getRawY()-downY),minY,maxY);updateWindow();
                }
                pressed=false;savePosition();nextAction=SystemClock.uptimeMillis()+5000;
                if(wasDrag&&!paused&&prefs.getBoolean("throw",false)&&!prefs.getBoolean("pass",false)){
                    PhysicsOptions options=physicsOptions();double density=getResources().getDisplayMetrics().density;double[] velocity=trail.release(event.getEventTime(),options.strength);
                    if(velocity!=null&&fling.start(layout.x,layout.y,velocity[0]*density,velocity[1]*density,minX,maxX,minY,maxY,density,options)){
                        moveUntil=0;manualUntil=0;pet.play("jump",true);
                    }
                }
                recycleVelocity();
                dragging=false;if(open)view.performLongClick();return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                gesture.cancel();recycleVelocity();pressed=false;dragging=false;savePosition();nextAction=SystemClock.uptimeMillis()+5000;return true;
            default:return true;
        }
    }
    private void play(String id){
        if(pet==null)return;
        cancelThrow();
        PetAssets.Action action=PetAssets.get(this).action(id);long now=SystemClock.uptimeMillis();moveUntil=0;
        if("walk-right".equals(action.id)||"walk-left".equals(action.id)){
            direction="walk-right".equals(action.id)?1:-1;motionX=layout.x;manualUntil=0;moveUntil=now+2400;nextAction=moveUntil+3500;
        }else{manualUntil=now+Math.max(2500,action.total*3);nextAction=manualUntil+3000;}
        pet.play(action.id,true);
    }
    private PhysicsOptions physicsOptions(){
        return new PhysicsOptions(prefs.getFloat("physics.strength",1),prefs.getFloat("physics.elasticity",.78f),prefs.getFloat("physics.gravity",1400),prefs.getFloat("physics.friction",2.5f));
    }
    private void showMenu(){
        if(menu!=null||pet==null||!Settings.canDrawOverlays(this))return;
        cancelThrow();
        ContextThemeWrapper theme=new ContextThemeWrapper(this,android.R.style.Theme_Material_Light_Dialog_Alert);
        Dialog panel=new Dialog(theme);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);menu=panel;
        LinearLayout root=new LinearLayout(theme);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable background=new GradientDrawable();background.setColor(0xfffff8fc);background.setCornerRadius(dp(18));background.setStroke(dp(1),0xffead7e6);root.setBackground(background);
        LinearLayout header=new LinearLayout(theme);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(theme);title.setText("爱弥斯的小动作");title.setTextSize(15);title.setTextColor(0xff45314e);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button close=menuButton(theme,"×");close.setTextSize(22);close.setContentDescription("关闭菜单");header.addView(close,new LinearLayout.LayoutParams(dp(36),dp(38)));close.setOnClickListener(view->panel.dismiss());root.addView(header);
        TextView hint=new TextView(theme);hint.setText("选个动作，或继续向下滑动");hint.setTextSize(11);hint.setTextColor(0xff8a7487);hint.setPadding(dp(3),0,0,dp(5));root.addView(hint);
        String[] labels=new String[PetAssets.get(this).actions.size()];for(int i=0;i<labels.length;i++)labels[i]=PetAssets.get(this).actions.get(i).label;
        ListView list=new ListView(theme);list.setDivider(new ColorDrawable(0xfff1e5ef));list.setDividerHeight(dp(1));list.setVerticalScrollBarEnabled(true);
        list.setAdapter(new ArrayAdapter<String>(theme,android.R.layout.simple_list_item_1,labels){
            @Override public View getView(int position,View convert,ViewGroup parent){
                TextView item=convert instanceof TextView?(TextView)convert:new TextView(theme);
                item.setText(getItem(position));item.setTextColor(0xff45314e);item.setTextSize(14);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(8),0,dp(8),0);item.setMinHeight(0);
                item.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(44)));return item;
            }
        });
        list.setOnItemClickListener((parent,view,position,id)->{play(PetAssets.get(this).actions.get(position).id);panel.dismiss();});
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(theme);footer.setPadding(0,dp(6),0,0);
        String roamText=prefs.getBoolean("roam",true)?"暂停走动":"允许走动";
        Button roam=menuButton(theme,roamText),settings=menuButton(theme,"设置"),exit=menuButton(theme,"退出");
        settings.setContentDescription("打开设置");exit.setContentDescription("退出桌宠");
        footer.addView(roam,new LinearLayout.LayoutParams(0,dp(44),1.4f));footer.addView(settings,new LinearLayout.LayoutParams(0,dp(44),1));footer.addView(exit,new LinearLayout.LayoutParams(0,dp(44),1));
        roam.setOnClickListener(view->{prefs.edit().putBoolean("roam",!prefs.getBoolean("roam",true)).apply();panel.dismiss();});
        settings.setOnClickListener(view->{panel.dismiss();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));});
        exit.setOnClickListener(view->{panel.dismiss();prefs.edit().putBoolean("enabled",false).apply();stopSelf();});
        root.addView(footer);panel.setContentView(root);panel.setCanceledOnTouchOutside(true);
        Window window=panel.getWindow();window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        panel.setOnDismissListener(dialog->{if(menu==panel)menu=null;nextAction=SystemClock.uptimeMillis()+4500;});
        panel.show();positionMenu();
    }
    private Button menuButton(Context context,String label){
        Button button=new Button(context);button.setText(label);button.setTextSize(12);button.setAllCaps(false);button.setTextColor(0xff79526f);button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setPadding(0,0,0,0);button.setBackgroundColor(Color.TRANSPARENT);return button;
    }
    private void positionMenu(){
        if(menu==null||layout==null)return;
        int right=maxX+layout.width,bottom=maxY+layout.height;
        int width=Math.max(1,Math.min(dp(232),right-minX)),height=Math.max(1,Math.min(dp(352),bottom-minY));
        int x=layout.x+layout.width+dp(8);
        if(x+width>right)x=layout.x-width-dp(8);
        if(x<minX)x=Motion.clamp(layout.x+layout.width/2-width/2,minX,right-width);
        int y=Motion.clamp(layout.y+layout.height/2-height/2,minY,bottom-height);
        Window window=menu.getWindow();WindowManager.LayoutParams attributes=window.getAttributes();attributes.gravity=Gravity.TOP|Gravity.LEFT;attributes.width=width;attributes.height=height;attributes.x=x;attributes.y=y;
        attributes.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;attributes.flags&=~WindowManager.LayoutParams.FLAG_DIM_BEHIND;attributes.dimAmount=0;
        if(Build.VERSION.SDK_INT>=30)attributes.setFitInsetsTypes(0);
        window.setAttributes(attributes);
    }
    private void updateVisibility(){
        if(pet==null)return;
        boolean visible=getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        paused=!visible;pet.setVisibility(visible?View.VISIBLE:View.INVISIBLE);pet.active(visible);handler.removeCallbacks(tick);
        gesture.cancel();recycleVelocity();cancelThrow();pressed=false;moveUntil=0;manualUntil=0;
        if(!visible&&menu!=null)menu.dismiss();
        if(visible){lastTick=SystemClock.uptimeMillis();nextAction=lastTick+3000;pet.play("idle",true);handler.post(tick);}
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences shared,String key){
        if("size".equals(key)||"pass".equals(key)||"roam".equals(key)||"throw".equals(key)||(key!=null&&key.startsWith("physics."))){
            if("pass".equals(key)&&prefs.getBoolean("pass",false)){
                gesture.cancel();recycleVelocity();pressed=false;dragging=false;if(menu!=null)menu.dismiss();
            }
            moveUntil=0;if(pet!=null)pet.play("idle",true);applyLayout();
            getSystemService(NotificationManager.class).notify(1,notification());
        }
    }
    @Override public void onDestroy(){
        running=false;handler.removeCallbacksAndMessages(null);
        fling.stop();recycleVelocity();
        if(menu!=null)menu.dismiss();
        if(pet!=null){pet.active(false);try{windows.removeView(pet);}catch(IllegalArgumentException ignored){}pet=null;}
        if(receiverRegistered)unregisterReceiver(screenReceiver);
        if(displays!=null)displays.unregisterDisplayListener(displayListener);
        if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(this);
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration configuration){
        super.onConfigurationChanged(configuration);
        gesture.cancel();recycleVelocity();cancelThrow();pressed=false;dragging=false;moveUntil=0;
        // 等显示配置更新完成再按新可用区域恢复位置，避免旋转瞬间使用前一方向的尺寸。
        handler.postDelayed(()->{if(pet!=null)applyLayout();},180);
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
