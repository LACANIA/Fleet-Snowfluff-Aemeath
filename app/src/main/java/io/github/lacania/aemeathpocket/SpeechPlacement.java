package io.github.lacania.aemeathpocket;

/** Place a non-interactive bubble fully on screen without overlapping the pet. */
public final class SpeechPlacement {
    private SpeechPlacement(){}
    public static int[] place(int x,int y,int width,int height,int bubbleW,int bubbleH,int left,int top,int right,int bottom,int gap){
        if(bubbleW<=0||bubbleH<=0||bubbleW>right-left||bubbleH>bottom-top)return null;
        int centeredX=Motion.clamp(x+width/2-bubbleW/2,left,right-bubbleW);
        int centeredY=Motion.clamp(y+height/2-bubbleH/2,top,bottom-bubbleH);
        if(y-gap-bubbleH>=top)return new int[]{centeredX,y-gap-bubbleH};
        if(y+height+gap+bubbleH<=bottom)return new int[]{centeredX,y+height+gap};
        if(x-gap-bubbleW>=left)return new int[]{x-gap-bubbleW,centeredY};
        if(x+width+gap+bubbleW<=right)return new int[]{x+width+gap,centeredY};
        return null;
    }
}
