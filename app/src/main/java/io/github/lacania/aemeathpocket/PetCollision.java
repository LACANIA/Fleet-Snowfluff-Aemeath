package io.github.lacania.aemeathpocket;

/** AABB contact, positional separation and mass-weighted normal impulse; no impulse when separating. */
public final class PetCollision {
    public static final class Body {
        public double x,y,width,height,vx,vy,mass,elasticity;
        public boolean held;
        public Body(double x,double y,double width,double height,double vx,double vy,double mass,double elasticity,boolean held){this.x=x;this.y=y;this.width=width;this.height=height;this.vx=vx;this.vy=vy;this.mass=mass;this.elasticity=elasticity;this.held=held;}
    }
    private PetCollision(){}
    public static boolean resolve(Body a,Body b){
        double overlapX=Math.min(a.x+a.width,b.x+b.width)-Math.max(a.x,b.x),overlapY=Math.min(a.y+a.height,b.y+b.height)-Math.max(a.y,b.y);
        if(!(overlapX>0&&overlapY>0))return false;
        double ia=a.held?0:1/Math.max(1,a.mass),ib=b.held?0:1/Math.max(1,b.mass),sum=ia+ib;if(sum==0)return false;
        double nx=0,ny=0,depth;
        if(overlapX<overlapY){nx=a.x+a.width/2<=b.x+b.width/2?1:-1;depth=overlapX;}else{ny=a.y+a.height/2<=b.y+b.height/2?1:-1;depth=overlapY;}
        double correction=depth+.01;a.x-=nx*correction*ia/sum;a.y-=ny*correction*ia/sum;b.x+=nx*correction*ib/sum;b.y+=ny*correction*ib/sum;
        double approach=(a.vx-b.vx)*nx+(a.vy-b.vy)*ny;
        if(approach>0){
            double restitution=Math.sqrt(Math.max(0,Math.min(.95,a.elasticity))*Math.max(0,Math.min(.95,b.elasticity)));
            double impulse=(1+restitution)*approach/sum;
            a.vx-=impulse*ia*nx;a.vy-=impulse*ia*ny;b.vx+=impulse*ib*nx;b.vy+=impulse*ib*ny;
        }
        return true;
    }
}
