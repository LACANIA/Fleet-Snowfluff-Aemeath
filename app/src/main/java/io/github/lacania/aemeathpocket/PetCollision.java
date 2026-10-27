package io.github.lacania.aemeathpocket;

/** Contact separation is independent of flight. Only flight or optional walkers can launch peers. */
public final class PetCollision {
    public static final class Body {
        public double x,y,width,height,vx,vy,mass,elasticity;
        public boolean held,flying,walking;
        public Body(double x,double y,double width,double height,double vx,double vy,double mass,double elasticity,boolean held){this.x=x;this.y=y;this.width=width;this.height=height;this.vx=vx;this.vy=vy;this.mass=mass;this.elasticity=elasticity;this.held=held;}
    }
    private PetCollision(){}
    public static boolean resolve(Body a,Body b){
        return resolve(a,b,false);
    }
    public static boolean resolve(Body a,Body b,boolean walkKnocks){
        double overlapX=Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x),overlapY=Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y);
        if(!(overlapX>0&&overlapY>0))return false;
        boolean sourceA=walkKnocks&&a.walking&&!a.flying&&!a.held;
        // If two walkers meet, the first one keeps walking and the other can receive the impact.
        boolean sourceB=walkKnocks&&b.walking&&!b.flying&&!b.held&&!sourceA;
        boolean physical=a.flying||b.flying||sourceA||sourceB;
        double ia,ib;
        if(physical){ia=a.held||sourceA?0:1/Math.max(1,a.mass);ib=b.held||sourceB?0:1/Math.max(1,b.mass);}
        else{
            // A walking pet yields to an idle pet; positional contact must not disturb the idle action.
            boolean movingA=a.walking||Math.hypot(a.vx,a.vy)>0,movingB=b.walking||Math.hypot(b.vx,b.vy)>0;
            ia=a.held||!movingA&&movingB?0:1/Math.max(1,a.mass);
            ib=b.held||!movingB&&movingA?0:1/Math.max(1,b.mass);
        }
        if(ia+ib==0){
            // A held pet stays attached to the finger; an immovable peer blocks a walking source.
            ia=a.held?0:1/Math.max(1,a.mass);ib=b.held?0:1/Math.max(1,b.mass);physical=false;
        }
        double sum=ia+ib;if(sum==0)return false;
        double nx=0,ny=0,depth;
        if(overlapX<overlapY){nx=a.x+a.width/2<=b.x+b.width/2?1:-1;depth=overlapX;}else{ny=a.y+a.height/2<=b.y+b.height/2?1:-1;depth=overlapY;}
        // A pixel of clearance survives integer window rounding and prevents persistent tiny overlaps.
        double correction=depth+1;a.x-=nx*correction*ia/sum;a.y-=ny*correction*ia/sum;b.x+=nx*correction*ib/sum;b.y+=ny*correction*ib/sum;
        double approach=(a.vx-b.vx)*nx+(a.vy-b.vy)*ny;
        if(physical&&approach>0){
            double restitution=Math.sqrt(Math.max(0,Math.min(.95,a.elasticity))*Math.max(0,Math.min(.95,b.elasticity)));
            double impulse=(1+restitution)*approach/sum;
            a.vx-=impulse*ia*nx;a.vy-=impulse*ia*ny;b.vx+=impulse*ib*nx;b.vy+=impulse*ib*ny;
            if(ia>0)a.flying=true;if(ib>0)b.flying=true;
        }
        return true;
    }
}
