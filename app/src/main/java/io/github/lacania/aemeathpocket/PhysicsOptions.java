package io.github.lacania.aemeathpocket;

/** User options use dp-based units; defaults follow dsh-pet's shared physics. */
public final class PhysicsOptions {
    public final double strength,elasticity,gravity,friction;
    public PhysicsOptions(){this(1,.78,1400,2.5);}
    public PhysicsOptions(double strength,double elasticity,double gravity,double friction){
        this.strength=valid(strength,.25,2.5,1);
        this.elasticity=valid(elasticity,0,.95,.78);
        this.gravity=valid(gravity,400,2800,1400);
        this.friction=valid(friction,.5,6,2.5);
    }
    private static double valid(double value,double low,double high,double fallback){
        return Double.isFinite(value)?Math.max(low,Math.min(value,high)):fallback;
    }
}
