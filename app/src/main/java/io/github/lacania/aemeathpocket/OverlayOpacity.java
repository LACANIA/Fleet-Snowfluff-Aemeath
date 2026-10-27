package io.github.lacania.aemeathpocket;

/** Reserve a combined opacity budget for same-UID pass-through windows that may overlap. */
public final class OverlayOpacity {
    private OverlayOpacity(){}
    public static float alpha(float requested,int layers){
        if(!Float.isFinite(requested))requested=1;
        double limit=1-Math.pow(.25,1.0/Math.max(1,layers));
        return (float)Math.min(Math.max(.1f,Math.min(1f,requested)),limit);
    }
}
