package io.github.lacania.seed;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Writes only to the independent test application's private legacy preferences. */
public final class SeedActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getSharedPreferences("pet",MODE_PRIVATE).edit().putInt("size",156).putBoolean("enabled",true).putBoolean("roam",false).putBoolean("pass",false).putBoolean("throw",true).putBoolean("chatter",false).putFloat("opacity",.85f).putFloat("physics.strength",1.65f).putFloat("physics.elasticity",.63f).putFloat("physics.gravity",1600).putFloat("physics.friction",3.1f).putFloat("x",.25f).putFloat("y",.65f).commit();
        TextView text=new TextView(this);text.setText("Legacy settings prepared");setContentView(text);
    }
}
