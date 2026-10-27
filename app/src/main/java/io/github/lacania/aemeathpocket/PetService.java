package io.github.lacania.aemeathpocket;

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
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One foreground service owns the registry; every live pet has independent state and windows. */
public final class PetService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String STOP="pet.stop",PLAY="pet.play",PASS="pet.pass",HOME="pet.home",SAY="pet.say",SYNC="pet.sync",FIND="pet.find";
    public static volatile boolean running;
    public static volatile int visibleCount;
    private static final String CHANNEL="pet_presence";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,PetInstance> pets=new LinkedHashMap<>();
    private final LinkedHashMap<String,SharedPreferences> watched=new LinkedHashMap<>();
    private PetStore store;
    private DisplayManager displays;
    private boolean receiverRegistered,awake,needsArrangement,reconciling;
    private int layoutRevision=-1;
    private long lastTick;
    private final Runnable reconcileTask=()->reconcile();
    private final Runnable visibilityRetry=()->refreshVisibility();
    private final Runnable tick=new Runnable(){public void run(){
        if(!running||!awake)return;
        if(!Settings.canDrawOverlays(PetService.this)){stopSelf();return;}
        long now=SystemClock.uptimeMillis();double dt=Math.min(.05,Math.max(0,(now-lastTick)/1000.0));lastTick=now;
        List<PetInstance> snapshot=new ArrayList<>(pets.values());
        for(PetInstance pet:snapshot)pet.step(dt,now);
        if(!store.global.getBoolean("allowOverlap",true))collisions(snapshot,now);
        boolean moving=false;for(PetInstance pet:snapshot){pet.refreshBubble();moving|=pet.flying();}
        handler.postDelayed(this,moving?16:50);
    }};
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){refreshVisibility();}};
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){displayChanged();}
    };
    @Override public void onCreate(){
        super.onCreate();store=new PetStore(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"桌宠运行",NotificationManager.IMPORTANCE_LOW));
        store.global.registerOnSharedPreferenceChangeListener(this);
        displays=getSystemService(DisplayManager.class);displays.registerDisplayListener(displayListener,handler);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        // All three are protected system events; actual power/keyguard state is checked below.
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,filter,Context.RECEIVER_EXPORTED);else registerReceiver(screenReceiver,filter);
        receiverRegistered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?null:intent.getAction();
        if(STOP.equals(action)){store.global.edit().putBoolean("enabled",false).apply();stopSelf();return START_NOT_STICKY;}
        if(!store.global.getBoolean("enabled",false)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        startForeground(1,notification());running=true;reconcile();
        if(PASS.equals(action)){
            for(String id:store.ids())store.prefs(id).edit().putBoolean("pass",false).apply();
        }else if(intent!=null){
            String id=intent.getStringExtra("petId");if(id==null)id=store.selected();PetInstance pet=pets.get(id);
            if(pet!=null)pet.perform(action,intent.getStringExtra("animation"));
        }
        // Reused services must recover even if a device delayed or omitted USER_PRESENT.
        refreshVisibility();return START_STICKY;
    }
    void changed(boolean arrange){needsArrangement|=arrange;handler.removeCallbacks(reconcileTask);handler.post(reconcileTask);}
    void hide(String id){store.prefs(id).edit().putBoolean("visible",false).apply();changed(false);}
    void openSettings(String id){store.select(id);startActivity(new Intent(this,MainActivity.class).putExtra("petId",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    private void reconcile(){
        if(reconciling||store==null||!running)return;
        if(!store.global.getBoolean("enabled",false)){stopSelf();return;}
        reconciling=true;
        try{
            List<String> ids=store.ids();
            for(String id:new ArrayList<>(watched.keySet()))if(!ids.contains(id)){watched.remove(id).unregisterOnSharedPreferenceChangeListener(this);}
            for(String id:ids)if(!watched.containsKey(id)){SharedPreferences prefs=store.prefs(id);prefs.registerOnSharedPreferenceChangeListener(this);watched.put(id,prefs);}
            for(String id:new ArrayList<>(pets.keySet()))if(!ids.contains(id)||!store.prefs(id).getBoolean("visible",true)){PetInstance old=pets.remove(id);old.close();}
            int before=pets.size();
            for(String id:ids)if(store.prefs(id).getBoolean("visible",true)&&!pets.containsKey(id)){
                try{pets.put(id,new PetInstance(this,store,id));}catch(RuntimeException error){Toast.makeText(this,"有一只桌宠暂时无法显示，请检查角色素材与悬浮窗权限",Toast.LENGTH_LONG).show();}
            }
            visibleCount=pets.size();
            int revision=store.revision();boolean revised=layoutRevision>=0&&revision!=layoutRevision;
            // Preserve the migrated first pet's position; multi-pet starts fit their requested sizes.
            if(needsArrangement||revised||(layoutRevision<0&&pets.size()>1)||pets.size()>before&&before>0)arrange();
            needsArrangement=false;layoutRevision=revision;opacityChanged();
            getSystemService(NotificationManager.class).notify(1,notification());
        }finally{reconciling=false;}
        refreshVisibility();
    }
    private void arrange(){
        List<PetInstance> list=new ArrayList<>(pets.values());if(list.isEmpty())return;
        for(PetInstance pet:list)pet.halt();
        int[] area=list.get(0).area();int[][] sizes=new int[list.size()][];for(int i=0;i<list.size();i++)sizes[i]=list.get(i).requestedSize();
        PetPacking.Slot[] slots=PetPacking.pack(area[0],area[1],area[2]-area[0],area[3]-area[1],sizes,Math.round(8*getResources().getDisplayMetrics().density));
        for(int i=0;i<list.size();i++)list.get(i).place(slots[i]);
    }
    private void collisions(List<PetInstance> list,long now){
        if(list.size()<2)return;
        PetCollision.Body[] bodies=new PetCollision.Body[list.size()];boolean[] touched=new boolean[list.size()];
        for(int i=0;i<list.size();i++)bodies[i]=list.get(i).body(now);
        for(int pass=0;pass<4;pass++){
            boolean hit=false;
            for(int i=0;i<bodies.length;i++)for(int j=i+1;j<bodies.length;j++)if(PetCollision.resolve(bodies[i],bodies[j])){touched[i]=touched[j]=true;hit=true;}
            if(!hit)break;
        }
        for(int i=0;i<list.size();i++)if(touched[i])list.get(i).contact(bodies[i]);
    }
    int touchLayers(){int count=0;for(PetInstance pet:pets.values()){if(pet.passing())count++;if(pet.hasBubble())count++;}return Math.max(1,count);}
    boolean settingsOpen(){return MainActivity.settingsVisible;}
    void opacityChanged(){for(PetInstance pet:new ArrayList<>(pets.values()))pet.alpha();}
    boolean bubbleAllowed(String owner,int x,int y,int width,int height){
        for(Map.Entry<String,PetInstance> entry:pets.entrySet())if(!entry.getKey().equals(owner)){
            int[] rect=entry.getValue().frame();if(x<rect[2]&&x+width>rect[0]&&y<rect[3]&&y+height>rect[1])return false;
        }
        return true;
    }
    private void refreshVisibility(){
        handler.removeCallbacks(visibilityRetry);handler.removeCallbacks(tick);
        if(!running)return;
        boolean interactive=getSystemService(PowerManager.class).isInteractive();
        awake=interactive&&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        for(PetInstance pet:pets.values())pet.visibility(awake);
        if(awake){lastTick=SystemClock.uptimeMillis();handler.post(tick);}
        else if(interactive)handler.postDelayed(visibilityRetry,500);
        // Fully sleeping screens do not run an animation or polling loop.
    }
    private void displayChanged(){
        for(PetInstance pet:new ArrayList<>(pets.values()))pet.displayChanged();
        handler.removeCallbacks(displaySettled);handler.postDelayed(displaySettled,180);refreshVisibility();
    }
    private final Runnable displaySettled=()->{for(PetInstance pet:new ArrayList<>(pets.values()))pet.displayChanged();arrange();refreshVisibility();};
    @Override public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);displayChanged();}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){
        if(key==null)return;
        if(preferences==store.global){
            if(key.equals("selected")||key.equals("serial"))return;
            if(key.equals("layoutRevision")||key.equals("allowOverlap"))changed(true);else changed(false);return;
        }
        if(key.equals("x")||key.equals("y"))return;
        for(Map.Entry<String,SharedPreferences> entry:watched.entrySet())if(entry.getValue()==preferences){
            PetInstance instance=pets.get(entry.getKey());if(instance!=null)instance.settingsChanged(key);
            if(key.equals("visible")||key.equals("character"))changed(true);else if(key.equals("name"))changed(false);break;
        }
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,PetService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent recover=PendingIntent.getService(this,2,new Intent(this,PetService.class).setAction(PASS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.pet_icon).setContentTitle("飞行雪绒 · "+pets.size()+" 只桌宠")
            .setContentText("长按单只桌宠选择动作，通知可恢复全部触控").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null,"恢复全部触控",recover).build()).addAction(new Notification.Action.Builder(null,"全部收起",stop).build()).build();
    }
    @Override public void onDestroy(){
        running=false;visibleCount=0;handler.removeCallbacksAndMessages(null);
        for(PetInstance pet:new ArrayList<>(pets.values()))pet.close();pets.clear();
        for(SharedPreferences prefs:watched.values())prefs.unregisterOnSharedPreferenceChangeListener(this);watched.clear();
        if(store!=null)store.global.unregisterOnSharedPreferenceChangeListener(this);
        if(receiverRegistered)unregisterReceiver(screenReceiver);if(displays!=null)displays.unregisterDisplayListener(displayListener);
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
