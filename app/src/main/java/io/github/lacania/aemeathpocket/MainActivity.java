package io.github.lacania.aemeathpocket;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

/** Global roster controls and the selected instance's independent settings. */
public final class MainActivity extends Activity {
    static volatile boolean settingsVisible;
    private static final int INK=0xff45314e,MUTED=0xff7c6c83,ACCENT=0xffad548a;
    private PetStore store;
    private SharedPreferences prefs;
    private String selected="";
    private PetView preview;
    private TextView status;
    private LinearLayout root,physicsPanel;
    private Button physicsToggle;
    private boolean awaitingOverlay,physicsExpanded,binding;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new PetStore(this);
        if(state!=null){awaitingOverlay=state.getBoolean("awaitingOverlay");physicsExpanded=state.getBoolean("physicsExpanded");}
        String id=getIntent().getStringExtra("petId");if(id!=null)store.select(id);render();
    }
    private void render(){
        binding=true;if(preview!=null)preview.active(false);preview=null;selected=store.selected();prefs=selected.isEmpty()?null:store.prefs(selected);
        getWindow().setStatusBarColor(0xfffff6fb);getWindow().setNavigationBarColor(0xfffff6fb);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfffff6fb);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(24),dp(24),dp(32));scroll.addView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((view,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(dp(24)+bars.left,dp(24)+bars.top,dp(24)+bars.right,dp(32)+bars.bottom);return insets;});}
        text(root,"FLYING EDELWEISS",12,MUTED,true);text(root,"飞行雪绒",28,INK,true);
        TextView intro=text(root,"把喜欢的角色，放在你的屏幕上。",15,MUTED,false);margin(intro,8,14);
        List<String> ids=store.ids();text(root,"已添加 "+ids.size()+" / "+PetStore.MAX_PETS+" 只 · 可重复选择同一角色",13,MUTED,false);
        LinearLayout roster=new LinearLayout(this);root.addView(roster);margin(roster,10,8);
        Button add=button("添加桌宠",true),clear=button("一键清空桌宠",false);addRowButton(roster,add,1);addRowButton(roster,clear,1);add.setEnabled(ids.size()<PetStore.MAX_PETS);clear.setEnabled(!ids.isEmpty());
        add.setOnClickListener(view->addPet());clear.setOnClickListener(view->clearPets());
        status=text(root,"",14,MUTED,false);margin(status,8,8);
        Button start=button("启动桌宠",true);root.addView(start,new LinearLayout.LayoutParams(-1,dp(48)));start.setEnabled(!ids.isEmpty());start.setOnClickListener(view->requestStart());
        Button stop=button("全部收起",false);root.addView(stop,new LinearLayout.LayoutParams(-1,dp(44)));margin(stop,6,8);stop.setOnClickListener(view->{store.global.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,PetService.class));updateStatus();});
        Switch overlap=switchView(root,"允许桌宠重叠",store.global.getBoolean("allowOverlap",true));
        text(root,"关闭后按身体碰撞箱接触，抛射撞击才会撞飞其他宠物。每次切换都会停止运动并重新排布。",12,MUTED,false);
        Switch walkKnocks=switchView(root,"走动也能撞飞",store.global.getBoolean("walkKnocks",false));walkKnocks.setEnabled(!overlap.isChecked());
        walkKnocks.setOnCheckedChangeListener((view,checked)->{if(!binding)store.global.edit().putBoolean("walkKnocks",checked).apply();});
        overlap.setOnCheckedChangeListener((view,checked)->{walkKnocks.setEnabled(!checked);if(!binding)store.overlap(checked);});
        text(root,"仅在关闭重叠时生效。开启后，走动者继续走完本次时间，被撞者按抛射规则弹跳、下落和继续碰撞。",12,MUTED,false);
        Button arrange=button("重新排布桌宠",false);root.addView(arrange,new LinearLayout.LayoutParams(-1,dp(44)));arrange.setOnClickListener(view->{store.arrange();toast("已重新排布");});
        text(root,"数量或大小排不下时，会临时缩小显示；各自设置的大小会保留。",12,MUTED,false);
        if(!ids.isEmpty()){
            TextView heading=text(root,"单只桌宠设置",20,INK,true);margin(heading,20,6);
            Button picker=button("切换桌宠："+store.name(selected),true);picker.setContentDescription("选择要设置的桌宠");picker.setSingleLine(true);picker.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(picker,new LinearLayout.LayoutParams(-1,dp(50)));
            picker.setOnClickListener(view->{String[] labels=new String[ids.size()];for(int i=0;i<ids.size();i++)labels[i]=store.name(ids.get(i))+(store.prefs(ids.get(i)).getBoolean("visible",true)?"":"（已收起）");new AlertDialog.Builder(this).setTitle("选择要设置的桌宠").setSingleChoiceItems(labels,Math.max(0,ids.indexOf(selected)),(dialog,index)->{store.select(ids.get(index));physicsExpanded=false;dialog.dismiss();render();}).setNegativeButton("取消",null).show();});
            text(root,"下面只修改："+store.name(selected),13,MUTED,false);
            text(root,"设置页中桌宠自动让触摸穿过，返回桌面后恢复各自的触控开关。",12,MUTED,false);
            renderSelected();
        }else{TextView empty=text(root,"列表已经清空，点击“添加桌宠”选择角色。",15,MUTED,false);margin(empty,24,12);}
        TextView help=text(root,"原地长按约 0.6 秒后松手打开单只菜单，拖动可以换位置。熄屏暂停绘制，解锁或回到应用时重新检查显示状态。",13,MUTED,false);margin(help,20,8);
        text(root,"个人测试版 0.2.1 · 本地运行",12,MUTED,false);
        setContentView(scroll);binding=false;updateStatus();
    }
    private void renderSelected(){
        PetCatalog.Character character=PetCatalog.get(this,prefs.getString("character","aemeath"));PetAssets assets=PetAssets.get(this,character.id);
        FrameLayout card=new FrameLayout(this);card.setBackground(background(0xffffeaf5,24));root.addView(card,new LinearLayout.LayoutParams(-1,dp(190)));margin(card,8,8);
        preview=new PetView(this,character.id);FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(dp(142),dp(148),Gravity.CENTER);image.bottomMargin=dp(20);card.addView(preview,image);
        TextView caption=new TextView(this);caption.setText(store.name(selected)+" · "+character.name+" · "+assets.actions.size()+" 个动作");caption.setTextColor(MUTED);caption.setTextSize(12);caption.setGravity(Gravity.CENTER);card.addView(caption,new FrameLayout.LayoutParams(-1,dp(32),Gravity.BOTTOM));
        LinearLayout identify=new LinearLayout(this);root.addView(identify);Button find=button("找找这只",false),rename=button("重命名这只",false),remove=button("删除这只",false);addRowButton(identify,find,1);addRowButton(identify,rename,1);addRowButton(identify,remove,1);
        find.setOnClickListener(view->send(PetService.FIND,null));rename.setOnClickListener(view->rename());remove.setOnClickListener(view->removePet());
        option("显示这只桌宠","visible",true);option("自主走动","roam",true);option("拖拽抛射","throw",false);
        text(root,"抛射开启后，快速拖动松手会飞出并碰边弹跳；再次按住或关闭开关可以停止。",12,MUTED,false);
        physicsToggle=button(physicsExpanded?"收起抛射参数":"调整抛射参数",false);root.addView(physicsToggle,new LinearLayout.LayoutParams(-1,dp(44)));
        physicsPanel=new LinearLayout(this);physicsPanel.setOrientation(LinearLayout.VERTICAL);physicsPanel.setPadding(dp(12),dp(8),dp(12),dp(12));physicsPanel.setBackground(background(0xffffeaf5,16));root.addView(physicsPanel);renderPhysics();physicsPanel.setVisibility(physicsExpanded?View.VISIBLE:View.GONE);
        physicsToggle.setOnClickListener(view->{physicsExpanded=!physicsExpanded;physicsPanel.setVisibility(physicsExpanded?View.VISIBLE:View.GONE);physicsToggle.setText(physicsExpanded?"收起抛射参数":"调整抛射参数");});
        option("点击穿透","pass",false);text(root,"只改变这只宠物的触摸响应。多只穿透窗口重叠时，会自动降低显示浓度，避免影响下层应用点击；通知可恢复全部触控。",12,MUTED,false);
        int transparency=Math.round((1-prefs.getFloat("opacity",1))*100);TextView alpha=text(root,"透明度 · "+transparency+"%",15,INK,false);
        SeekBar opacity=new SeekBar(this);opacity.setContentDescription("这只桌宠的透明度");opacity.setMax(90);opacity.setProgress(transparency);root.addView(opacity);opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){prefs.edit().putFloat("opacity",1-value/100f).apply();alpha.setText("透明度 · "+value+"%");}}});
        Switch chatter=option("随机小气泡","chatter",false);chatter.setEnabled(!character.chatter.isEmpty());
        text(root,character.chatter.isEmpty()?"这个角色暂未配置台词，后续可以单独补充。":"偶尔显示一句本地短句，气泡不会拦截触摸。",12,MUTED,false);
        Button say=button("看看台词气泡",false);root.addView(say,new LinearLayout.LayoutParams(-1,dp(44)));say.setEnabled(!character.chatter.isEmpty());say.setOnClickListener(view->send(PetService.SAY,null));
        int requested=prefs.getInt("size",128);TextView sizeLabel=text(root,"设置大小 · "+requested,15,INK,false);margin(sizeLabel,12,0);
        SeekBar size=new SeekBar(this);size.setContentDescription("这只桌宠的大小");size.setMax(120);size.setProgress(requested-72);root.addView(size);size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){prefs.edit().putInt("size",value+72).apply();sizeLabel.setText("设置大小 · "+(value+72));}}});
        Button gallery=button("选择这只的动作",false);root.addView(gallery,new LinearLayout.LayoutParams(-1,dp(44)));gallery.setOnClickListener(view->{String[] labels=new String[assets.actions.size()];for(int i=0;i<labels.length;i++)labels[i]=assets.actions.get(i).label;new AlertDialog.Builder(this).setTitle(store.name(selected)+"的小动作").setItems(labels,(dialog,index)->{String action=assets.actions.get(index).id;preview.play(action,false);if(PetService.running)send(PetService.PLAY,action);}).setNegativeButton("关闭",null).show();});
    }
    private Switch option(String label,String key,boolean fallback){Switch control=switchView(root,label,prefs.getBoolean(key,fallback));SharedPreferences target=prefs;control.setOnCheckedChangeListener((view,checked)->{if(!binding)target.edit().putBoolean(key,checked).apply();});return control;}
    private Switch switchView(LinearLayout parent,String label,boolean value){Switch control=new Switch(this);control.setText(label);control.setTextSize(16);control.setTextColor(INK);control.setPadding(0,dp(10),0,dp(10));control.setChecked(value);parent.addView(control);return control;}
    private void addPet(){
        List<PetCatalog.Character> list=PetCatalog.all(this);String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择角色").setItems(names,(dialog,index)->{
            int room=PetStore.MAX_PETS-store.ids().size();if(room<=0){toast("最多添加 "+PetStore.MAX_PETS+" 只");return;}
            NumberPicker number=new NumberPicker(this);number.setMinValue(1);number.setMaxValue(room);number.setValue(1);number.setWrapSelectorWheel(false);number.setContentDescription("添加数量");
            new AlertDialog.Builder(this).setTitle("添加"+names[index]).setMessage("选择添加数量，最多还能添加 "+room+" 只").setView(number).setPositiveButton("添加",(choice,which)->{store.add(list.get(index).id,number.getValue());physicsExpanded=false;render();}).setNegativeButton("取消",null).show();
        }).setNegativeButton("取消",null).show();
    }
    private void clearPets(){new AlertDialog.Builder(this).setTitle("清空全部桌宠？").setMessage("清空当前列表并收起全部桌宠，之后可以重新添加。").setPositiveButton("清空全部",(dialog,which)->{store.clear();stopService(new Intent(this,PetService.class));physicsExpanded=false;render();}).setNegativeButton("取消",null).show();}
    private void removePet(){String id=selected;new AlertDialog.Builder(this).setTitle("删除 "+store.name(id)+"？").setPositiveButton("删除",(dialog,which)->{store.remove(id);physicsExpanded=false;render();}).setNegativeButton("取消",null).show();}
    private void rename(){String id=selected;EditText input=new EditText(this);input.setSingleLine(true);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});input.setText(store.name(id));new AlertDialog.Builder(this).setTitle("桌宠名称").setView(input).setPositiveButton("保存",(dialog,which)->{String name=input.getText().toString().trim();if(!name.isEmpty()){store.prefs(id).edit().putString("name",name).apply();render();}}).setNegativeButton("取消",null).show();}
    private void send(String action,String animation){if(!PetService.running){toast("先启动桌宠，再操作这一只");return;}Intent intent=new Intent(this,PetService.class).setAction(action).putExtra("petId",selected);if(animation!=null)intent.putExtra("animation",animation);startService(intent);}
    private void requestStart(){
        boolean any=false;for(String id:store.ids())any|=store.prefs(id).getBoolean("visible",true);if(!any){toast("先打开至少一只桌宠的显示开关");return;}
        if(!Settings.canDrawOverlays(this)){awaitingOverlay=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception error){awaitingOverlay=false;toast("请在系统设置允许显示悬浮窗");}return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!store.global.getBoolean("askedNotification",false)){store.global.edit().putBoolean("askedNotification",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);return;}
        startPets();
    }
    private void startPets(){store.global.edit().putBoolean("enabled",true).apply();try{startForegroundService(new Intent(this,PetService.class).setAction(PetService.SYNC));status.postDelayed(this::updateStatus,400);}catch(RuntimeException error){store.global.edit().putBoolean("enabled",false).apply();toast("暂时无法启动，请保持应用在前台后重试");}}
    private void updateStatus(){if(status!=null)status.setText(!Settings.canDrawOverlays(this)?"首次启动需要允许显示悬浮窗":PetService.running&&store.global.getBoolean("enabled",false)?"当前显示 "+PetService.visibleCount+" 只桌宠":"桌宠已收起，可随时启动");}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==1)startPets();}
    @Override protected void onResume(){super.onResume();settingsVisible=true;if(store==null)return;render();if(awaitingOverlay&&Settings.canDrawOverlays(this)){awaitingOverlay=false;requestStart();}else if(store.global.getBoolean("enabled",false)&&Settings.canDrawOverlays(this))startPets();}
    @Override protected void onPause(){settingsVisible=false;if(preview!=null)preview.active(false);if(PetService.running)try{startService(new Intent(this,PetService.class).setAction(PetService.SYNC));}catch(RuntimeException ignored){}super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String id=intent.getStringExtra("petId");if(id!=null)store.select(id);physicsExpanded=false;render();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("awaitingOverlay",awaitingOverlay);out.putBoolean("physicsExpanded",physicsExpanded);super.onSaveInstanceState(out);}
    private void renderPhysics(){
        physicsPanel.removeAllViews();PhysicsOptions options=new PhysicsOptions(prefs.getFloat("physics.strength",1),prefs.getFloat("physics.elasticity",.78f),prefs.getFloat("physics.gravity",1400),prefs.getFloat("physics.friction",2.5f));
        physicsSlider("physics.strength","抛射力度",.25,2.5,.05,options.strength,1,"%.2f×");physicsSlider("physics.elasticity","反弹弹性",0,.95,.01,options.elasticity,100,"%.0f%%");physicsSlider("physics.gravity","重力",400,2800,50,options.gravity,1,"%.0f dp/s²");physicsSlider("physics.friction","地面摩擦",.5,6,.1,options.friction,1,"%.1f /s");
        Button reset=button("恢复这只的默认参数",false);physicsPanel.addView(reset,new LinearLayout.LayoutParams(-1,dp(44)));reset.setOnClickListener(view->{prefs.edit().putFloat("physics.strength",1).putFloat("physics.elasticity",.78f).putFloat("physics.gravity",1400).putFloat("physics.friction",2.5f).apply();renderPhysics();});
    }
    private void physicsSlider(String key,String title,double low,double high,double step,double value,double displayScale,String format){
        TextView label=text(physicsPanel,title+" · "+String.format(Locale.ROOT,format,value*displayScale),14,INK,true);margin(label,6,0);SeekBar slider=new SeekBar(this);slider.setContentDescription(title);slider.setMax((int)Math.round((high-low)/step));slider.setProgress((int)Math.round((value-low)/step));physicsPanel.addView(slider);SharedPreferences target=prefs;
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar view){}public void onStopTrackingTouch(SeekBar view){}public void onProgressChanged(SeekBar view,int progress,boolean user){if(user){double value=low+progress*step;target.edit().putFloat(key,(float)value).apply();label.setText(title+" · "+String.format(Locale.ROOT,format,value*displayScale));}}});
    }
    private void addRowButton(LinearLayout row,Button button,float weight){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),weight);p.setMargins(dp(2),0,dp(2),0);row.addView(button,p);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(text);return text;}
    private Button button(String text,boolean primary){Button button=new Button(this);button.setText(text);button.setTextSize(14);button.setAllCaps(false);button.setTextColor(primary?Color.WHITE:INK);button.setBackground(background(primary?ACCENT:0xffffeaf5,16));button.setStateListAnimator(null);button.setMinHeight(0);button.setMinWidth(0);return button;}
    private void margin(View view,int top,int bottom){LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)view.getLayoutParams();params.topMargin=dp(top);params.bottomMargin=dp(bottom);view.setLayoutParams(params);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
}
