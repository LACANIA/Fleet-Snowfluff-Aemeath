import io.github.lacania.aemeathpocket.PhysicsOptions;
import io.github.lacania.aemeathpocket.ThrowMotion;
import io.github.lacania.aemeathpocket.ThrowTrail;

public final class PhysicsOptionsTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        PhysicsOptions options=new PhysicsOptions(Double.NaN,Double.POSITIVE_INFINITY,-9,999);
        check(options.strength==1&&options.elasticity==.78&&options.gravity==400&&options.friction==6,"Invalid settings are bounded or replaced by defaults");
        options=new PhysicsOptions();check(options.strength==1&&options.elasticity==.78&&options.gravity==1400&&options.friction==2.5,"Source defaults");
        ThrowTrail trail=new ThrowTrail();trail.add(0,0,0);trail.add(50,50,0);trail.add(100,100,0);
        check(Math.abs(trail.release(100,2)[0]-2*trail.release(100,1)[0])<1e-6,"Strength scales release speed");
        ThrowMotion light=new ThrowMotion(),heavy=new ThrowMotion();
        light.start(100,100,800,0,0,5000,0,5000,1,new PhysicsOptions(1,.78,400,2.5));
        heavy.start(100,100,800,0,0,5000,0,5000,1,new PhysicsOptions(1,.78,2800,2.5));
        for(int i=0;i<5;i++){light.step(.05);heavy.step(.05);}
        check(heavy.y()>light.y(),"Gravity changes downward acceleration");
        ThrowMotion soft=new ThrowMotion(),springy=new ThrowMotion();
        soft.start(95,100,800,0,0,100,0,5000,1,new PhysicsOptions(1,0,1400,2.5));
        springy.start(95,100,800,0,0,100,0,5000,1,new PhysicsOptions(1,.95,1400,2.5));
        soft.step(.05);springy.step(.05);soft.step(.05);springy.step(.05);
        check(springy.x()<soft.x(),"Elasticity changes reflected motion");
        ThrowMotion slippery=new ThrowMotion(),rough=new ThrowMotion();
        slippery.start(100,500,1000,0,0,5000,0,500,1,new PhysicsOptions(1,0,1400,.5));
        rough.start(100,500,1000,0,0,5000,0,500,1,new PhysicsOptions(1,0,1400,6));
        for(int i=0;i<10;i++){slippery.step(.05);rough.step(.05);}
        check(slippery.x()>rough.x(),"Ground friction changes sliding distance");
        // Rotation cancels the old throw; the next throw uses the new screen's lower edge.
        ThrowMotion rotated=new ThrowMotion();rotated.start(900,600,900,100,9,1100,63,2200,1);rotated.stop();
        rotated.start(300,400,800,0,9,2200,63,700,1);
        for(int i=0;i<1000&&rotated.isActive();i++){
            rotated.step(.016);check(rotated.y()<=700&&rotated.x()<=2200,"Rotated screen uses fresh bounds");
        }
        check(rotated.y()==700,"Current screen's lower edge is the floor");
        System.out.println("Four parameter effects, default restoration, invalid values and rotated floor: PASS");
    }
}
