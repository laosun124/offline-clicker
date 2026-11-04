package local.offline.clicker;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Store {
    private final SharedPreferences prefs;
    public Store(Context context){prefs=context.getSharedPreferences("offline_plans",Context.MODE_PRIVATE);}
    public void observe(SharedPreferences.OnSharedPreferenceChangeListener listener){prefs.registerOnSharedPreferenceChangeListener(listener);}
    public void stopObserving(SharedPreferences.OnSharedPreferenceChangeListener listener){prefs.unregisterOnSharedPreferenceChangeListener(listener);}
    public List<Plan> all(){
        List<Plan> plans=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("plans","[]"));for(int i=0;i<a.length();i++)plans.add(decode(a.getJSONObject(i)));}
        catch(JSONException|IllegalArgumentException ex){throw new IllegalStateException("方案数据无法读取，请先导出备份或检查存储",ex);}
        if(plans.isEmpty()){Plan p=new Plan();p.name="默认方案";plans.add(p);saveAll(plans);prefs.edit().putString("selected",p.id).apply();}
        return plans;
    }
    public Plan selected(){List<Plan> ps=all();String id=prefs.getString("selected","");for(Plan p:ps)if(p.id.equals(id))return p;return ps.get(0);}
    public void select(String id){prefs.edit().putString("selected",id).apply();}
    public void save(Plan p){p.validate();List<Plan> ps=all();for(int i=0;i<ps.size();i++)if(ps.get(i).id.equals(p.id)){ps.set(i,p);saveAll(ps);return;}if(ps.size()>=50)throw new IllegalArgumentException("最多保存 50 个方案");ps.add(p);saveAll(ps);}
    public void delete(String id){List<Plan> ps=all();if(ps.size()==1)throw new IllegalArgumentException("至少保留一个方案");String selected=selected().id;ps.removeIf(p->p.id.equals(id));saveAll(ps);if(selected.equals(id))select(ps.get(0).id);}
    private void saveAll(List<Plan> ps){JSONArray a=new JSONArray();for(Plan p:ps)a.put(encode(p));prefs.edit().putString("plans",a.toString()).apply();}
    public String backup(){JSONObject root=new JSONObject();try{root.put("format","offline-clicker");root.put("version",1);JSONArray a=new JSONArray();for(Plan p:all())a.put(encode(p));root.put("plans",a);return root.toString(2);}catch(JSONException ex){throw new IllegalStateException(ex);}}
    public int importBackup(String text)throws JSONException{
        JSONObject root=new JSONObject(text);if(!"offline-clicker".equals(root.optString("format"))||root.optInt("version")!=1)throw new IllegalArgumentException("这不是兼容的离线连点器备份");
        JSONArray a=root.getJSONArray("plans");List<Plan> ps=all();if(a.length()<1||a.length()+ps.size()>50)throw new IllegalArgumentException("导入后方案总数必须在 1–50 个以内");
        String first="";for(int i=0;i<a.length();i++){Plan p=decode(a.getJSONObject(i));p.id=UUID.randomUUID().toString();if(p.name.length()>75)p.name=p.name.substring(0,75);p.name+="（导入）";if(i==0)first=p.id;ps.add(p);}saveAll(ps);select(first);return a.length();
    }
    public static JSONObject encode(Plan p){
        try{JSONObject o=new JSONObject();o.put("id",p.id);o.put("name",p.name);o.put("rounds",p.rounds);o.put("startDelayMs",p.startDelayMs);o.put("roundDelayMs",p.roundDelayMs);o.put("displayWidth",p.displayWidth);o.put("displayHeight",p.displayHeight);
            JSONArray actions=new JSONArray();for(Plan.Action a:p.actions){JSONObject j=new JSONObject();j.put("label",a.label);j.put("waitAfterMs",a.waitAfterMs);j.put("repeat",a.repeat);JSONArray strokes=new JSONArray();for(Plan.Stroke s:a.strokes){JSONObject st=new JSONObject();st.put("startMs",s.startMs);st.put("durationMs",s.durationMs);JSONArray points=new JSONArray();for(Plan.Point pt:s.points)points.put(new JSONArray().put(pt.x).put(pt.y));st.put("points",points);strokes.put(st);}j.put("strokes",strokes);actions.put(j);}o.put("actions",actions);return o;
        }catch(JSONException ex){throw new IllegalStateException(ex);}
    }
    public static Plan decode(JSONObject o)throws JSONException{
        Plan p=new Plan();p.id=o.optString("id",UUID.randomUUID().toString());p.name=o.getString("name");p.rounds=o.getInt("rounds");p.startDelayMs=o.getLong("startDelayMs");p.roundDelayMs=o.getLong("roundDelayMs");p.displayWidth=o.optInt("displayWidth");p.displayHeight=o.optInt("displayHeight");
        JSONArray actions=o.getJSONArray("actions");if(actions.length()>500)throw new IllegalArgumentException("动作数量过多");
        for(int i=0;i<actions.length();i++){JSONObject j=actions.getJSONObject(i);Plan.Action a=new Plan.Action();a.label=j.getString("label");a.waitAfterMs=j.getLong("waitAfterMs");a.repeat=j.getInt("repeat");JSONArray strokes=j.getJSONArray("strokes");if(strokes.length()>10)throw new IllegalArgumentException("触点数量过多");for(int k=0;k<strokes.length();k++){JSONObject st=strokes.getJSONObject(k);Plan.Stroke s=new Plan.Stroke();s.startMs=st.getLong("startMs");s.durationMs=st.getLong("durationMs");JSONArray pts=st.getJSONArray("points");if(pts.length()>1000)throw new IllegalArgumentException("路径过长");for(int n=0;n<pts.length();n++){JSONArray pt=pts.getJSONArray(n);s.points.add(new Plan.Point((float)pt.getDouble(0),(float)pt.getDouble(1)));}a.strokes.add(s);}p.actions.add(a);}p.validate();return p;
    }
}
