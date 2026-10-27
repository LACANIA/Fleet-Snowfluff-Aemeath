import io.github.lacania.aemeathpocket.ThrowTrail;

public final class ThrowTrailTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        ThrowTrail trail=new ThrowTrail();
        check(trail.release(200)==null,"Empty trail");
        trail.add(100,0,0);trail.add(110,100,0);
        check(trail.release(110)==null,"Too short to estimate");trail.clear();
        trail.add(0,0,0);trail.add(50,10,0);trail.add(100,20,0);
        check(trail.release(100)==null,"Slow movement is a gentle placement");trail.clear();
        trail.add(0,0,0);trail.add(50,50,0);trail.add(100,100,0);
        double[] velocity=trail.release(100);
        check(velocity!=null&&Math.abs(velocity[0]-3600*(1-Math.exp(-1000.0/3600)))<1e-6&&velocity[1]==0,"Constant velocity matches dsh-pet soft limit");
        check(trail.release(251)==null,"Pause before release removes stale velocity");trail.clear();
        trail.add(0,0,0);trail.add(50,50,0);trail.add(100,0,0);
        check(trail.release(100)==null,"Roundtrip jitter has no throw direction");trail.clear();
        trail.add(0,0,0);trail.add(50,0,-40);trail.add(100,0,-200);
        velocity=trail.release(100);
        check(velocity!=null&&velocity[0]==0&&velocity[1]<-2000&&velocity[1]>-3600,"Acceleration gain and direction match source");trail.clear();
        trail.add(0,0,0);trail.add(50,100000,100000);trail.add(100,200000,200000);
        velocity=trail.release(100);
        check(velocity!=null&&Math.hypot(velocity[0],velocity[1])<=3600.000001,"Extreme movement retains soft cap");
        trail.clear();check(trail.release(101)==null,"Cancel removes previous gesture");
        trail.add(0,0,0);trail.add(50,100,0);trail.add(100,200,0);trail.add(400,200,0);
        check(trail.release(400)==null,"Old movement is trimmed");
        System.out.println("dsh-pet release speed, acceleration, stale samples, jitter and soft cap: PASS");
    }
}
