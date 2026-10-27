package io.github.lacania.aemeathpocket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** 素材只解码一次，预览和悬浮窗共享；文件来自可重建的本地图集。 */
final class PetAssets {
    static final class Action {
        final String id, label;
        final int[] frames, durations;
        final int total;
        Action(JSONObject json) throws Exception {
            id=json.getString("id"); label=json.getString("label");
            JSONArray indices=json.getJSONArray("frames"), times=json.getJSONArray("durations");
            frames=new int[indices.length()];durations=new int[frames.length];
            int sum=0;
            for(int i=0;i<frames.length;i++){frames[i]=indices.getInt(i);durations[i]=times.getInt(i);sum+=durations[i];}
            total=sum;
        }
        int frame(long elapsed) {
            int position=(int)(Math.max(0,elapsed)%total);
            for(int i=0;i<frames.length;i++){if(position<durations[i])return frames[i];position-=durations[i];}
            return frames[frames.length-1];
        }
    }
    private static final Map<String,PetAssets> instances=new HashMap<>();
    final Bitmap bitmap;
    final List<Action> actions=new ArrayList<>();
    final int width,height,columns;
    static synchronized PetAssets get(Context context) {
        return get(context,"aemeath");
    }
    static synchronized PetAssets get(Context context,String characterId){
        PetCatalog.Character character=PetCatalog.get(context,characterId);PetAssets found=instances.get(character.id);
        if(found==null){found=new PetAssets(context.getApplicationContext(),character);instances.put(character.id,found);}return found;
    }
    private PetAssets(Context context,PetCatalog.Character character) {
        try {
            JSONObject data=new JSONObject(read(context,character.animations));
            width=data.getInt("cellWidth");height=data.getInt("cellHeight");columns=data.getInt("columns");
            JSONArray list=data.getJSONArray("actions");
            for(int i=0;i<list.length();i++)actions.add(new Action(list.getJSONObject(i)));
            try(InputStream input=context.getAssets().open(character.atlas)){bitmap=BitmapFactory.decodeStream(input);}
            if(bitmap==null)throw new IllegalStateException("Cannot decode pet atlas");
        }catch(Exception error){throw new IllegalStateException("Cannot load local pet assets",error);}
    }
    Action action(String id) {
        for(Action action:actions)if(action.id.equals(id))return action;
        return actions.get(0);
    }
    static String read(Context context,String file) throws Exception {
        try(InputStream input=context.getAssets().open(file);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
