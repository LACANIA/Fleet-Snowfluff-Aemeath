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
import android.content.ContextWrapper;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 独立前台悬浮窗：Activity 关闭后继续运行，用户退出或权限撤销时清理所有窗口。 */
final class PetInstance extends ContextWrapper {
    final String id;
    final SharedPreferences prefs;
    final PetCatalog.Character character;
    private final PetAssets assets;
    private final PetService host;
    private double displayScale=1;
    private long dragSampleTime;
    private float dragSampleX,dragSampleY;
    private double dragVx,dragVy;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private WindowManager windows;
    private WindowManager.LayoutParams layout;
    private PetView pet;
    private Dialog menu;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLayout;
    private JSONArray chatter=new JSONArray();
    private long nextChatter,bubbleUntil,lastChatter;
    private int lastLine=-1;
    private final PetGesture gesture=new PetGesture();
    private final ThrowMotion fling=new ThrowMotion();
    private final ThrowTrail trail=new ThrowTrail();
    private boolean paused,pressed,dragging;
    private int minX,minY,maxX,maxY;
    private float downX,downY;
    private int originX,originY,direction=1;
    private long nextAction,moveUntil,manualUntil,lastTick;
    private double motionX;
    private String[] randomActions;
    void step(double dt,long now){
        if(pet==null||paused)return;

        if(bubble!=null&&now>=bubbleUntil)hideBubble();
        if(prefs.getBoolean("chatter",false)&&!pressed&&menu==null&&!fling.isActive()&&now>=nextChatter)showChatter("idle",false);
        if(fling.isActive()){
            boolean moving=fling.step(dt);layout.x=fling.x();layout.y=fling.y();updateWindow();
            if(!moving){savePosition();pet.play("idle",true);nextAction=now+5000;showChatter("throw",false);}
            return;
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
                    PetAssets.Action action=assets.action(id);
                    manualUntil=now+Math.max(1800,action.total*2);pet.play(id,true);
                }
            }
        }
    }
    PetInstance(PetService host,PetStore store,String id){
        super(host);this.host=host;this.id=id;this.prefs=store.prefs(id);
        character=PetCatalog.get(this,prefs.getString("character","aemeath"));assets=PetAssets.get(this,character.id);
        windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        try{if(!character.chatter.isEmpty())chatter=new JSONObject(PetAssets.read(this,character.chatter)).getJSONArray("lines");}catch(Exception ignored){}
        List<String> idle=new ArrayList<>();for(PetAssets.Action action:assets.actions)if(!action.id.startsWith("walk-")&&!action.id.equals("idle"))idle.add(action.id);
        randomActions=idle.isEmpty()?new String[]{"idle"}:idle.toArray(new String[0]);
        pet=new PetView(this,character.id);
        layout=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.TOP|Gravity.LEFT;layout.setTitle("FlyingPet:"+id);if(Build.VERSION.SDK_INT>=30)layout.setFitInsetsTypes(0);
        pet.setOnTouchListener(this::touch);pet.setOnLongClickListener(view->{showMenu();return true;});
        applyLayout();windows.addView(pet,layout);nextAction=SystemClock.uptimeMillis()+3500;nextChatter=SystemClock.uptimeMillis()+15000;
    }
    String name(){return prefs.getString("name",character.name);}
    void perform(String action,String animation){
        if(PetService.PLAY.equals(action))play(animation);
        else if(PetService.HOME.equals(action)){cancelThrow();prefs.edit().putFloat("x",.85f).putFloat("y",.65f).apply();moveUntil=0;applyLayout();}
        else if(PetService.SAY.equals(action))showChatter("idle",true);
        else if(PetService.FIND.equals(action)){play("wave");showText(name());}
    }
    private void stopSelf(){host.hide(id);}
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
        int size=Math.min(dp(prefs.getInt("size",128)),Math.min(safeW,(int)Math.floor(safeH*assets.width/(double)assets.height)));
        int baseHeight=(int)Math.round(size*assets.height/(double)assets.width);
        layout.width=Math.max(1,(int)Math.floor(size*displayScale));layout.height=Math.max(1,(int)Math.floor(baseHeight*displayScale));
        maxX=Math.max(minX,area.width()-right-layout.width-dp(4));maxY=Math.max(minY,area.height()-bottom-layout.height-dp(4));
        layout.x=Motion.restore(prefs.getFloat("x",.85f),minX,maxX);layout.y=Motion.restore(prefs.getFloat("y",.65f),minY,maxY);motionX=layout.x;
        boolean pass=passing();
        if(pass)layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        // Android 12 起，不可触摸的跨应用悬浮窗需低于系统遮挡透明度阈值，才能把点击传给下层。
        layout.alpha=pass?OverlayOpacity.alpha(prefs.getFloat("opacity",1),host.touchLayers()):Math.max(.1f,Math.min(1,prefs.getFloat("opacity",1)));
        updateWindow();
        positionMenu();
    }
    private void updateWindow(){if(pet!=null&&pet.isAttachedToWindow()){try{windows.updateViewLayout(pet,layout);positionBubble();}catch(IllegalArgumentException|SecurityException error){stopSelf();}}}
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
                hideBubble();
                cancelThrow();recycleVelocity();trackVelocity(event);dragSampleTime=event.getEventTime();dragSampleX=event.getRawX();dragSampleY=event.getRawY();dragVx=dragVy=0;
                pressed=true;dragging=false;downX=event.getRawX();downY=event.getRawY();originX=layout.x;originY=layout.y;moveUntil=0;manualUntil=0;
                gesture.begin(event.getEventTime(),downX,downY,ViewConfiguration.get(this).getScaledTouchSlop());
                pet.play("idle",true);return true;
            case MotionEvent.ACTION_MOVE:
                if(!pressed)return true;
                trackVelocity(event);
                long sampleSpan=event.getEventTime()-dragSampleTime;
                if(sampleSpan>0){dragVx=Math.max(-dp(3600),Math.min(dp(3600),(event.getRawX()-dragSampleX)*1000/sampleSpan));dragVy=Math.max(-dp(3600),Math.min(dp(3600),(event.getRawY()-dragSampleY)*1000/sampleSpan));dragSampleTime=event.getEventTime();dragSampleX=event.getRawX();dragSampleY=event.getRawY();}
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
        PetAssets.Action action=assets.action(id);long now=SystemClock.uptimeMillis();moveUntil=0;
        if("walk-right".equals(action.id)||"walk-left".equals(action.id)){
            direction="walk-right".equals(action.id)?1:-1;motionX=layout.x;manualUntil=0;moveUntil=now+2400;nextAction=moveUntil+3500;
        }else{manualUntil=now+Math.max(2500,action.total*3);nextAction=manualUntil+3000;}
        pet.play(action.id,true);
        showChatter(action.id,false);
    }
    private void showChatter(String context,boolean preview){
        long now=SystemClock.uptimeMillis();nextChatter=now+45000+random.nextInt(45001);
        if(pet==null||paused||pressed||menu!=null||chatter.length()==0||(!preview&&!prefs.getBoolean("chatter",false))||(!preview&&now-lastChatter<12000))return;
        List<Integer> matches=new ArrayList<>();
        for(int i=0;i<chatter.length();i++){JSONObject item=chatter.optJSONObject(i);if(item!=null&&context.equals(item.optString("context")))matches.add(i);}
        if(matches.isEmpty())return;
        if(matches.size()>1)matches.remove(Integer.valueOf(lastLine));
        int selected=matches.get(random.nextInt(matches.size()));String line=chatter.optJSONObject(selected).optString("text");
        if(line.isEmpty()||line.length()>32)return;
        showText(line);lastLine=selected;
    }
    private void showText(String line){
        if(pet==null||paused)return;long now=SystemClock.uptimeMillis();hideBubble();TextView text=new TextView(this);text.setText(line);text.setTextSize(13);text.setTextColor(0xff45314e);text.setGravity(Gravity.CENTER);text.setMaxLines(2);text.setPadding(dp(10),dp(8),dp(10),dp(8));
        text.setContentDescription(name()+"的小气泡："+line);
        GradientDrawable background=new GradientDrawable();background.setColor(0xfffff6fb);background.setCornerRadius(dp(13));background.setStroke(dp(1),0xffdcc4d4);text.setBackground(background);
        text.measure(View.MeasureSpec.makeMeasureSpec(Math.min(dp(180),maxX+layout.width-minX),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(dp(76),View.MeasureSpec.AT_MOST));
        bubbleLayout=new WindowManager.LayoutParams(text.getMeasuredWidth(),text.getMeasuredHeight(),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        bubbleLayout.gravity=Gravity.TOP|Gravity.LEFT;bubbleLayout.alpha=OverlayOpacity.alpha(.75f,host.touchLayers()+1);if(Build.VERSION.SDK_INT>=30)bubbleLayout.setFitInsetsTypes(0);
        bubbleLayout.setTitle("FlyingSpeech:"+id);
        int[] position=bubblePosition();if(position==null)return;bubbleLayout.x=position[0];bubbleLayout.y=position[1];
        try{bubble=text;host.opacityChanged();windows.addView(text,bubbleLayout);bubbleUntil=now+4500;lastChatter=now;}catch(IllegalArgumentException|SecurityException ignored){bubble=null;host.opacityChanged();}
    }
    private int[] bubblePosition(){
        if(layout==null||bubbleLayout==null)return null;
        int[] point=SpeechPlacement.place(layout.x,layout.y,layout.width,layout.height,bubbleLayout.width,bubbleLayout.height,minX,minY,maxX+layout.width,maxY+layout.height,dp(6));
        return point!=null&&host.bubbleAllowed(id,point[0],point[1],bubbleLayout.width,bubbleLayout.height)?point:null;
    }
    private void positionBubble(){
        if(bubble==null)return;int[] position=bubblePosition();if(position==null){hideBubble();return;}
        bubbleLayout.x=position[0];bubbleLayout.y=position[1];
        try{windows.updateViewLayout(bubble,bubbleLayout);}catch(IllegalArgumentException|SecurityException ignored){hideBubble();}
    }
    private void hideBubble(){
        if(bubble!=null){TextView old=bubble;bubble=null;try{windows.removeView(old);}catch(IllegalArgumentException|SecurityException ignored){}host.opacityChanged();}
        bubbleUntil=0;
    }
    private PhysicsOptions physicsOptions(){
        return new PhysicsOptions(prefs.getFloat("physics.strength",1),prefs.getFloat("physics.elasticity",.78f),prefs.getFloat("physics.gravity",1400),prefs.getFloat("physics.friction",2.5f));
    }
    private void showMenu(){
        if(menu!=null||pet==null||!Settings.canDrawOverlays(this))return;
        hideBubble();
        cancelThrow();
        ContextThemeWrapper theme=new ContextThemeWrapper(this,android.R.style.Theme_Material_Light_Dialog_Alert);
        Dialog panel=new Dialog(theme);panel.requestWindowFeature(Window.FEATURE_NO_TITLE);menu=panel;
        LinearLayout root=new LinearLayout(theme);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable background=new GradientDrawable();background.setColor(0xfffff8fc);background.setCornerRadius(dp(18));background.setStroke(dp(1),0xffead7e6);root.setBackground(background);
        LinearLayout header=new LinearLayout(theme);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(theme);title.setText(name()+"的小动作");title.setTextSize(15);title.setTextColor(0xff45314e);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button close=menuButton(theme,"×");close.setTextSize(22);close.setContentDescription("关闭菜单");header.addView(close,new LinearLayout.LayoutParams(dp(36),dp(38)));close.setOnClickListener(view->panel.dismiss());root.addView(header);
        TextView hint=new TextView(theme);hint.setText("选个动作，或继续向下滑动");hint.setTextSize(11);hint.setTextColor(0xff8a7487);hint.setPadding(dp(3),0,0,dp(5));root.addView(hint);
        String[] labels=new String[assets.actions.size()];for(int i=0;i<labels.length;i++)labels[i]=assets.actions.get(i).label;
        ListView list=new ListView(theme);list.setDivider(new ColorDrawable(0xfff1e5ef));list.setDividerHeight(dp(1));list.setVerticalScrollBarEnabled(true);
        list.setAdapter(new ArrayAdapter<String>(theme,android.R.layout.simple_list_item_1,labels){
            @Override public View getView(int position,View convert,ViewGroup parent){
                TextView item=convert instanceof TextView?(TextView)convert:new TextView(theme);
                item.setText(getItem(position));item.setTextColor(0xff45314e);item.setTextSize(14);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(8),0,dp(8),0);item.setMinHeight(0);
                item.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(44)));return item;
            }
        });
        list.setOnItemClickListener((parent,view,position,id)->{panel.dismiss();play(assets.actions.get(position).id);});
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(theme);footer.setPadding(0,dp(6),0,0);
        String roamText=prefs.getBoolean("roam",true)?"暂停走动":"允许走动";
        Button roam=menuButton(theme,roamText),settings=menuButton(theme,"设置"),exit=menuButton(theme,"收起这只");
        settings.setContentDescription("打开设置");exit.setContentDescription("收起"+name());
        footer.addView(roam,new LinearLayout.LayoutParams(0,dp(44),1.4f));footer.addView(settings,new LinearLayout.LayoutParams(0,dp(44),1));footer.addView(exit,new LinearLayout.LayoutParams(0,dp(44),1));
        roam.setOnClickListener(view->{prefs.edit().putBoolean("roam",!prefs.getBoolean("roam",true)).apply();panel.dismiss();});
        settings.setOnClickListener(view->{panel.dismiss();host.openSettings(id);});
        exit.setOnClickListener(view->{panel.dismiss();stopSelf();});
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
    void visibility(boolean visible){
        if(pet==null)return;
        if(paused==!visible&&pet.getVisibility()==(visible?View.VISIBLE:View.INVISIBLE))return;
        paused=!visible;pet.setVisibility(visible?View.VISIBLE:View.INVISIBLE);pet.active(visible);halt();
        nextAction=SystemClock.uptimeMillis()+3000;nextChatter=SystemClock.uptimeMillis()+15000;
    }
    void settingsChanged(String key){
        if("chatter".equals(key)){hideBubble();nextChatter=SystemClock.uptimeMillis();return;}
        if("size".equals(key)){host.changed(true);return;}
        if("pass".equals(key)){gesture.cancel();recycleVelocity();pressed=false;dragging=false;if(menu!=null)menu.dismiss();}
        if("pass".equals(key)||"opacity".equals(key)||"roam".equals(key)||"throw".equals(key)||key.startsWith("physics.")){
            moveUntil=0;cancelThrow();if(pet!=null)pet.play("idle",true);applyLayout();host.opacityChanged();
        }
    }
    void halt(){gesture.cancel();recycleVelocity();pressed=false;dragging=false;cancelThrow();moveUntil=manualUntil=0;hideBubble();if(menu!=null)menu.dismiss();if(pet!=null)pet.play("idle",true);nextAction=SystemClock.uptimeMillis()+5000;}
    void displayChanged(){halt();applyLayout();}
    void close(){halt();handler.removeCallbacksAndMessages(null);if(pet!=null){pet.active(false);try{windows.removeView(pet);}catch(IllegalArgumentException ignored){}pet=null;}}
    boolean flying(){return fling.isActive();}
    boolean held(){return pressed||menu!=null;}
    boolean passing(){return prefs.getBoolean("pass",false)||host.settingsOpen();}
    boolean hasBubble(){return bubble!=null;}
    int[] frame(){return new int[]{layout.x,layout.y,layout.x+layout.width,layout.y+layout.height};}
    int[] area(){return new int[]{minX,minY,maxX+layout.width,maxY+layout.height};}
    int[] requestedSize(){int[] area=area();int width=Math.min(dp(prefs.getInt("size",128)),Math.min(area[2]-area[0],(int)Math.floor((area[3]-area[1])*assets.width/(double)assets.height)));return new int[]{Math.max(1,width),Math.max(1,(int)Math.round(width*assets.height/(double)assets.width))};}
    void place(PetPacking.Slot slot){halt();displayScale=slot.scale;applyLayout();layout.x=Motion.clamp(slot.x,minX,maxX);layout.y=Motion.clamp(slot.y,minY,maxY);motionX=layout.x;updateWindow();savePosition();}
    void alpha(){
        if(pet==null)return;
        float requested=Math.max(.1f,Math.min(1,prefs.getFloat("opacity",1)));
        if(passing())layout.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;else layout.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if(host.settingsOpen()&&menu!=null)menu.dismiss();
        layout.alpha=passing()?OverlayOpacity.alpha(requested,host.touchLayers()):requested;
        if(pet.isAttachedToWindow())try{windows.updateViewLayout(pet,layout);}catch(IllegalArgumentException ignored){}
        if(bubble!=null&&bubble.isAttachedToWindow()){bubbleLayout.alpha=OverlayOpacity.alpha(.75f,host.touchLayers());try{windows.updateViewLayout(bubble,bubbleLayout);}catch(IllegalArgumentException ignored){}}
    }
    void refreshBubble(){positionBubble();}
    PetCollision.Body body(long now){
        double vx=fling.isActive()?fling.velocityX():moveUntil>now?direction*dp(38):0,vy=fling.isActive()?fling.velocityY():0;
        if(pressed){vx=now-dragSampleTime<120?dragVx:0;vy=now-dragSampleTime<120?dragVy:0;}
        if(menu!=null){vx=0;vy=0;}
        return new PetCollision.Body(layout.x+character.body[0]*layout.width,layout.y+character.body[1]*layout.height,(character.body[2]-character.body[0])*layout.width,(character.body[3]-character.body[1])*layout.height,vx,vy,layout.width*(double)layout.width,physicsOptions().elasticity,held());
    }
    void contact(PetCollision.Body body){
        if(held())return;
        int x=Motion.clamp((int)Math.round(body.x-character.body[0]*layout.width),minX,maxX),y=Motion.clamp((int)Math.round(body.y-character.body[1]*layout.height),minY,maxY);
        boolean wasFlying=fling.isActive();moveUntil=manualUntil=0;nextAction=SystemClock.uptimeMillis()+5000;
        fling.collision(x,y,body.vx,body.vy,minX,maxX,minY,maxY,getResources().getDisplayMetrics().density,physicsOptions());
        layout.x=x;layout.y=y;motionX=x;updateWindow();if(fling.isActive()&&!wasFlying)pet.play("jump",true);if(!fling.isActive())savePosition();
    }
}
