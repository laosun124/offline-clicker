package local.offline.clicker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Plain Java model; coordinates are fractions of the full physical display. */
public final class Plan {
    public String id = UUID.randomUUID().toString();
    public String name = "我的方案";
    public int rounds = 1; // Zero means unlimited, explicitly chosen by the user.
    public long startDelayMs = 3000;
    public long roundDelayMs = 300;
    public int displayWidth;
    public int displayHeight;
    public final List<Action> actions = new ArrayList<>();

    public Plan copy() {
        Plan p = new Plan(); p.id=id; p.name=name; p.rounds=rounds;
        p.startDelayMs=startDelayMs; p.roundDelayMs=roundDelayMs;
        p.displayWidth=displayWidth; p.displayHeight=displayHeight;
        for (Action a: actions) p.actions.add(a.copy());
        return p;
    }

    public void validate() {
        if (name == null || name.trim().isEmpty() || name.length()>80) throw new IllegalArgumentException("方案名称需要 1–80 个字");
        if (rounds<0 || rounds>1000000) throw new IllegalArgumentException("循环次数需要在 0–1000000 之间");
        if (startDelayMs<0 || startDelayMs>86400000L || roundDelayMs<0 || roundDelayMs>3600000L) throw new IllegalArgumentException("等待时间超出范围");
        if (actions.size()>500) throw new IllegalArgumentException("每个方案最多保存 500 个动作");
        if (displayWidth<0 || displayHeight<0) throw new IllegalArgumentException("屏幕尺寸无效");
        for (Action a:actions) a.validate();
    }

    public static final class Point {
        public float x,y;
        public Point(float x,float y) { this.x=x; this.y=y; }
    }

    public static final class Stroke {
        public long startMs;
        public long durationMs = 50;
        public final List<Point> points = new ArrayList<>();
        public Stroke copy() {
            Stroke s=new Stroke(); s.startMs=startMs; s.durationMs=durationMs;
            for(Point p:points) s.points.add(new Point(p.x,p.y)); return s;
        }
    }

    public static final class Action {
        public String label="点击";
        public long waitAfterMs=300;
        public int repeat=1;
        public final List<Stroke> strokes = new ArrayList<>();
        public Action copy() {
            Action a=new Action(); a.label=label; a.waitAfterMs=waitAfterMs; a.repeat=repeat;
            for(Stroke s:strokes) a.strokes.add(s.copy()); return a;
        }
        public long durationMs() {
            long duration=0; for(Stroke s:strokes) duration=Math.max(duration,s.startMs+s.durationMs); return duration;
        }
        public void setDuration(long duration) {
            long previous=durationMs();
            for(Stroke s:strokes) {
                s.startMs=previous==0?0:Math.round((double)s.startMs*duration/previous);
                s.durationMs=previous==0?duration:Math.max(1,Math.round((double)s.durationMs*duration/previous));
                s.durationMs=Math.min(s.durationMs,Math.max(1,duration-s.startMs));
            }
        }
        public void validate() {
            if(label==null || label.length()>80) throw new IllegalArgumentException("动作名称无效");
            if(waitAfterMs<0 || waitAfterMs>3600000L || repeat<1 || repeat>10000) throw new IllegalArgumentException("动作间隔或重复次数超出范围");
            if(strokes.isEmpty() || strokes.size()>10) throw new IllegalArgumentException("每个动作需要 1–10 个触点");
            for(Stroke s:strokes) {
                if(s.startMs<0 || s.durationMs<1 || s.durationMs>59000 || s.startMs>59000-s.durationMs) throw new IllegalArgumentException("动作时长需要在 1–59000 毫秒之间");
                if(s.points.isEmpty() || s.points.size()>1000) throw new IllegalArgumentException("手势路径无效");
                for(Point p:s.points) if(!Float.isFinite(p.x)||!Float.isFinite(p.y)||p.x<0||p.x>1||p.y<0||p.y>1) throw new IllegalArgumentException("坐标必须在屏幕内");
            }
        }
    }
}
