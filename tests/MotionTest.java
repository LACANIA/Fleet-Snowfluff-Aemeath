import io.github.lacania.aemeathpocket.Motion;
public final class MotionTest {
    public static void main(String[] args){
        for(int low=0;low<100;low+=17){
            for(int high=low;high<2000;high+=61){
                for(int value=-500;value<3000;value+=23){
                    int clamped=Motion.clamp(value,low,high);
                    if(clamped<low||clamped>high)throw new AssertionError("Position outside bounds");
                    int restored=Motion.restore(Motion.fraction(value,low,high),low,high);
                    if(Math.abs(restored-clamped)>1)throw new AssertionError("Position roundtrip");
                }
            }
        }
        if(Motion.restore(Float.NaN,10,20)!=15)throw new AssertionError("NaN position");
        if(Motion.clamp(999,20,5)!=20)throw new AssertionError("Tiny display bounds");
        System.out.println("Position boundary and orientation restoration properties: PASS");
    }
}
