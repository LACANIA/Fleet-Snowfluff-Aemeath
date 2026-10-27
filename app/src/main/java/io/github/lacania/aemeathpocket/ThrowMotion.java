package io.github.lacania.aemeathpocket;

/**
 * Android port of dsh-pet/src/shared/physics.ts: throwStep and throwSubsteps.
 * Copyright (c) 2026 PC2005-cloud, MIT; see assets/DSH-PET-MIT.txt.
 * Android additions: dp scaling, whole-overlay bounds, cancellation and time limit.
 */
public final class ThrowMotion {
    private double x,y,vx,vy,minX,maxX,minY,maxY,scale,elapsed;
    private boolean active;
    private PhysicsOptions options=new PhysicsOptions();

    public boolean start(double x,double y,double vx,double vy,int minX,int maxX,int minY,int maxY,double density){
        return start(x,y,vx,vy,minX,maxX,minY,maxY,density,new PhysicsOptions());
    }
    public boolean start(double x,double y,double vx,double vy,int minX,int maxX,int minY,int maxY,double density,PhysicsOptions options){
        active=false;
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(vx)||!Double.isFinite(vy))return false;
        scale=Double.isFinite(density)&&density>0?density:1;
        this.options=options==null?new PhysicsOptions():options;
        this.minX=minX;this.maxX=Math.max(minX,maxX);this.minY=minY;this.maxY=Math.max(minY,maxY);
        this.x=clamp(x,this.minX,this.maxX);this.y=clamp(y,this.minY,this.maxY);
        double cap=3600*scale*this.options.strength,speed=Math.hypot(vx,vy),ratio=speed>cap?cap/speed:1;
        this.vx=vx*ratio;this.vy=vy*ratio;
        if(this.minX==this.maxX)this.vx=0;
        if(this.minY==this.maxY)this.vy=0;
        elapsed=0;active=Math.hypot(this.vx,this.vy)>=500*scale;
        return active;
    }

    public boolean step(double seconds){
        if(!active||!Double.isFinite(seconds)||seconds<=0)return active;
        double frame=Math.min(seconds,.05);elapsed+=frame;
        int substeps=Math.min(64,Math.max(1,(int)Math.ceil(Math.hypot(vx,vy)*frame/(8*scale))));
        double dt=frame/substeps;
        for(int i=0;i<substeps&&active;i++){
            if(maxY>minY)vy+=options.gravity*scale*dt;
            x+=vx*dt;y+=vy*dt;
            boolean bounced=false;
            if(x<minX){x=minX;vx=Math.abs(vx)*options.elasticity;bounced=true;}
            else if(x>maxX){x=maxX;vx=-Math.abs(vx)*options.elasticity;bounced=true;}
            if(y<minY){y=minY;vy=Math.abs(vy)*options.elasticity;bounced=true;}
            else if(y>=maxY){
                y=maxY;vx*=Math.max(0,1-options.friction*dt);
                vy=Math.abs(vy)<40*scale?0:-Math.abs(vy)*options.elasticity;bounced=true;
            }
            if(maxY==minY){y=minY;vy=0;}
            active=!((y>=maxY-scale&&Math.abs(vy)<scale&&Math.abs(vx)<15*scale)||(bounced&&Math.hypot(vx,vy)<40*scale&&Math.abs(vy)<scale));
        }
        if(!active||elapsed>=12){active=false;vx=0;vy=0;}
        return active;
    }
    public void stop(){active=false;vx=0;vy=0;}
    public boolean isActive(){return active;}
    public int x(){return (int)Math.round(x);}
    public int y(){return (int)Math.round(y);}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(value,high));}
}
