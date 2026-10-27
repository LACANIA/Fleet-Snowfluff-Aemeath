package io.github.lacania.aemeathpocket;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** 本地控制面板；离开 Activity 不停止前台桌宠服务。 */
public final class MainActivity extends Activity {
    private static final int INK=0xff45314e, MUTED=0xff7c6c83, ACCENT=0xffad548a;
    private SharedPreferences prefs;
    private PetView preview;
    private TextView status,sizeLabel;
    private boolean awaitingOverlay;
    private Switch roaming,passing,throwing,chattering;
    private LinearLayout physicsPanel;
    private Button physicsToggle;
    private boolean physicsExpanded;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("pet",MODE_PRIVATE);
        if(state!=null){awaitingOverlay=state.getBoolean("awaitingOverlay");physicsExpanded=state.getBoolean("physicsExpanded");}
        getWindow().setStatusBarColor(0xfffff6fb);getWindow().setNavigationBarColor(0xfffff6fb);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffff6fb);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(32));scroll.addView(root);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(dp(24)+bars.left,dp(24)+bars.top,dp(24)+bars.right,dp(32)+bars.bottom);return insets;});
        }
        text(root,"AEMEATH · POCKET",12,MUTED,true);
        text(root,"爱弥斯口袋桌宠",28,INK,true);
        TextView intro=text(root,"把一只小爱弥斯，放在你的屏幕上。",15,MUTED,false);margin(intro,8,18);
        FrameLayout card=new FrameLayout(this);card.setBackground(background(0xffffeaf5,28));
        root.addView(card,new LinearLayout.LayoutParams(-1,dp(218)));
        preview=new PetView(this);FrameLayout.LayoutParams petLayout=new FrameLayout.LayoutParams(dp(168),dp(175),Gravity.CENTER);petLayout.bottomMargin=dp(20);card.addView(preview,petLayout);
        TextView count=new TextView(this);count.setText("本地动画 · "+PetAssets.get(this).actions.size()+" 个动作条目");count.setTextSize(13);count.setTextColor(MUTED);count.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-1,dp(32),Gravity.BOTTOM);caption.bottomMargin=dp(8);card.addView(count,caption);
        LinearLayout reactions=new LinearLayout(this);reactions.setGravity(Gravity.CENTER);root.addView(reactions);margin(reactions,12,16);
        for(String[] item:new String[][]{{"招呼","wave"},{"蹦跳","jump"},{"打盹","nap"}}){
            Button action=button(item[0],false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);reactions.addView(action,lp);
            action.setOnClickListener(view->{preview.play(item[1],false);if(PetService.running)startService(new Intent(this,PetService.class).setAction(PetService.PLAY).putExtra("animation",item[1]));});
        }
        status=text(root,"",14,MUTED,false);margin(status,0,12);
        Button start=button("让爱弥斯出来",true);root.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));start.setOnClickListener(view->requestStart());
        Button stop=button("收起桌宠",false);root.addView(stop,new LinearLayout.LayoutParams(-1,dp(48)));margin(stop,8,18);
        stop.setOnClickListener(view->{prefs.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,PetService.class));refresh();});
        text(root,"陪伴方式",19,INK,true);
        roaming=new Switch(this);roaming.setText("自主走动");roaming.setTextColor(INK);roaming.setTextSize(16);roaming.setPadding(0,dp(10),0,dp(10));root.addView(roaming);
        roaming.setChecked(prefs.getBoolean("roam",true));roaming.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("roam",checked).apply());
        throwing=new Switch(this);throwing.setText("拖拽抛射");throwing.setTextColor(INK);throwing.setTextSize(16);throwing.setPadding(0,dp(10),0,dp(10));root.addView(throwing);
        throwing.setChecked(prefs.getBoolean("throw",false));throwing.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("throw",checked).apply());
        text(root,"开启后，快速拖动松手可以抛出爱弥斯，碰到屏幕边缘会弹回。再次按住可以停止，关闭后拖动松手立即停下。",12,MUTED,false);
        physicsToggle=button(physicsExpanded?"收起抛射参数":"调整抛射参数",false);root.addView(physicsToggle,new LinearLayout.LayoutParams(-1,dp(44)));
        physicsPanel=new LinearLayout(this);physicsPanel.setOrientation(LinearLayout.VERTICAL);physicsPanel.setPadding(dp(12),dp(8),dp(12),dp(12));physicsPanel.setBackground(background(0xffffeaf5,16));root.addView(physicsPanel);renderPhysicsControls();physicsPanel.setVisibility(physicsExpanded?View.VISIBLE:View.GONE);
        physicsToggle.setOnClickListener(view->{physicsExpanded=!physicsExpanded;physicsPanel.setVisibility(physicsExpanded?View.VISIBLE:View.GONE);physicsToggle.setText(physicsExpanded?"收起抛射参数":"调整抛射参数");});
        passing=new Switch(this);passing.setText("点击穿透");passing.setTextColor(INK);passing.setTextSize(16);passing.setPadding(0,dp(10),0,dp(10));root.addView(passing);
        passing.setChecked(prefs.getBoolean("pass",false));passing.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("pass",checked).apply());
        text(root,"开启后爱弥斯会变淡，不响应点击或拖动，操作直接交给下层游戏。允许通知后，可从通知栏恢复触控，也可回到这里关闭。",12,MUTED,false);
        chattering=new Switch(this);chattering.setText("随机小气泡");chattering.setTextColor(INK);chattering.setTextSize(16);chattering.setPadding(0,dp(10),0,dp(10));root.addView(chattering);
        chattering.setChecked(prefs.getBoolean("chatter",false));chattering.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean("chatter",checked).apply());
        text(root,"偶尔说一句短短的话，每次约 4 秒，间隔约 45–90 秒。气泡不会拦截触摸，可以随时关闭。",12,MUTED,false);
        Button previewChatter=button("看看台词气泡",false);root.addView(previewChatter,new LinearLayout.LayoutParams(-1,dp(44)));previewChatter.setOnClickListener(view->{if(PetService.running)startService(new Intent(this,PetService.class).setAction(PetService.SAY));else toast("先让爱弥斯出来，再看看气泡吧");});
        sizeLabel=text(root,"",15,INK,false);margin(sizeLabel,18,0);
        SeekBar size=new SeekBar(this);size.setMax(120);size.setProgress(prefs.getInt("size",128)-72);sizeLabel.setText("宠物大小 · "+prefs.getInt("size",128));root.addView(size);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar seek,int progress,boolean user){if(user){int value=progress+72;prefs.edit().putInt("size",value).apply();sizeLabel.setText("宠物大小 · "+value);}}
            public void onStartTrackingTouch(SeekBar seek){}public void onStopTrackingTouch(SeekBar seek){}
        });
        Button gallery=button("看看所有动作",false);root.addView(gallery,new LinearLayout.LayoutParams(-1,dp(48)));margin(gallery,12,8);gallery.setOnClickListener(view->gallery());
        Button home=button("回到初始位置",false);root.addView(home,new LinearLayout.LayoutParams(-1,dp(48)));home.setOnClickListener(view->{
            prefs.edit().putFloat("x",.85f).putFloat("y",.65f).apply();if(PetService.running)startService(new Intent(this,PetService.class).setAction(PetService.HOME));toast("位置已重置");
        });
        TextView help=text(root,"原地按住宠物约 0.6 秒，松手打开动作菜单；按住拖动可以换位置。\n关闭这个设置页后，宠物会继续显示；锁屏时暂停动画。",13,MUTED,false);help.setLineSpacing(dp(4),1);margin(help,20,10);
        Button about=button("素材来源与说明",false);root.addView(about,new LinearLayout.LayoutParams(-1,dp(44)));about.setOnClickListener(view->about());
        text(root,"个人测试版 0.1.3 · 本地运行",12,MUTED,false);
        setContentView(scroll);refresh();
    }
    private void requestStart(){
        if(!Settings.canDrawOverlays(this)){
            awaitingOverlay=true;
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(Exception error){awaitingOverlay=false;toast("请在系统设置中允许本应用显示悬浮窗");}
            return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("askedNotification",false)){
            prefs.edit().putBoolean("askedNotification",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);return;
        }
        startPet();
    }
    private void startPet(){
        if(!Settings.canDrawOverlays(this)){refresh();return;}
        prefs.edit().putBoolean("enabled",true).apply();
        try{startForegroundService(new Intent(this,PetService.class));toast("爱弥斯出来了，返回桌面看看吧");status.postDelayed(this::refresh,400);}
        catch(RuntimeException error){prefs.edit().putBoolean("enabled",false).apply();toast("暂时无法启动，请保持应用在前台后重试");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==1)startPet();}
    @Override protected void onResume(){super.onResume();if(preview!=null)preview.active(true);refresh();if(awaitingOverlay&&Settings.canDrawOverlays(this)){awaitingOverlay=false;requestStart();}}
    @Override protected void onPause(){if(preview!=null)preview.active(false);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)refresh();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("awaitingOverlay",awaitingOverlay);out.putBoolean("physicsExpanded",physicsExpanded);super.onSaveInstanceState(out);}
    private void renderPhysicsControls(){
        physicsPanel.removeAllViews();
        PhysicsOptions options=new PhysicsOptions(prefs.getFloat("physics.strength",1),prefs.getFloat("physics.elasticity",.78f),prefs.getFloat("physics.gravity",1400),prefs.getFloat("physics.friction",2.5f));
        physicsSlider("physics.strength","抛射力度",.25,2.5,.05,options.strength,"%.2f×","数值越大，相同手势抛得越远。");
        physicsSlider("physics.elasticity","反弹弹性",0,.95,.01,options.elasticity*100,"%.0f%%","数值越大，碰边和落地后反弹越明显。",100);
        physicsSlider("physics.gravity","重力",400,2800,50,options.gravity,"%.0f dp/s²","数值越大，下落越快；随屏幕旋转重新判断地面。");
        physicsSlider("physics.friction","地面摩擦",.5,6,.1,options.friction,"%.1f /s","数值越大，落地后水平滑动停止越快。");
        Button reset=button("恢复默认抛射参数",false);physicsPanel.addView(reset,new LinearLayout.LayoutParams(-1,dp(44)));
        reset.setOnClickListener(view->{prefs.edit().putFloat("physics.strength",1).putFloat("physics.elasticity",.78f).putFloat("physics.gravity",1400).putFloat("physics.friction",2.5f).apply();renderPhysicsControls();toast("抛射参数已恢复默认");});
    }
    private void physicsSlider(String key,String title,double low,double high,double step,double value,String format,String hint){physicsSlider(key,title,low,high,step,value,format,hint,1);}
    private void physicsSlider(String key,String title,double low,double high,double step,double displayValue,String format,String hint,double displayScale){
        TextView label=text(physicsPanel,title+" · "+String.format(Locale.ROOT,format,displayValue),14,INK,true);margin(label,4,0);
        SeekBar slider=new SeekBar(this);slider.setContentDescription(title);slider.setMax((int)Math.round((high-low)/step));slider.setProgress((int)Math.round((displayValue/displayScale-low)/step));physicsPanel.addView(slider);
        text(physicsPanel,hint,12,MUTED,false);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){double value=low+progress*step;prefs.edit().putFloat(key,(float)value).apply();label.setText(title+" · "+String.format(Locale.ROOT,format,value*displayScale));}}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    private void refresh(){
        if(status!=null)status.setText(!Settings.canDrawOverlays(this)?"首次使用需要允许显示悬浮窗":PetService.running&&prefs.getBoolean("enabled",false)?"爱弥斯已经在屏幕上了":"准备好时，叫她出来陪伴你");
        if(roaming!=null)roaming.setChecked(prefs.getBoolean("roam",true));if(passing!=null)passing.setChecked(prefs.getBoolean("pass",false));
        if(throwing!=null)throwing.setChecked(prefs.getBoolean("throw",false));
        if(chattering!=null)chattering.setChecked(prefs.getBoolean("chatter",false));
    }
    private void gallery(){
        PetAssets assets=PetAssets.get(this);String[] labels=new String[assets.actions.size()];for(int i=0;i<labels.length;i++)labels[i]=assets.actions.get(i).label;
        new AlertDialog.Builder(this).setTitle("选择一个小动作").setItems(labels,(dialog,index)->{
            PetAssets.Action action=assets.actions.get(index);preview.play(action.id,false);
            if(PetService.running)startService(new Intent(this,PetService.class).setAction(PetService.PLAY).putExtra("animation",action.id));
        }).setNegativeButton("关闭",null).show();
    }
    private void about(){
        try{TextView body=new TextView(this);body.setText(PetAssets.read(this,"NOTICES.txt"));body.setTextSize(14);body.setTextColor(INK);body.setPadding(dp(22),dp(12),dp(22),dp(12));body.setTextIsSelectable(true);
            ScrollView scroll=new ScrollView(this);scroll.addView(body);new AlertDialog.Builder(this).setTitle("素材来源与说明").setView(scroll).setPositiveButton("知道了",null).show();
        }catch(Exception error){toast("说明文件暂时无法打开");}
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private TextView text(LinearLayout root,String value,int size,int color,boolean bold){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(text);return text;}
    private Button button(String value,boolean primary){Button button=new Button(this);button.setText(value);button.setTextSize(15);button.setAllCaps(false);button.setTextColor(primary?Color.WHITE:INK);button.setBackground(background(primary?ACCENT:0xffffeaf5,16));button.setStateListAnimator(null);button.setMinHeight(0);return button;}
    private void margin(View view,int top,int bottom){LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)view.getLayoutParams();params.topMargin=dp(top);params.bottomMargin=dp(bottom);view.setLayoutParams(params);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
