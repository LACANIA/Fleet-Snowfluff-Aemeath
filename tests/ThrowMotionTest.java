import io.github.lacania.aemeathpocket.ThrowMotion;

public final class ThrowMotionTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        ThrowMotion motion=new ThrowMotion();
        check(!motion.start(50,50,50,10,0,100,0,100,1),"Slow drag must stop at release");
        check(!motion.start(50,50,Double.NaN,100,0,100,0,100,1),"Reject invalid velocity");
        check(!motion.start(50,50,1000,1000,50,50,50,50,1),"No movement area");
        check(!motion.start(50,50,200,0,0,100,0,100,3),"Velocity threshold follows display density");
        check(motion.start(95,30,600,0,0,100,0,100,1),"Fast drag starts motion");
        motion.step(.05);int wall=motion.x();motion.step(.05);
        check(motion.x()<wall,"Right edge reverses horizontal motion");
        motion.start(50,95,0,800,0,100,0,100,1);motion.step(.025);int floor=motion.y();motion.step(.05);
        check(motion.y()<floor,"Floor collision bounces upwards");
        motion.start(50,5,0,-800,0,100,0,100,1);motion.step(.025);int ceiling=motion.y();motion.step(.05);
        check(motion.y()>ceiling,"Top collision reverses vertical motion");
        motion.start(50,50,1000,-500,0,100,0,100,1);motion.stop();int stoppedX=motion.x(),stoppedY=motion.y();motion.step(.1);
        check(!motion.isActive()&&motion.x()==stoppedX&&motion.y()==stoppedY,"Touch or setting change cancels motion");
        int trajectories=0;
        for(double density:new double[]{1,2.75})for(int vx:new int[]{-100000,-800,800,100000})for(int vy:new int[]{-100000,-500,0,100000})for(double dt:new double[]{.016,.05,.1}){
            int maxX=(int)(340*density),maxY=(int)(760*density);
            motion.start(maxX/2,maxY/3,vx*density,vy*density,9,maxX,63,maxY,density);
            int steps=0;
            while(motion.isActive()&&steps++<1000){
                motion.step(dt);
                check(motion.x()>=9&&motion.x()<=maxX&&motion.y()>=63&&motion.y()<=maxY,"Whole overlay stays within supplied bounds");
            }
            check(!motion.isActive(),"Damping or time limit ends motion");trajectories++;
        }
        motion.start(50,50,600,600,0,100,0,100,1);int x=motion.x(),y=motion.y();
        motion.step(Double.NaN);motion.step(-1);motion.step(0);
        check(motion.x()==x&&motion.y()==y,"Invalid elapsed time does not move the pet");
        motion.start(50,50,600,600,0,100,0,100,1);motion.step(100);
        check(motion.isActive(),"A delayed frame is subdivided and limited");
        System.out.println("Throw thresholds, edge bounces, cancellation and "+trajectories+" bounded trajectories: PASS");
    }
}
