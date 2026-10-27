package io.github.lacania.aemeathpocket;

/** 松手时区分原地长按与拖动；一旦越过移动阈值，本次手势不会再打开菜单。 */
public final class PetGesture {
    public static final long MENU_HOLD_MS=600;
    private boolean active,dragging;
    private long started;
    private float x,y,slopSquared;
    public void begin(long time,float startX,float startY,float slop){
        active=true;dragging=false;started=time;x=startX;y=startY;slopSquared=Math.max(0,slop)*Math.max(0,slop);
    }
    public void move(float currentX,float currentY){
        if(!active)return;
        float dx=currentX-x,dy=currentY-y;
        if(dx*dx+dy*dy>slopSquared)dragging=true;
    }
    public boolean isDragging(){return dragging;}
    public boolean end(long time,float endX,float endY){
        move(endX,endY);
        boolean menu=active&&!dragging&&time-started>=MENU_HOLD_MS;
        active=false;
        return menu;
    }
    public void cancel(){active=false;dragging=false;}
}
