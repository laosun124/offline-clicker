package local.offline.clicker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** No device or Android runtime needed; tests the actual production queue. */
public final class PlaybackTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Plan actionPlan(int count){Plan p=new Plan();p.startDelayMs=100;p.roundDelayMs=30;for(int i=0;i<count;i++){Plan.Action a=new Plan.Action();a.label="action-"+i;a.waitAfterMs=50;Plan.Stroke s=new Plan.Stroke();s.points.add(new Plan.Point(.5f,.5f));a.strokes.add(s);p.actions.add(a);}return p;}
    static final class Task {Runnable r;long at;Task(Runnable r,long at){this.r=r;this.at=at;}}
    static final class Clock implements Playback.Clock{
        long time;List<Task> tasks=new ArrayList<>();public long now(){return time;}public void post(Runnable r,long d){tasks.add(new Task(r,time+d));}public void remove(Runnable r){tasks.removeIf(t->t.r==r);}
        void next(){if(tasks.isEmpty())throw new AssertionError("no queued task");tasks.sort(Comparator.comparingLong(t->t.at));Task t=tasks.remove(0);time=t.at;t.r.run();}
    }
    static final class Harness{
        Clock c=new Clock();List<String> sent=new ArrayList<>();List<Playback.Result> callbacks=new ArrayList<>();List<String> endings=new ArrayList<>();
        Playback p=new Playback(c,(a,r)->{sent.add(a.label);callbacks.add(r);},new Playback.Observer(){public void changed(){}public void finished(String reason){endings.add(reason);}});
        void complete(){callbacks.get(callbacks.size()-1).finish(true);}
    }
    static void sequential(){Harness h=new Harness();Plan p=actionPlan(2);p.rounds=2;p.actions.get(0).repeat=2;h.p.start(p);check(h.sent.isEmpty(),"countdown must defer");h.c.next();check(h.c.time==100,"countdown duration");check(h.p.isInFlight(),"gesture in flight");check(h.c.tasks.isEmpty(),"no concurrent gesture before completion");
        for(int i=0;i<5;i++){h.complete();h.c.next();}h.complete();check(h.sent.toString().equals("[action-0, action-0, action-1, action-0, action-0, action-1]"),"per-action and round ordering");check(!h.p.isRunning(),"finite run ends");check(h.endings.size()==1,"one finish event");check(h.c.time==380,"gesture gaps and round gap");}
    static void pauseDelay(){Harness h=new Harness();h.p.start(actionPlan(1));h.c.time=40;h.p.pause();check(h.c.tasks.isEmpty(),"pause removes delayed task");h.c.time=1000;h.p.resume();h.c.next();check(h.c.time==1060,"resume preserves remaining countdown");check(h.sent.size()==1,"one execution after resume");}
    static void pauseInFlight(){Harness h=new Harness();h.p.start(actionPlan(2));h.c.next();h.p.pause();h.complete();check(h.p.isPaused(),"pause retained across completion");check(h.c.tasks.isEmpty(),"no advancement while paused");h.p.resume();h.c.next();check(h.sent.size()==2,"resume advances to next action");}
    static void stopAndStale(){Harness h=new Harness();h.p.start(actionPlan(2));h.c.next();Playback.Result old=h.callbacks.get(0);h.p.stop("stop");old.finish(true);check(h.c.tasks.isEmpty(),"stale callback cannot restart stopped queue");h.p.start(actionPlan(1));old.finish(true);h.c.next();check(h.sent.size()==2,"stale completion does not corrupt new run");h.complete();check(!h.p.isRunning(),"new run ends normally");}
    static void cancelled(){Harness h=new Harness();h.p.start(actionPlan(2));h.c.next();h.callbacks.get(0).finish(false);check(!h.p.isRunning(),"cancelled gesture stops");check(h.c.tasks.isEmpty(),"cancelled gesture cannot continue");}
    static void pauseCancelled(){Harness h=new Harness();h.p.start(actionPlan(2));h.c.next();h.p.pause();h.callbacks.get(0).finish(false);check(h.p.isRunning()&&h.p.isPaused(),"pause can retain a gesture interrupted by the pause tap");check(h.c.tasks.isEmpty(),"paused cancellation cannot dispatch");h.p.resume();h.c.next();check(h.sent.toString().equals("[action-0, action-0]"),"interrupted step retries without skipping");}
    static void duplicateCompletion(){Harness h=new Harness();h.p.start(actionPlan(2));h.c.next();Playback.Result cb=h.callbacks.get(0);cb.finish(true);cb.finish(true);check(h.c.tasks.size()==1,"duplicate result must not schedule twice");h.c.next();check(h.sent.size()==2,"duplicate callback cannot skip a step");}
    static void unlimitedAndCopy(){Harness h=new Harness();Plan p=actionPlan(1);p.rounds=0;h.p.start(p);p.actions.clear();for(int i=0;i<5;i++){h.c.next();h.complete();}check(h.p.isRunning(),"explicit unlimited run continues");check(h.sent.size()==5,"playback uses snapshot");h.p.stop("user");check(h.c.tasks.isEmpty(),"unlimited run stops");}
    static void validation(){
        Plan p=actionPlan(1);p.actions.get(0).strokes.get(0).points.get(0).x=Float.NaN;try{p.validate();throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
        p=actionPlan(1);p.actions.get(0).strokes.get(0).durationMs=60000;try{p.validate();throw new AssertionError("oversized gesture accepted");}catch(IllegalArgumentException expected){checks++;}
        p=actionPlan(1);p.actions.get(0).repeat=0;try{p.validate();throw new AssertionError("zero repeat accepted");}catch(IllegalArgumentException expected){checks++;}
        p=actionPlan(1);p.actions.get(0).strokes.get(0).points.get(0).y=1.1f;try{p.validate();throw new AssertionError("off-screen point accepted");}catch(IllegalArgumentException expected){checks++;}
        Harness h=new Harness();try{h.p.start(new Plan());throw new AssertionError("empty plan accepted");}catch(IllegalArgumentException expected){checks++;}
    }
    static void multiValidation(){Plan p=actionPlan(1);Plan.Action a=p.actions.get(0);Plan.Stroke s=new Plan.Stroke();s.startMs=200;s.durationMs=300;s.points.add(new Plan.Point(.2f,.8f));a.strokes.add(s);a.setDuration(1000);check(a.durationMs()==1000,"multi-finger duration scales without losing offsets");p.validate();for(int i=0;i<9;i++)a.strokes.add(s.copy());try{p.validate();throw new AssertionError("11 fingers accepted");}catch(IllegalArgumentException expected){checks++;}Plan copy=actionPlan(1).copy();Plan snapshot=copy.copy();copy.actions.get(0).strokes.get(0).points.get(0).x=.1f;check(snapshot.actions.get(0).strokes.get(0).points.get(0).x==.5f,"coordinate snapshot is a deep copy");}
    public static void main(String[] args){sequential();pauseDelay();pauseInFlight();stopAndStale();cancelled();pauseCancelled();duplicateCompletion();unlimitedAndCopy();validation();multiValidation();System.out.println("Playback tests passed: "+checks+" checks");}
}
