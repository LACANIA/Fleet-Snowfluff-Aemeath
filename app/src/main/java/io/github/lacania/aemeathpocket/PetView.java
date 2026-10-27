package io.github.lacania.aemeathpocket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/** 原始透明帧按时间播放；隐藏、锁屏或移除视图后停止刷新。 */
final class PetView extends View {
    private final PetAssets assets;
    private final Paint paint=new Paint();
    private final Rect source=new Rect(),destination=new Rect();
    private PetAssets.Action action;
    private long started;
    private boolean looping=true,active=true;
    private final Runnable tick=new Runnable(){public void run(){if(active&&isAttachedToWindow()&&getVisibility()==VISIBLE){invalidate();postDelayed(this,50);}}};
    PetView(Context context){this(context,"aemeath");}
    PetView(Context context,String characterId){super(context);assets=PetAssets.get(context,characterId);paint.setFilterBitmap(false);play("idle",true);setContentDescription(PetCatalog.get(context,characterId).name+"，原地长按后松手打开菜单，按住拖动可换位置");}
    void play(String id,boolean loop){action=assets.action(id);looping=loop;started=SystemClock.uptimeMillis();invalidate();}
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
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
