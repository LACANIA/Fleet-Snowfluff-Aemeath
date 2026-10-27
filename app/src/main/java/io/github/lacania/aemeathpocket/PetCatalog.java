package io.github.lacania.aemeathpocket;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Packaged characters, independent from the number of live instances. */
final class PetCatalog {
    static final class Character {
        final String id,name,animations,atlas,chatter;
        final double[] body=new double[4];
        Character(JSONObject data) throws Exception {
            id=data.getString("id");name=data.getString("name");animations=data.getString("animations");atlas=data.getString("atlas");chatter=data.optString("chatter","");
            JSONArray box=data.getJSONArray("body");for(int i=0;i<4;i++)body[i]=box.getDouble(i);
            if(!(body[0]>=0&&body[1]>=0&&body[2]<=1&&body[3]<=1&&body[0]<body[2]&&body[1]<body[3]))throw new IllegalArgumentException("Invalid body box");
        }
    }
    private static List<Character> characters;
    static synchronized List<Character> all(Context context){
        if(characters==null){
            try{
                List<Character> loaded=new ArrayList<>();JSONArray data=new JSONObject(PetAssets.read(context,"characters.json")).getJSONArray("characters");
                for(int i=0;i<data.length();i++)loaded.add(new Character(data.getJSONObject(i)));
                try{
                    JSONArray local=new JSONObject(PetAssets.read(context,"characters-local.json")).getJSONArray("characters");
                    for(int i=0;i<local.length();i++){
                        Character item=new Character(local.getJSONObject(i));boolean duplicate=false;for(Character prior:loaded)if(prior.id.equals(item.id))duplicate=true;if(!duplicate)loaded.add(item);
                    }
                }catch(java.io.FileNotFoundException ignored){}
                if(loaded.isEmpty())throw new IllegalArgumentException("Empty character catalog");characters=Collections.unmodifiableList(loaded);
            }catch(Exception error){throw new IllegalStateException("Cannot load character catalog",error);}
        }
        return characters;
    }
    static Character get(Context context,String id){for(Character character:all(context))if(character.id.equals(id))return character;return all(context).get(0);}
}
