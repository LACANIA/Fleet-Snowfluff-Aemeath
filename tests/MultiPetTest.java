import io.github.lacania.aemeathpocket.PetPacking;
import io.github.lacania.aemeathpocket.PetCollision;
import io.github.lacania.aemeathpocket.OverlayOpacity;

public final class MultiPetTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static double energy(PetCollision.Body a,PetCollision.Body b){return .5*a.mass*(a.vx*a.vx+a.vy*a.vy)+.5*b.mass*(b.vx*b.vx+b.vy*b.vy);}
    private static PetCollision.Body body(double x,double vx,double mass,double elasticity){PetCollision.Body body=new PetCollision.Body(x,50,100,120,vx,0,mass,elasticity,false);body.flying=true;return body;}
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
            double focus=OverlayOpacity.locating(opacity,count,true),others=OverlayOpacity.locating(opacity,count,false);
            combined=1-(1-focus)*Math.pow(1-others,count-1);
            check(combined<=.75001&&focus>=.6,"Temporary locating emphasis preserves the combined pass-through budget");
        }
        PetCollision.Body a=body(0,600,1,.8),b=body(90,0,1,.8);double before=energy(a,b);check(PetCollision.resolve(a,b),"Approaching bodies collide");
        check(a.vx<b.vx&&energy(a,b)<=before+.001,"Contact transfers motion without creating energy");check(a.x+a.width<=b.x+.001,"Contact separates overlap");
        a=body(0,-100,1,.9);b=body(90,100,1,.9);PetCollision.resolve(a,b);check(a.vx==-100&&b.vx==100,"Separating pets are not bounced twice");
        a=body(0,600,1,0);b=body(90,0,1,.95);PetCollision.resolve(a,b);check(Math.abs(a.vx-b.vx)<.001,"Both pets' elasticity contributes");
        a=body(0,600,1,.25);b=body(90,0,1,.81);PetCollision.resolve(a,b);check(Math.abs(a.vx-165)<.001&&Math.abs(b.vx-435)<.001,"Geometric-mean contact elasticity");
        a=body(0,300,1,.8);a.held=true;b=body(90,0,1,.8);PetCollision.resolve(a,b);check(a.x==0&&a.vx==300&&b.vx>0,"Dragged pet stays attached to the finger");
        for(double mass:new double[]{1,2,9}){a=body(0,900,mass,.95);b=body(90,-100,1,.3);before=energy(a,b);PetCollision.resolve(a,b);check(energy(a,b)<=before+.001,"Different sizes cannot add collision energy");}
        a=body(0,38,1,.8);b=body(90,0,1,.8);a.flying=b.flying=false;a.walking=true;
        check(PetCollision.resolve(a,b),"Normal walking still contacts");
        check(!a.flying&&!b.flying&&b.x==90&&b.y==50&&b.vx==0&&b.vy==0,"Default walking cannot move or launch an idle peer");
        check(a.x+a.width<=b.x,"Walker is blocked without resetting the idle peer");
        a=body(0,0,1,.8);b=body(99.7,0,1,.8);a.flying=b.flying=false;
        PetCollision.resolve(a,b);a.x=Math.round(a.x);b.x=Math.round(b.x);
        check(!PetCollision.resolve(a,b)&&!a.flying&&!b.flying,"Integer rounding cannot keep tiny idle contacts alive");
        a=body(0,300,1,.8);a.held=true;b=body(90,0,1,.8);a.flying=b.flying=false;
        PetCollision.resolve(a,b);check(a.x==0&&!a.flying&&!b.flying&&b.vx==0,"A held drag is not a thrown launch");
        a=body(0,38,1,.8);b=body(90,0,1,.8);a.flying=b.flying=false;a.walking=true;
        PetCollision.resolve(a,b,true);
        check(a.x==0&&a.vx==38&&!a.flying&&a.walking&&b.flying&&b.vx>38,"Optional walker continues while only the target launches");
        io.github.lacania.aemeathpocket.ThrowMotion flight=new io.github.lacania.aemeathpocket.ThrowMotion();
        flight.collision(b.x,b.y,b.vx,b.vy,0,1000,0,1000,1,new io.github.lacania.aemeathpocket.PhysicsOptions());
        check(flight.isActive(),"Low-speed walking impact uses collision launch rather than the release dead zone");
        flight.step(.05);check(flight.y()>b.y,"A launched target experiences gravity");
        PetCollision.Body c=body(b.x+90,0,1,.8);c.flying=false;
        PetCollision.resolve(b,c);check(c.flying&&c.vx>0,"Launched targets propagate ordinary flight collisions with walking mode disabled");
        a=body(0,38,1,.8);b=body(90,0,1,.8);a.flying=b.flying=false;a.walking=true;b.held=true;
        check(PetCollision.resolve(a,b,true)&&a.x<0&&!a.flying&&!b.flying,"An immovable target blocks a walker without deadlocking flight mode");
        check(PetPacking.pack(0,0,320,480,new int[0][2],8).length==0,"Empty roster is valid");
        System.out.println(packings+" safe non-overlapping layouts, opacity budgets, ordinary contact and optional walking-launch chains: PASS");
    }
}
