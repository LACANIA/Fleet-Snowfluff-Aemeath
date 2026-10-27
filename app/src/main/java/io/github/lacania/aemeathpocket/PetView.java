package io.github.lacania.aemeathpocket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/** 原始透明帧按时间播放；隐藏、锁屏或移除视图后停止刷新。 */
final class PetView extends View {
    static final int LOCATION_MS=1000;
    private final PetAssets assets;
    private final Paint paint=new Paint();
    private final Paint marker=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint labelPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source=new Rect(),destination=new Rect();
    private PetAssets.Action action;
    private long started;
    private long locateUntil;
    private String locateLabel="";
    private boolean looping=true,active=true;
    private final Runnable tick=new Runnable(){public void run(){if(active&&isAttachedToWindow()&&getVisibility()==VISIBLE){invalidate();postDelayed(this,50);}}};
    PetView(Context context){this(context,"aemeath");}
    PetView(Context context,String characterId){super(context);assets=PetAssets.get(context,characterId);paint.setFilterBitmap(false);play("idle",true);setContentDescription(PetCatalog.get(context,characterId).name+"，原地长按后松手打开菜单，按住拖动可换位置");}
    void play(String id,boolean loop){action=assets.action(id);looping=loop;started=SystemClock.uptimeMillis();invalidate();}
    String actionId(){return action.id;}
    long actionStarted(){return started;}
    long locateUntil(){return locateUntil;}
    void locate(String label){locateLabel=label;locateUntil=SystemClock.uptimeMillis()+LOCATION_MS;invalidate();}
    void clearLocate(){locateUntil=0;invalidate();}
    void active(boolean value){active=value;removeCallbacks(tick);if(value&&isAttachedToWindow())post(tick);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(tick);if(active)post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        long elapsed=SystemClock.uptimeMillis()-started;
        if(!looping&&elapsed>=action.total){play("idle",true);elapsed=0;}
        int index=action.frame(elapsed),x=(index%assets.columns)*assets.width,y=(index/assets.columns)*assets.height;
        source.set(x,y,x+assets.width,y+assets.height);destination.set(0,0,getWidth(),getHeight());
        canvas.drawBitmap(assets.bitmap,source,destination,paint);
        if(SystemClock.uptimeMillis()<locateUntil){
            float density=getResources().getDisplayMetrics().density,inset=3*density;
            marker.setStyle(Paint.Style.STROKE);marker.setStrokeWidth(3*density);
            marker.setColor((SystemClock.uptimeMillis()/300)%2==0?0xffad548a:0xffffffff);
            canvas.drawRoundRect(inset,inset,getWidth()-inset,getHeight()-inset,10*density,10*density,marker);
            labelPaint.setTextSize(12*density);labelPaint.setColor(0xffffffff);
            String label=android.text.TextUtils.ellipsize(locateLabel,labelPaint,Math.max(1,getWidth()-16*density),android.text.TextUtils.TruncateAt.END).toString();
            float width=Math.min(getWidth()-2*inset,labelPaint.measureText(label)+10*density),height=20*density,left=(getWidth()-width)/2;
            marker.setStyle(Paint.Style.FILL);marker.setColor(0xffad548a);
            canvas.drawRoundRect(left,inset,left+width,inset+height,5*density,5*density,marker);
            canvas.drawText(label,(getWidth()-labelPaint.measureText(label))/2,inset+height/2-(labelPaint.ascent()+labelPaint.descent())/2,labelPaint);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
