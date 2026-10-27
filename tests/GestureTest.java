import io.github.lacania.aemeathpocket.PetGesture;

public final class GestureTest {
    private static int passed;
    private static PetGesture start(){PetGesture gesture=new PetGesture();gesture.begin(1000,100,100,10);return gesture;}
    private static void expect(String name,boolean expected,boolean actual){
        if(expected!=actual)throw new AssertionError(name+": expected "+expected+" but got "+actual);
        passed++;
    }
    public static void main(String[] args){
        expect("short tap",false,start().end(1120,100,100));
        expect("just below hold time",false,start().end(1599,100,100));
        expect("hold time reached",true,start().end(1600,100,100));
        expect("long stationary hold",true,start().end(5000,100,100));
        expect("small hand tremor",true,start().end(1800,104,105));
        expect("at movement threshold",true,start().end(1800,106,108));
        expect("past movement threshold",false,start().end(1800,107,108));
        PetGesture returned=start();returned.move(125,100);returned.move(100,100);
        expect("dragging back to start stays a drag",false,returned.end(2200,100,100));
        PetGesture slow=start();slow.move(101,100);slow.move(130,100);
        expect("hold then drag",false,slow.end(4000,130,100));
        expect("release with a distant final position",false,start().end(2400,400,200));
        PetGesture cancel=start();cancel.cancel();expect("cancelled hold",false,cancel.end(2000,100,100));
        PetGesture reused=start();reused.move(140,100);reused.end(2000,140,100);reused.begin(3000,200,200,10);
        expect("new gesture clears earlier dragging",true,reused.end(3600,200,200));
        expect("release without press",false,new PetGesture().end(9000,100,100));
        expect("clock cannot run backwards",false,start().end(999,100,100));
        PetGesture once=start();expect("first release",true,once.end(2000,100,100));expect("second release cannot repeat menu",false,once.end(3000,100,100));
        System.out.println(passed+" stationary-hold versus drag cases: PASS");
    }
}
