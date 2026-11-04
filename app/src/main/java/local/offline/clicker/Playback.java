package local.offline.clicker;

/** Sequential playback: only completion of the previous gesture advances the queue. */
public final class Playback {
    public interface Clock { long now(); void post(Runnable r,long delay); void remove(Runnable r); }
    public interface Result { void finish(boolean success); }
    public interface Gestures { void perform(Plan.Action action,Result callback); }
    public interface Observer { void changed(); void finished(String reason); }
    private final Clock clock;
    private final Gestures gestures;
    private final Observer observer;
    private Plan plan;
    private Runnable pending;
    private long due,remaining;
    private int generation,actionIndex,repeatIndex;
    private long round;
    private boolean running,paused,inFlight;
    private String state="尚未开始";

    public Playback(Clock c,Gestures g,Observer o){clock=c;gestures=g;observer=o;}
    public boolean isRunning(){return running;}
    public boolean isPaused(){return paused;}
    public boolean isInFlight(){return inFlight;}
    public String state(){return state;}
    public int actionIndex(){return actionIndex;}

    public void start(Plan value){
        value.validate();
        if(value.actions.isEmpty())throw new IllegalArgumentException("请先添加至少一个动作");
        stopInternal(); plan=value.copy(); running=true; actionIndex=repeatIndex=0;round=1;
        state=plan.startDelayMs>0?"倒计时后开始 · "+(plan.startDelayMs/1000.0)+" 秒":"准备开始";
        observer.changed(); schedule(plan.startDelayMs);
    }
    private void schedule(long delay){
        remaining=Math.max(0,delay);
        if(!running||paused)return;
        final int token=generation;due=clock.now()+remaining;
        pending=()->{pending=null;if(token!=generation||!running||paused)return;dispatch();};
        clock.post(pending,remaining);
    }
    private void dispatch(){
        final int token=generation;
        inFlight=true;state="第 "+round+(plan.rounds==0?" 轮":" / "+plan.rounds+" 轮")+" · 动作 "+(actionIndex+1)+" / "+plan.actions.size();observer.changed();
        Plan.Action current=plan.actions.get(actionIndex);
        final boolean[] answered={false};
        try {gestures.perform(current,success->{
            if(answered[0]||token!=generation||!running)return;answered[0]=true;inFlight=false;
            if(!success){
                if(paused){remaining=0;state="已暂停 · 继续时重新执行被中断的动作";observer.changed();return;}
                finish("手势被系统或手动触摸打断，已停止");return;
            }
            long wait=current.waitAfterMs;
            repeatIndex++;
            if(repeatIndex>=current.repeat){repeatIndex=0;actionIndex++;}
            if(actionIndex>=plan.actions.size()){
                actionIndex=0;
                if(plan.rounds>0 && round>=plan.rounds){finish("已完成全部循环");return;}
                round++;wait+=plan.roundDelayMs;
            }
            if(paused)state="已暂停 · 继续时执行下一动作";
            observer.changed();schedule(wait);
        });}catch(RuntimeException ex){inFlight=false;finish("无法执行手势："+ex.getMessage());}
    }
    public void pause(){
        if(!running||paused)return;paused=true;
        if(pending!=null){remaining=Math.max(0,due-clock.now());clock.remove(pending);pending=null;}
        state=inFlight?"暂停中 · 等当前动作结束":"已暂停";observer.changed();
    }
    public void resume(){
        if(!running||!paused)return;paused=false;state="继续执行";observer.changed();if(!inFlight)schedule(remaining);
    }
    public void stop(String reason){if(!running)return;stopInternal();state=reason;observer.changed();observer.finished(reason);}
    private void finish(String reason){stopInternal();state=reason;observer.changed();observer.finished(reason);}
    private void stopInternal(){generation++;if(pending!=null)clock.remove(pending);pending=null;running=paused=inFlight=false;}
}
