package io.github.lacania.overlayprobe;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;

/** Separate Android UID, no permissions, no overlay-hiding flag, no network. */
public final class ProbeActivity extends Activity {
    private int taps;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null)taps=state.getInt("taps");
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xffe6f0fa);
        Button button=new Button(this);button.setContentDescription("Overlay probe target");button.setText("Tap count: "+taps);
        root.addView(button,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        button.setOnClickListener(view->{taps++;button.setText("Tap count: "+taps);});setContentView(root);
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("taps",taps);super.onSaveInstanceState(out);}
}
