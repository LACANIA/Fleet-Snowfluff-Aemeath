import io.github.lacania.aemeathpocket.SpeechPlacement;

public final class SpeechPlacementTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int cases=0;
        for(int[] area:new int[][]{{9,63,1431,2551},{9,63,2551,1431},{0,0,320,480},{0,0,480,320}}){
            int left=area[0],top=area[1],right=area[2],bottom=area[3];
            for(int x=left;x<=right-120;x+=37)for(int y=top;y<=bottom-130;y+=41){
                int[] p=SpeechPlacement.place(x,y,120,130,160,45,left,top,right,bottom,6);
                if(p!=null){
                    check(p[0]>=left&&p[1]>=top&&p[0]+160<=right&&p[1]+45<=bottom,"Bubble stays in safe area");
                    check(p[0]+160<=x||p[0]>=x+120||p[1]+45<=y||p[1]>=y+130,"Bubble must never overlap the pet window");
                }
                cases++;
            }
        }
        check(SpeechPlacement.place(0,0,100,100,50,20,0,0,100,100,6)==null,"Hide when no separate space exists");
        check(SpeechPlacement.place(0,0,10,10,101,20,0,0,100,100,6)==null,"Oversized bubble is hidden");
        System.out.println(cases+" speech positions across portrait, landscape and small displays: PASS");
    }
}
