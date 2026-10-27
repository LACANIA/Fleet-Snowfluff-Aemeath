package io.github.lacania.aemeathpocket;

import java.util.ArrayList;
import java.util.List;

/**
 * Java port of dsh-pet physics.ts trimTrail / estimateReleaseVelocity / softClampSpeed.
 * Copyright (c) 2026 PC2005-cloud, MIT; see assets/DSH-PET-MIT.txt.
 * Samples use screen coordinates in dp so the source constants retain their meaning.
 */
public final class ThrowTrail {
    private static final class Sample {
        final long time;final double x,y;
        Sample(long time,double x,double y){this.time=time;this.x=x;this.y=y;}
    }
    private final List<Sample> trail=new ArrayList<>();
    public void clear(){trail.clear();}
    public void add(long time,double x,double y){
        if(!Double.isFinite(x)||!Double.isFinite(y))return;
        if(!trail.isEmpty()&&time<=trail.get(trail.size()-1).time)return;
        trail.add(new Sample(time,x,y));
        while(!trail.isEmpty()&&trail.get(0).time<time-200)trail.remove(0);
    }
    public double[] release(long now){
        return release(now,1);
    }
    public double[] release(long now,double strength){
        strength=new PhysicsOptions(strength,.78,1400,2.5).strength;
        if(trail.isEmpty()||now-trail.get(trail.size()-1).time>150)return null;
        List<Sample> win=new ArrayList<>();
        for(Sample sample:trail)if(now-sample.time>=0&&now-sample.time<=150)win.add(sample);
        if(win.size()<2)return null;
        Sample first=win.get(0),last=win.get(win.size()-1);
        long span=last.time-first.time;if(span<20)return null;
        double baseVx=(last.x-first.x)*1000/span,baseVy=(last.y-first.y)*1000/span;
        double baseSpeed=Math.hypot(baseVx,baseVy);if(baseSpeed<1e-6)return null;
        double peak=0,firstSpeed=0,lastSpeed=0;long firstEnd=0,lastEnd=0;
        Sample previous=first;int segments=0;
        for(int i=1;i<win.size();i++){
            Sample sample=win.get(i);long dt=sample.time-previous.time;
            if(dt>=8){
                double speed=Math.hypot(sample.x-previous.x,sample.y-previous.y)*1000/dt;
                peak=Math.max(peak,speed);lastSpeed=speed;lastEnd=sample.time;
                if(segments++==0){firstSpeed=speed;firstEnd=sample.time;}
                previous=sample;
            }
        }
        if(segments==0)peak=baseSpeed;
        double accel=segments<2?0:(lastSpeed-firstSpeed)/Math.max((lastEnd-firstEnd)/1000.0,.02);
        double before=(.5*baseSpeed+.5*peak)*(1+Math.min(Math.max(accel,0)/8000,1)*.6);
        double speed=3600*(1-Math.exp(-before/3600))*strength;
        if(speed<500)return null;
        return new double[]{baseVx/baseSpeed*speed,baseVy/baseSpeed*speed};
    }
}
