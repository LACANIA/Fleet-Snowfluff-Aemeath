package io.github.lacania.aemeathpocket;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ordered instance registry. Legacy settings are copied once and never erased. */
final class PetStore {
    static final int MAX_PETS=10;
    final SharedPreferences global;
    private final Context context;
    PetStore(Context context){
        this.context=context.getApplicationContext();global=this.context.getSharedPreferences("flights",Context.MODE_PRIVATE);
        if(!global.contains("schema")){
            SharedPreferences legacy=this.context.getSharedPreferences("pet",Context.MODE_PRIVATE);
            String id="legacy";SharedPreferences.Editor editor=prefs(id).edit();
            for(Map.Entry<String,?> entry:legacy.getAll().entrySet())copy(editor,entry.getKey(),entry.getValue());
            editor.putString("character","aemeath").putString("name","爱弥斯 1").putBoolean("visible",true).commit();
            global.edit().putString("instances",new JSONArray().put(id).toString()).putString("selected",id).putInt("serial",1).putBoolean("enabled",legacy.getBoolean("enabled",false)).putBoolean("allowOverlap",true).putInt("layoutRevision",0).putInt("schema",1).commit();
        }
    }
    SharedPreferences prefs(String id){return context.getSharedPreferences("pet_instance_"+id,Context.MODE_PRIVATE);}
    List<String> ids(){
        List<String> ids=new ArrayList<>();
        try{JSONArray data=new JSONArray(global.getString("instances","[]"));for(int i=0;i<data.length();i++){String id=data.getString(i);if(id.matches("[A-Za-z0-9-]{1,64}")&&!ids.contains(id))ids.add(id);}}catch(Exception ignored){}
        return ids;
    }
    String selected(){List<String> ids=ids();String id=global.getString("selected","");return ids.contains(id)?id:ids.isEmpty()?"":ids.get(0);}
    void select(String id){if(ids().contains(id))global.edit().putString("selected",id).apply();}
    String name(String id){return prefs(id).getString("name",PetCatalog.get(context,prefs(id).getString("character","aemeath")).name);}
    List<String> add(String characterId,int count){
        PetCatalog.Character character=PetCatalog.get(context,characterId);List<String> ids=ids(),added=new ArrayList<>();
        int serial=global.getInt("serial",ids.size());count=Math.max(0,Math.min(count,MAX_PETS-ids.size()));
        for(int i=0;i<count;i++){
            String id=UUID.randomUUID().toString();serial++;
            prefs(id).edit().putString("character",character.id).putString("name",character.name+" "+serial).putBoolean("visible",true).putFloat("x",.5f).putFloat("y",.5f).commit();ids.add(id);added.add(id);
        }
        if(!added.isEmpty())global.edit().putString("instances",new JSONArray(ids).toString()).putInt("serial",serial).putString("selected",added.get(added.size()-1)).putInt("layoutRevision",revision()+1).apply();
        return added;
    }
    void remove(String id){
        List<String> ids=ids();if(!ids.remove(id))return;
        // A removed instance's small preference file remains local, so a late callback cannot write into another pet.
        global.edit().putString("instances",new JSONArray(ids).toString()).putString("selected",ids.isEmpty()?"":ids.get(0)).putInt("layoutRevision",revision()+1).apply();
    }
    int revision(){return global.getInt("layoutRevision",0);}
    void clear(){global.edit().putString("instances","[]").putString("selected","").putBoolean("enabled",false).putInt("layoutRevision",revision()+1).apply();}
    void arrange(){global.edit().putInt("layoutRevision",revision()+1).apply();}
    void overlap(boolean allow){if(global.getBoolean("allowOverlap",true)!=allow)global.edit().putBoolean("allowOverlap",allow).putInt("layoutRevision",revision()+1).apply();}
    private static void copy(SharedPreferences.Editor editor,String key,Object value){
        if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof String)editor.putString(key,(String)value);
    }
}
