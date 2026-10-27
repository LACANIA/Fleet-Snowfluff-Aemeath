package io.github.lacania.aemeathpocket;

/** Full-window grid packing, with a temporary display scale when requested sizes cannot fit. */
public final class PetPacking {
    public static final class Slot {public final int x,y;public final double scale;Slot(int x,int y,double scale){this.x=x;this.y=y;this.scale=scale;}}
    private PetPacking(){}
    public static Slot[] pack(int left,int top,int width,int height,int[][] sizes,int gap){
        int count=sizes.length;Slot[] result=new Slot[count];if(count==0)return result;
        width=Math.max(1,width);height=Math.max(1,height);gap=Math.max(0,Math.min(gap,Math.min(width,height)/Math.max(1,count)));
        int widest=1,tallest=1;for(int[] size:sizes){widest=Math.max(widest,size[0]);tallest=Math.max(tallest,size[1]);}
        int columns=1;double best=-1;
        for(int cols=1;cols<=count;cols++){
            int rows=(count+cols-1)/cols;double cellW=(width-(cols-1)*gap)/(double)cols,cellH=(height-(rows-1)*gap)/(double)rows;
            double scale=Math.min(1,Math.min(cellW/widest,cellH/tallest));
            if(scale>best+1e-9){best=scale;columns=cols;}
        }
        int rows=(count+columns-1)/columns;double cellW=(width-(columns-1)*gap)/(double)columns,cellH=(height-(rows-1)*gap)/(double)rows;
        for(int i=0;i<count;i++){
            int w=Math.max(1,(int)Math.floor(sizes[i][0]*best)),h=Math.max(1,(int)Math.floor(sizes[i][1]*best));
            int x=left+(int)Math.floor((i%columns)*(cellW+gap)+(cellW-w)/2),y=top+(int)Math.floor((i/columns)*(cellH+gap)+(cellH-h)/2);
            result[i]=new Slot(x,y,Math.max(.001,best));
        }
        return result;
    }
}
