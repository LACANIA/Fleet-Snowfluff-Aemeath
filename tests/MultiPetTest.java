import io.github.lacania.aemeathpocket.PetPacking;
import io.github.lacania.aemeathpocket.PetCollision;
import io.github.lacania.aemeathpocket.OverlayOpacity;

public final class MultiPetTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double energy(PetCollision.Body a,PetCollision.Body b){return .5*a.mass*(a.vx*a.vx+a.vy*a.vy)+.5*b.mass*(b.vx*b.vx+b.vy*b.vy);}
    private static PetCollision.Body body(double x,double vx,double mass,double elasticity){return new PetCollision.Body(x,50,100,120,vx,0,mass,elasticity,false);}
    public static void main(String[] args){
        int packings=0;
        for(int[] area:new int[][]{{320,480},{480,320},{1440,2560},{2560,1440},{200,200}})for(int count=1;count<=10;count++)for(int size:new int[]{72,128,192,576}){
            int[][] sizes=new int[count][2];for(int i=0;i<count;i++){sizes[i][0]=size-i%3*8;sizes[i][1]=(int)Math.round(sizes[i][0]*1.04);}
            PetPacking.Slot[] slots=PetPacking.pack(9,63,area[0],area[1],sizes,8);
            for(int i=0;i<count;i++){
                int w=Math.max(1,(int)Math.floor(sizes[i][0]*slots[i].scale)),h=Math.max(1,(int)Math.floor(sizes[i][1]*slots[i].scale));
                check(slots[i].x>=9&&slots[i].y>=63&&slots[i].x+w<=9+area[0]&&slots[i].y+h<=63+area[1],"Packed pet stays on screen");
                for(int j=0;j<i;j++){
                    int ow=Math.max(1,(int)Math.floor(sizes[j][0]*slots[j].scale)),oh=Math.max(1,(int)Math.floor(sizes[j][1]*slots[j].scale));
                    check(slots[i].x+w<=slots[j].x||slots[i].x>=slots[j].x+ow||slots[i].y+h<=slots[j].y||slots[i].y>=slots[j].y+oh,"Toggle reset must separate every full window");
                }
            }
            packings++;
        }
        for(int count=1;count<=20;count++)for(float opacity:new float[]{.1f,.5f,.75f,1f}){
            double combined=1-Math.pow(1-OverlayOpacity.alpha(opacity,count),count);check(combined<=.75001,"Overlapping pass-through windows stay within opacity budget");
        }
        PetCollision.Body a=body(0,600,1,.8),b=body(90,0,1,.8);double before=energy(a,b);check(PetCollision.resolve(a,b),"Approaching bodies collide");
        check(a.vx<b.vx&&energy(a,b)<=before+.001,"Contact transfers motion without creating energy");check(a.x+a.width<=b.x+.001,"Contact separates overlap");
        a=body(0,-100,1,.9);b=body(90,100,1,.9);PetCollision.resolve(a,b);check(a.vx==-100&&b.vx==100,"Separating pets are not bounced twice");
        a=body(0,600,1,0);b=body(90,0,1,.95);PetCollision.resolve(a,b);check(Math.abs(a.vx-b.vx)<.001,"Both pets' elasticity contributes");
        a=body(0,600,1,.25);b=body(90,0,1,.81);PetCollision.resolve(a,b);check(Math.abs(a.vx-165)<.001&&Math.abs(b.vx-435)<.001,"Geometric-mean contact elasticity");
        a=body(0,300,1,.8);a.held=true;b=body(90,0,1,.8);PetCollision.resolve(a,b);check(a.x==0&&a.vx==300&&b.vx>0,"Dragged pet stays attached to the finger");
        for(double mass:new double[]{1,2,9}){a=body(0,900,mass,.95);b=body(90,-100,1,.3);before=energy(a,b);PetCollision.resolve(a,b);check(energy(a,b)<=before+.001,"Different sizes cannot add collision energy");}
        check(PetPacking.pack(0,0,320,480,new int[0][2],8).length==0,"Empty roster is valid");
        System.out.println(packings+" safe non-overlapping layouts, opacity budgets and collision transfer cases: PASS");
    }
}
