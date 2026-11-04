package local.offline.clicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClickService extends AccessibilityService {
    private static ClickService instance;
    public static ClickService get(){return instance;}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private LinearLayout panel;
    private TextView dock;
    private WindowManager.LayoutParams dockParams;
    private TextView status,heading;
    private Button pause,recordButton,stopButton,markersButton;
    private WindowManager.LayoutParams panelParams;
    private MarkerView markers;
    private FrameLayout inputLayer;
    private ScrollView actionList;
    private CaptureView capture;
    private Store store;
    private Plan plan;
    private Playback engine;
    private String notice="准备就绪";
    private boolean showMarkers=true,recording,replayingCapture,destroyed,editingActions,choosingOperation,minimized;
    private int lastScreenWidth,lastScreenHeight;
    private int pendingDelete=-1,actionListPosition;
    private int captureGeneration,interruptionGeneration;
    private boolean cancelingGesture;
    private long lastReplayDone;
    private int recordedCount;
    private final SharedPreferences.OnSharedPreferenceChangeListener appearanceChanged=(prefs,key)->{if("mode".equals(key))applyAppearance();};
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){stopAll("屏幕已关闭，已停止",false);}};

    @Override protected void onServiceConnected(){
        super.onServiceConnected();Ui.configure(this);instance=this;windows=(WindowManager)getSystemService(WINDOW_SERVICE);store=new Store(this);plan=store.selected();
        engine=new Playback(new Playback.Clock(){public long now(){return SystemClock.uptimeMillis();}public void post(Runnable r,long d){handler.postDelayed(r,d);}public void remove(Runnable r){handler.removeCallbacks(r);}},this::perform,new Playback.Observer(){
            public void changed(){refreshPanel();updateMarkers();}
            public void finished(String reason){notice=reason;if(minimized&&dock!=null)snapDock();refreshPanel();updateMarkers();}
        });
        Point initial=screen();lastScreenWidth=initial.x;lastScreenHeight=initial.y;
        getSharedPreferences(Ui.PREFERENCES,MODE_PRIVATE).registerOnSharedPreferenceChangeListener(appearanceChanged);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screenReceiver,f);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){ /* No window data is inspected. */ }
    @Override public void onInterrupt(){stopAll("无障碍服务已中断",false);}
    @Override public void onConfigurationChanged(Configuration c){
        super.onConfigurationChanged(c);Point size=screen();
        if(size.x!=lastScreenWidth||size.y!=lastScreenHeight){
            stopAll("屏幕方向已变化，请检查动作位置",true);
            lastScreenWidth=size.x;lastScreenHeight=size.y;
            if(panel!=null){panelParams.x=Math.max(0,Math.min(panelParams.x,size.x-panel.getWidth()));panelParams.y=Math.max(dp(28),Math.min(panelParams.y,size.y-panel.getHeight()-dp(24)));windows.updateViewLayout(panel,panelParams);}
            if(dock!=null)snapDock();
        }
        applyAppearance();updateMarkers();
    }
    private void applyAppearance(){
        Ui.configure(this);
        if(panel!=null){populatePanel();panel.setBackground(Ui.card(this));refreshPanel();}
        if(dock!=null){dock.setTextColor(Ui.INK);dock.setBackground(Ui.card(this));}
        if(editingActions)renderActionSettings();else if(choosingOperation)renderOperationMenu();
        if(capture!=null)capture.invalidate();if(markers!=null)markers.invalidate();
    }
    @Override public void onDestroy(){
        destroyed=true;stopAll("服务已关闭",false);hideControls();handler.removeCallbacksAndMessages(null);
        getSharedPreferences(Ui.PREFERENCES,MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(appearanceChanged);
        try{unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){} if(instance==this)instance=null;super.onDestroy();
    }
    public boolean busy(){return engine!=null&&(engine.isRunning()||recording||capture!=null||replayingCapture||editingActions||choosingOperation);}
    public String status(){return engine!=null&&engine.isRunning()?engine.state():notice;}
    public void reload(){stopAll("方案已更新",true);plan=store.selected();refreshPanel();updateMarkers();}
    public void showControls(){
        if(engine==null)return;
        if(panel!=null){if(minimized)restoreControls();refreshPanel();updateMarkers();return;}
        Ui.configure(this);plan=store.selected();
        panel=Ui.column(this);panel.setPadding(dp(9),dp(8),dp(9),dp(8));panel.setBackground(Ui.card(this));panel.setElevation(0);
        panelParams=params(Math.min(dp(320),screen().x-dp(16)),WindowManager.LayoutParams.WRAP_CONTENT,false);
        panelParams.x=dp(8);panelParams.y=dp(75);populatePanel();
        markers=new MarkerView();markers.setVisibility(View.GONE);
        inputLayer=new FrameLayout(this);inputLayer.setVisibility(View.GONE);
        dock=Ui.text(this,"›",27,Ui.INK);dock.setGravity(Gravity.CENTER);dock.setBackground(Ui.card(this));dock.setVisibility(View.GONE);dock.setContentDescription("恢复悬浮控制");
        dockParams=params(dp(44),dp(64),false);dockParams.x=0;dockParams.y=panelParams.y;
        dock.setOnClickListener(v->restoreControls());
        dock.setOnTouchListener(new View.OnTouchListener(){
            float sx,sy;int x,y;boolean dragged;
            public boolean onTouch(View view,MotionEvent event){
                if(event.getAction()==MotionEvent.ACTION_DOWN){if(engine!=null&&engine.isRunning()&&!engine.isPaused())engine.pause();sx=event.getRawX();sy=event.getRawY();x=dockParams.x;y=dockParams.y;dragged=false;return true;}
                if(event.getAction()==MotionEvent.ACTION_MOVE){float dx=event.getRawX()-sx,dy=event.getRawY()-sy;if(Math.hypot(dx,dy)>dp(8))dragged=true;if(dragged){Point size=screen();dockParams.x=Math.max(0,Math.min(size.x-dockParams.width,x+Math.round(dx)));dockParams.y=Math.max(dp(28),Math.min(size.y-dockParams.height-dp(24),y+Math.round(dy)));windows.updateViewLayout(dock,dockParams);}return true;}
                if(event.getAction()==MotionEvent.ACTION_UP){if(dragged)snapDock();else view.performClick();return true;}return true;
            }
        });
        try{windows.addView(markers,params(-1,-1,true));windows.addView(inputLayer,params(-1,-1,false));windows.addView(panel,panelParams);windows.addView(dock,dockParams);}
        catch(RuntimeException ex){hideControls();Toast.makeText(this,"无法显示悬浮控制，请重新开启无障碍服务",Toast.LENGTH_LONG).show();return;}
        refreshPanel();updateMarkers();
    }
    private void populatePanel(){
        panel.removeAllViews();
        LinearLayout top=Ui.row(this);
        heading=Ui.text(this,"",13,Ui.INK);heading.setSingleLine();heading.setEllipsize(TextUtils.TruncateAt.END);heading.setPadding(dp(5),dp(4),dp(5),dp(8));
        top.addView(heading,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView minimize=Ui.text(this,"最小化",13,Ui.INK);minimize.setGravity(Gravity.CENTER);minimize.setOnClickListener(v->minimizeControls());top.addView(minimize,new LinearLayout.LayoutParams(dp(64),dp(38)));panel.addView(top);
        status=Ui.text(this,"",12,Ui.MUTED);status.setPadding(dp(5),0,dp(5),dp(5));panel.addView(status);
        LinearLayout primary=Ui.row(this);panel.addView(primary);
        Ui.weight(primary,Ui.button(this,"开始",true,this::startPlayback));
        pause=Ui.button(this,"暂停",false,()->{if(engine.isPaused())engine.resume();else engine.pause();});Ui.weight(primary,pause);
        stopButton=Ui.button(this,"停止",false,()->stopAll("已停止",true));stopButton.setTextColor(Ui.RED);Ui.weight(primary,stopButton);
        LinearLayout actions=Ui.row(this);panel.addView(actions);
        Ui.weight(actions,Ui.button(this,"添加操作",false,this::showOperationMenu));
        recordButton=Ui.button(this,"录制动作",false,()->{if(recording)endRecording("录制完成");else startRecording();});Ui.weight(actions,recordButton);
        markersButton=Ui.button(this,showMarkers?"隐藏操作":"显示操作",false,()->{showMarkers=!showMarkers;markersButton.setText(showMarkers?"隐藏操作":"显示操作");updateMarkers();});Ui.weight(actions,markersButton);
        LinearLayout more=Ui.row(this);panel.addView(more);
        Ui.weight(more,Ui.button(this,"动作设置",false,this::showActionSettings));
        Ui.weight(more,Ui.button(this,"返回应用",false,this::returnToApp));
        Ui.weight(more,Ui.button(this,"关闭",false,this::closeControls));
        heading.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int x,y;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();x=panelParams.x;y=panelParams.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){Point size=screen();panelParams.x=Math.max(0,Math.min(size.x-panel.getWidth(),x+Math.round(e.getRawX()-sx)));panelParams.y=Math.max(dp(28),Math.min(size.y-panel.getHeight()-dp(24),y+Math.round(e.getRawY()-sy)));windows.updateViewLayout(panel,panelParams);return true;}if(e.getAction()==MotionEvent.ACTION_UP){v.performClick();return true;}return true;}});
    }
    private void snapDock(){
        if(dock==null)return;Point size=screen();boolean left=dockParams.x+dockParams.width/2<size.x/2;
        dockParams.x=left?0:size.x-dockParams.width;dockParams.y=Math.max(dp(28),Math.min(size.y-dockParams.height-dp(24),dockParams.y));dock.setText(left?"›":"‹");windows.updateViewLayout(dock,dockParams);
    }
    private void minimizeNow(){
        if(panel==null||dock==null)return;
        if(!minimized){dockParams.x=panelParams.x+panel.getWidth()/2<screen().x/2?0:screen().x-dockParams.width;dockParams.y=panelParams.y;snapDock();}
        minimized=true;panel.setVisibility(View.GONE);dock.setVisibility(View.VISIBLE);hideMarkers();refreshPanel();
    }
    private void minimizeControls(){
        boolean injecting=replayingCapture;
        if(recording||capture!=null||choosingOperation){stopCaptureOnly();notice="已结束选取或录制";}
        int request=captureGeneration;Runnable reduce=()->{if(request==captureGeneration)minimizeNow();};
        if(injecting)cancelInjectedGesture(reduce);else reduce.run();
    }
    private void restoreControls(){
        if(panel==null)return;
        if(engine!=null&&engine.isRunning()&&!engine.isPaused())engine.pause();
        minimized=false;if(dock!=null)dock.setVisibility(View.GONE);panel.setVisibility(View.VISIBLE);refreshPanel();updateMarkers();
    }
    private void returnToApp(){
        boolean injecting=(engine!=null&&engine.isInFlight())||replayingCapture;
        stopAll("已返回应用",false);int request=captureGeneration;
        Runnable open=()->{
            if(destroyed||request!=captureGeneration)return;
            minimizeNow();
            Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try{
                PendingIntent launch=PendingIntent.getActivity(this,2,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                ActivityOptions options=ActivityOptions.makeBasic();
                if(Build.VERSION.SDK_INT>=34)options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                launch.send(this,0,null,null,null,null,options.toBundle());
            }catch(PendingIntent.CanceledException|RuntimeException ex){
                try{startActivity(intent);}catch(RuntimeException ignored){tell("无法返回应用，请从桌面打开");}
            }
        };
        if(injecting)cancelInjectedGesture(open);else open.run();
    }
    public void startPlayback(){
        if(engine==null||engine.isRunning())return;
        boolean injecting=replayingCapture;
        stopAll("准备开始",false);int request=captureGeneration;
        Runnable start=()->{
            if(destroyed||request!=captureGeneration)return;
            plan=store.selected();
            if(plan.actions.isEmpty()){tell("请先通过添加操作或录制添加动作");return;}
            if(!geometryOkay()){tell("屏幕方向或比例已变化，请重新选取位置或切回录制方向");return;}
            showControls();if(panel==null)return;hideMarkers();
            try{engine.start(plan);}catch(IllegalArgumentException ex){tell(ex.getMessage());updateMarkers();}
        };
        if(injecting)cancelInjectedGesture(start);else start.run();
    }
    public void capture(String kind,int editIndex){
        if(engine==null)return;
        boolean injecting=(engine.isInFlight()||replayingCapture);
        stopAll("准备选取位置",false);int request=captureGeneration;
        Runnable open=()->{
            if(destroyed||request!=captureGeneration)return;
            plan=store.selected();showControls();
            if(!geometryOkay()){tell("请先切回方案原来的屏幕方向，或新建方案");return;}
            hideMarkers();
            String hint="滑动".equals(kind)?"在目标应用上划出滑动路径":"多指".equals(kind)?"用 2–10 根手指同时点按或滑动":"长按".equals(kind)?"点一下长按位置（时长可在方案中调整）":"点一下目标位置";
            notice=hint+" · 点停止可取消";refreshPanel();addCapture(kind,editIndex);
        };
        if(injecting)cancelInjectedGesture(open);else open.run();
    }
    private void startRecording(){
        if(engine.isRunning()){tell("请先停止运行，再录制");return;}
        stopCaptureOnly();plan=store.selected();if(!geometryOkay()){tell("请切回方案原来的屏幕方向，或新建方案");return;}
        recording=true;recordedCount=0;lastReplayDone=0;captureGeneration++;
        notice="录制中 · 每个手势会转发到目标应用 · 点击完成结束";hideMarkers();addCapture("录制",-1);refreshPanel();
    }
    private void addCapture(String kind,int editIndex){
        if(destroyed||panel==null||inputLayer==null)return;
        capture=new CaptureView(kind,editIndex);
        inputLayer.removeAllViews();inputLayer.addView(capture,new FrameLayout.LayoutParams(-1,-1));inputLayer.setVisibility(View.VISIBLE);
    }
    private void captured(Plan.Action action,int editIndex,long gestureStart){
        if(action.strokes.isEmpty())return;
        Point screen=screen();plan.displayWidth=screen.x;plan.displayHeight=screen.y;
        if(editIndex>=0&&editIndex<plan.actions.size()){
            Plan.Action old=plan.actions.get(editIndex);action.label=old.label;action.repeat=old.repeat;action.waitAfterMs=old.waitAfterMs;
            if(old.strokes.size()==1&&old.strokes.get(0).points.size()==1)action.setDuration(old.durationMs());
            plan.actions.set(editIndex,action);
        }else{
            if(plan.actions.size()>=500){endRecording("每个方案最多 500 个动作");return;}
            if(recording&&recordedCount>0&&!plan.actions.isEmpty())plan.actions.get(plan.actions.size()-1).waitAfterMs=Math.max(30,Math.min(3600000,gestureStart-lastReplayDone));
            plan.actions.add(action);
        }
        store.save(plan);clearInputLayer();
        if(!recording){tell("已保存动作 "+(editIndex>=0?editIndex+1:plan.actions.size()));updateMarkers();return;}
        recordedCount++;replayingCapture=true;final int token=captureGeneration;
        notice="已录制 "+recordedCount+" 个动作 · 正在转发，请稍候";refreshPanel();
        handler.postDelayed(()->{
            if(token!=captureGeneration||!recording)return;
            perform(action,success->{if(token!=captureGeneration||!recording)return;replayingCapture=false;lastReplayDone=SystemClock.uptimeMillis();if(!success){endRecording("动作已保存，但转发被中断，录制已停止");return;}notice="录制中 · 已保存 "+recordedCount+" 个动作 · 点击完成结束";refreshPanel();handler.postDelayed(()->{if(token==captureGeneration&&recording)addCapture("录制",-1);},80);});
        },60);
    }
    private void endRecording(String message){boolean inFlight=replayingCapture;stopCaptureOnly();if(inFlight)cancelInjectedGesture();notice=message;refreshPanel();updateMarkers();}
    private void stopCaptureOnly(){
        captureGeneration++;interruptionGeneration++;cancelingGesture=false;recording=false;replayingCapture=false;
        clearInputLayer();editingActions=false;choosingOperation=false;pendingDelete=-1;actionList=null;
        if(panel!=null)panel.setVisibility(minimized?View.GONE:View.VISIBLE);
    }
    public void stopAll(String message,boolean cancelGesture){
        boolean injecting=(engine!=null&&engine.isInFlight())||replayingCapture;
        stopCaptureOnly();if(engine!=null)engine.stop(message);
        if(cancelGesture&&injecting)cancelInjectedGesture();notice=message;refreshPanel();updateMarkers();
    }
    private void cancelInjectedGesture(){
        cancelInjectedGesture(null);
    }
    private void cancelInjectedGesture(Runnable after){
        if(inputLayer==null||panel==null){if(after!=null)after.run();return;}
        final int token=++interruptionGeneration,request=captureGeneration;
        cancelingGesture=true;
        panel.setVisibility(View.GONE);if(dock!=null)dock.setVisibility(View.GONE);
        FrameLayout shield=new FrameLayout(this);shield.setClickable(true);
        inputLayer.removeAllViews();inputLayer.addView(shield,new FrameLayout.LayoutParams(-1,-1));inputLayer.setVisibility(View.VISIBLE);
        final boolean[] finished={false};
        Runnable done=()->{
            if(finished[0]||token!=interruptionGeneration)return;finished[0]=true;cancelingGesture=false;
            clearInputLayer();if(panel!=null)panel.setVisibility(minimized?View.GONE:View.VISIBLE);if(dock!=null)dock.setVisibility(minimized?View.VISIBLE:View.GONE);
            updateMarkers();if(after!=null&&request==captureGeneration)handler.post(after);
        };
        handler.postDelayed(()->{
            if(destroyed||token!=interruptionGeneration||request!=captureGeneration)return;
            Path path=new Path();Point size=screen();path.moveTo(size.x/2f,size.y/2f);
            try{
                boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,1)).build(),new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription g){handler.removeCallbacks(done);done.run();}
                    @Override public void onCancelled(GestureDescription g){handler.removeCallbacks(done);done.run();}
                },handler);
                if(accepted)handler.postDelayed(done,1500);else done.run();
            }catch(RuntimeException ignored){done.run();}
        },60);
    }
    private void perform(Plan.Action action,Playback.Result result){
        if(destroyed){result.finish(false);return;}
        boolean playback=engine!=null&&engine.isRunning();
        Point size=screen();
        if(playback){
            minimizeNow();
            int[] safe=OverlayPosition.find(action,size.x,size.y,dockParams.width,dockParams.height,dp(28),dp(10),dockParams.x,dockParams.y);
            if(safe==null)throw new IllegalArgumentException("找不到不会遮挡手势的控制入口，请调整路径");
            dockParams.x=safe[0];dockParams.y=safe[1];dock.setText(safe[0]<size.x/2?"›":"‹");windows.updateViewLayout(dock,dockParams);
        }
        final int token=captureGeneration;
        final boolean[] answered={false};
        final Runnable timeout=()->{if(!answered[0]){answered[0]=true;result.finish(false);}};
        Playback.Result once=success->{if(!answered[0]){answered[0]=true;handler.removeCallbacks(timeout);result.finish(success);}};
        GestureDescription.Builder builder=new GestureDescription.Builder();
        try{
            action.validate();
            for(Plan.Stroke stroke:action.strokes){
                Path path=new Path();
                for(int i=0;i<stroke.points.size();i++){Plan.Point point=stroke.points.get(i);float x=Math.max(1,Math.min(size.x-2,point.x*size.x)),y=Math.max(1,Math.min(size.y-2,point.y*size.y));if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}
                builder.addStroke(new GestureDescription.StrokeDescription(path,stroke.startMs,stroke.durationMs));
            }
            GestureDescription gesture=builder.build();
            final int[] layoutAttempts={0};
            final Runnable[] send={null};
            send[0]=()->{
                if(destroyed||token!=captureGeneration||(playback&&(!engine.isRunning()||engine.isPaused()))){once.finish(false);return;}
                if(playback){
                    // Verify the actual on-screen bounds too: some devices shift
                    // overlay windows for cutouts or system bars.
                    if(panel.isShown()||inputLayer.isShown()||dock==null||!dock.isShown()){once.finish(false);return;}
                    int[] actual=new int[2];dock.getLocationOnScreen(actual);
                    int width=dock.getWidth(),height=dock.getHeight();
                    boolean ready=width>0&&height>0&&OverlayPosition.safe(action,size.x,size.y,actual[0],actual[1],width,height,dp(10));
                    if(!ready){
                        if(++layoutAttempts[0]>5){once.finish(false);return;}
                        if(width>0&&height>0){
                            int[] next=OverlayPosition.find(action,size.x,size.y,width,height,dp(28),dp(10),actual[0],actual[1]);
                            if(next==null){once.finish(false);return;}
                            int shiftX=actual[0]-dockParams.x,shiftY=actual[1]-dockParams.y;
                            dockParams.x=next[0]-shiftX;dockParams.y=next[1]-shiftY;windows.updateViewLayout(dock,dockParams);
                        }
                        handler.postDelayed(send[0],60);return;
                    }
                }
                try{
                    boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
                        @Override public void onCompleted(GestureDescription g){once.finish(true);}
                        @Override public void onCancelled(GestureDescription g){handler.postDelayed(()->once.finish(false),120);}
                    },handler);
                    if(!accepted)once.finish(false);else if(!answered[0])handler.postDelayed(timeout,action.durationMs()+5000);
                }catch(RuntimeException ex){once.finish(false);}
            };
            // Allow the window manager to commit the hidden panel and safe handle
            // location before sending any pointer to the target application.
            if(playback)handler.postDelayed(send[0],60);else send[0].run();
        }catch(RuntimeException ex){once.finish(false);}
    }
    private void refreshPanel(){
        if(panel==null)return;
        status.setText(engine!=null&&engine.isRunning()?engine.state():notice);
        pause.setText(engine!=null&&engine.isPaused()?"继续":"暂停");
        pause.setEnabled(engine!=null&&engine.isRunning());
        recordButton.setText(recording?"完成录制":"录制动作");
        heading.setText("拖动移动 · "+(plan==null?"离线连点器":plan.name));
        panelParams.flags=baseFlags(false)|((engine!=null&&engine.isRunning())||recording?WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:0);
        if(dock!=null){dockParams.flags=baseFlags(false)|((engine!=null&&engine.isRunning())?WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON:0);try{windows.updateViewLayout(dock,dockParams);}catch(RuntimeException ignored){}}
        try{windows.updateViewLayout(panel,panelParams);}catch(RuntimeException ignored){}
    }
    private void tell(String text){notice=text;refreshPanel();Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private boolean geometryOkay(){
        if(plan.actions.isEmpty()||plan.displayWidth==0||plan.displayHeight==0)return true;
        Point p=screen();double before=(double)plan.displayWidth/plan.displayHeight,now=(double)p.x/p.y;return Math.abs(now/before-1)<0.08;
    }
    private int dp(float n){return Ui.dp(this,n);}
    private Point screen(){DisplayMetrics m=new DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(m);return new Point(m.widthPixels,m.heightPixels);}
    private int baseFlags(boolean through){return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|(through?WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE:0);}
    private WindowManager.LayoutParams params(int w,int h,boolean through){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,baseFlags(through),PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;return p;
    }
    private void remove(View view){if(view!=null&&windows!=null)try{windows.removeView(view);}catch(RuntimeException ignored){}}
    private void clearInputLayer(){
        if(inputLayer!=null){inputLayer.setVisibility(View.GONE);inputLayer.removeAllViews();}
        capture=null;
    }
    private void hideControls(){
        editingActions=false;choosingOperation=false;pendingDelete=-1;actionList=null;clearInputLayer();
        remove(panel);panel=null;remove(inputLayer);inputLayer=null;remove(markers);markers=null;remove(dock);dock=null;minimized=false;
    }
    private void closeControls(){
        boolean injecting=(engine!=null&&engine.isInFlight())||replayingCapture;
        stopAll("悬浮控制已关闭",false);int request=captureGeneration;
        Runnable close=()->{if(request==captureGeneration)hideControls();};
        if(injecting)cancelInjectedGesture(close);else close.run();
    }
    private void hideMarkers(){if(markers!=null)markers.setVisibility(View.GONE);}
    private void updateMarkers(){
        if(markers==null)return;
        boolean visible=!destroyed&&panel!=null&&showMarkers&&!recording&&!editingActions&&!choosingOperation&&!minimized&&!cancelingGesture&&capture==null&&!(engine!=null&&engine.isRunning()&&!engine.isPaused())&&plan!=null&&geometryOkay();
        markers.setVisibility(visible?View.VISIBLE:View.GONE);
        if(visible)markers.invalidate();
    }

    private void showOperationMenu(){
        boolean injecting=(engine!=null&&engine.isInFlight())||replayingCapture;
        stopAll("选择操作",false);int request=captureGeneration;
        Runnable open=()->{if(destroyed||request!=captureGeneration||panel==null)return;choosingOperation=true;panel.setVisibility(View.GONE);hideMarkers();renderOperationMenu();};
        if(injecting)cancelInjectedGesture(open);else open.run();
    }
    private void renderOperationMenu(){
        if(!choosingOperation||inputLayer==null)return;
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackgroundColor(0x66000000);backdrop.setClickable(true);
        LinearLayout card=Ui.column(this);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(Ui.card(this));
        card.addView(Ui.title(this,"添加操作",20));Ui.space(card,12);
        ScrollView list=new ScrollView(this);LinearLayout options=Ui.column(this);list.addView(options);card.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        for(String kind:new String[]{"点击","滑动","长按","多指"}){
            options.addView(Ui.button(this,kind,false,()->{choosingOperation=false;clearInputLayer();panel.setVisibility(View.VISIBLE);capture(kind,-1);}));Ui.space(options,8);
        }
        Ui.space(card,8);card.addView(Ui.button(this,"取消",false,()->{choosingOperation=false;clearInputLayer();panel.setVisibility(View.VISIBLE);refreshPanel();updateMarkers();}));
        Point size=screen();FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(Math.min(dp(400),size.x-dp(24)),Math.min(dp(420),Math.max(dp(200),size.y-dp(80))),Gravity.CENTER);backdrop.addView(card,layout);
        inputLayer.removeAllViews();inputLayer.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));inputLayer.setVisibility(View.VISIBLE);
    }

    private void showActionSettings(){
        boolean injecting=(engine!=null&&engine.isInFlight())||replayingCapture;
        stopAll("动作设置",false);int request=captureGeneration;
        Runnable open=()->{
            if(destroyed||request!=captureGeneration||panel==null||inputLayer==null)return;
            plan=store.selected();editingActions=true;pendingDelete=-1;actionListPosition=0;
            hideMarkers();panel.setVisibility(View.GONE);renderActionSettings();
        };
        if(injecting)cancelInjectedGesture(open);else open.run();
    }

    private void closeActionSettings(){
        editingActions=false;choosingOperation=false;pendingDelete=-1;actionList=null;clearInputLayer();
        if(panel!=null)panel.setVisibility(minimized?View.GONE:View.VISIBLE);
        notice="动作设置已关闭";refreshPanel();updateMarkers();
    }

    private void renderActionSettings(){
        if(!editingActions||inputLayer==null)return;
        if(actionList!=null)actionListPosition=actionList.getScrollY();
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackgroundColor(0x66000000);backdrop.setClickable(true);
        LinearLayout card=Ui.column(this);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(Ui.card(this));
        card.addView(Ui.title(this,"动作设置",20));Ui.space(card,6);
        card.addView(Ui.text(this,plan.name+" · "+plan.actions.size()+" 个动作",13,Ui.MUTED));Ui.space(card,12);
        ScrollView list=new ScrollView(this);actionList=list;
        LinearLayout rows=Ui.column(this);list.addView(rows);card.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        if(plan.actions.isEmpty()){
            TextView empty=Ui.text(this,"暂无动作",15,Ui.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(28),0,dp(28));rows.addView(empty);
        }
        for(int i=0;i<plan.actions.size();i++){
            final int index=i;Plan.Action action=plan.actions.get(i);
            LinearLayout row=Ui.column(this);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setBackground(Ui.bg(Ui.PALE,dp(12)));
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.bottomMargin=dp(10);rows.addView(row,rowParams);
            row.addView(Ui.title(this,(i+1)+". "+action.label,16));Ui.space(row,5);
            row.addView(Ui.text(this,action.durationMs()+" ms · 间隔 "+action.waitAfterMs+" ms · 重复 "+action.repeat+" 次",12,Ui.MUTED));Ui.space(row,10);
            if(pendingDelete==i){
                row.addView(Ui.text(this,"删除这个动作？",13,Ui.RED));Ui.space(row,6);
                LinearLayout confirmation=Ui.row(this);
                Button confirm=Ui.button(this,"确认删除",false,()->{
                    plan.actions.remove(index);store.save(plan);pendingDelete=-1;
                    notice="已删除动作 "+(index+1);refreshPanel();renderActionSettings();
                });confirm.setTextColor(Ui.RED);Ui.weight(confirmation,confirm);
                Ui.weight(confirmation,Ui.button(this,"取消",false,()->{pendingDelete=-1;renderActionSettings();}));row.addView(confirmation);
            }else{
                Button delete=Ui.button(this,"删除",false,()->{pendingDelete=index;renderActionSettings();});delete.setTextColor(Ui.RED);row.addView(delete);
            }
        }
        Ui.space(card,10);card.addView(Ui.button(this,"关闭",true,this::closeActionSettings));
        Point size=screen();int width=Math.min(dp(440),size.x-dp(24)),height=Math.min(dp(620),Math.max(dp(180),size.y-dp(80)));
        FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(width,height,Gravity.CENTER);backdrop.addView(card,layout);
        inputLayer.removeAllViews();inputLayer.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));inputLayer.setVisibility(View.VISIBLE);
        final int restore=actionListPosition;list.post(()->{if(actionList==list)list.scrollTo(0,restore);});
    }

    private final class MarkerView extends View{
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        MarkerView(){super(ClickService.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);Point size=screen();int[] origin=new int[2];getLocationOnScreen(origin);
            for(int i=0;i<plan.actions.size();i++)for(Plan.Stroke s:plan.actions.get(i).strokes){
                if(s.points.isEmpty())continue;paint.setColor(Ui.GREEN);paint.setStrokeWidth(dp(3));paint.setStyle(Paint.Style.STROKE);Path path=new Path();
                for(int j=0;j<s.points.size();j++){Plan.Point p=s.points.get(j);float x=p.x*size.x-origin[0],y=p.y*size.y-origin[1];if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}canvas.drawPath(path,paint);
                Plan.Point p=s.points.get(0);float x=p.x*size.x-origin[0],y=p.y*size.y-origin[1];paint.setStyle(Paint.Style.FILL);paint.setColor(Ui.GREEN);canvas.drawCircle(x,y,dp(16),paint);paint.setColor(Ui.ON_ACCENT);paint.setTextSize(dp(13));paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.valueOf(i+1),x,y+dp(5),paint);
            }
        }
    }
    private final class CaptureView extends View{
        private final String kind;private final int editIndex;
        private final Map<Integer,Plan.Stroke> active=new LinkedHashMap<>();
        private final List<Plan.Stroke> strokes=new ArrayList<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private long started;private boolean invalid;
        CaptureView(String kind,int index){super(ClickService.this);this.kind=kind;editIndex=index;setBackgroundColor(0x12000000);setContentDescription("手势选取区域");}
        private Plan.Point point(MotionEvent e,int index){Point size=screen();float x=e.getRawX()-e.getX()+e.getX(index),y=e.getRawY()-e.getY()+e.getY(index);return new Plan.Point(Math.max(0,Math.min(1,x/size.x)),Math.max(0,Math.min(1,y/size.y)));}
        private void sample(Plan.Stroke stroke,Plan.Point p){if(stroke.points.size()>=1000)return;if(stroke.points.isEmpty()){stroke.points.add(p);return;}Plan.Point q=stroke.points.get(stroke.points.size()-1);Point size=screen();if(Math.hypot((p.x-q.x)*size.x,(p.y-q.y)*size.y)>=dp(2))stroke.points.add(p);}
        @Override public boolean onTouchEvent(MotionEvent e){
            int type=e.getActionMasked(),idx=e.getActionIndex();long now=SystemClock.uptimeMillis();
            if(type==MotionEvent.ACTION_DOWN){active.clear();strokes.clear();invalid=false;started=now;}
            if(type==MotionEvent.ACTION_DOWN||type==MotionEvent.ACTION_POINTER_DOWN){
                if(strokes.size()>=10){invalid=true;return true;}
                Plan.Stroke s=new Plan.Stroke();s.startMs=now-started;sample(s,point(e,idx));active.put(e.getPointerId(idx),s);strokes.add(s);
            }
            if(type==MotionEvent.ACTION_MOVE){for(int i=0;i<e.getPointerCount();i++){Plan.Stroke s=active.get(e.getPointerId(i));if(s!=null)sample(s,point(e,i));}}
            if(type==MotionEvent.ACTION_POINTER_UP||type==MotionEvent.ACTION_UP){Plan.Stroke s=active.remove(e.getPointerId(idx));if(s!=null){sample(s,point(e,idx));s.durationMs=Math.max(1,now-started-s.startMs);}}
            if(type==MotionEvent.ACTION_CANCEL){active.clear();strokes.clear();invalid=true;invalidate();return true;}
            invalidate();
            if(type==MotionEvent.ACTION_UP){
                if(invalid||now-started>59000){tell("手势过长或触点过多，请重新选取");return true;}
                Plan.Action a=new Plan.Action();a.label=kind;
                if("点击".equals(kind)||"长按".equals(kind)){Plan.Stroke s=strokes.get(0).copy();Plan.Point p=s.points.get(0);s.points.clear();s.points.add(p);s.startMs=0;s.durationMs="长按".equals(kind)?800:50;a.strokes.add(s);}
                else{for(Plan.Stroke s:strokes)a.strokes.add(s.copy());}
                if("多指".equals(kind)&&a.strokes.size()<2){tell("请用至少两根手指同时点按或滑动");return true;}
                if("滑动".equals(kind)&&a.strokes.get(0).points.size()<2){tell("请划出一段路径");return true;}
                if("录制".equals(kind)){boolean moved=false;Point size=screen();for(Plan.Stroke s:a.strokes){Plan.Point first=s.points.get(0);for(Plan.Point p:s.points)if(Math.hypot((p.x-first.x)*size.x,(p.y-first.y)*size.y)>dp(8))moved=true;}a.label=a.strokes.size()>1?"多指":moved?"滑动":a.durationMs()>=350?"长按":"点击";if(!moved&&a.strokes.size()==1){Plan.Stroke s=a.strokes.get(0);Plan.Point p=s.points.get(0);s.points.clear();s.points.add(p);s.durationMs=Math.max(30,s.durationMs);}}
                long gestureStart=started;
                handler.post(()->{if(capture==this&&!editingActions)captured(a,editIndex,gestureStart);});
            }return true;
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);Point size=screen();int[] origin=new int[2];getLocationOnScreen(origin);paint.setColor(Ui.GREEN);paint.setStrokeWidth(dp(5));paint.setStyle(Paint.Style.STROKE);for(Plan.Stroke s:strokes){Path p=new Path();for(int i=0;i<s.points.size();i++){Plan.Point q=s.points.get(i);float x=q.x*size.x-origin[0],y=q.y*size.y-origin[1];if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}c.drawPath(p,paint);if(!s.points.isEmpty()){Plan.Point q=s.points.get(s.points.size()-1);c.drawCircle(q.x*size.x-origin[0],q.y*size.y-origin[1],dp(17),paint);}}}
    }
}
