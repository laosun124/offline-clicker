package local.offline.clicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int EXPORT=40,IMPORT=41;
    private static final int ACCESS=0,EDITOR=1,PLANS=2;
    private static final String[] TAB_LABELS={"设置","动作","方案"};
    private Store store;
    private boolean darkAtCreation;
    private Plan plan;
    private LinearLayout page,steps;
    private ScrollView scroll;
    private Spinner planSelector;
    private TextView accessStatus,runStatus,planInfo,stepInfo;
    private Button accessButton;
    private int selectedTab=EDITOR;
    private final int[] scrollPositions=new int[3];
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshPlans=this::rebuild;
    private final SharedPreferences.OnSharedPreferenceChangeListener plansChanged=(prefs,key)->{
        if("plans".equals(key)||"selected".equals(key)){handler.removeCallbacks(refreshPlans);handler.post(refreshPlans);}
    };
    private final Runnable poll=new Runnable(){
        public void run(){updateStatus();handler.postDelayed(this,700);}
    };

    @Override public void onCreate(Bundle state) {
        Ui.applyTheme(this);super.onCreate(state);Ui.systemBars(this);darkAtCreation=Ui.isDark(this);store=new Store(this);
        selectedTab=getPreferences(MODE_PRIVATE).getInt("last_tab",EDITOR);
        if(state!=null){
            selectedTab=state.getInt("tab",selectedTab);
            int[] positions=state.getIntArray("scroll");
            if(positions!=null&&positions.length==3)System.arraycopy(positions,0,scrollPositions,0,3);
        }
        if(state==null&&getIntent().hasExtra("tab"))selectedTab=getIntent().getIntExtra("tab",EDITOR);
        getIntent().removeExtra("tab");
        selectedTab=Math.max(0,Math.min(2,selectedTab));render();
    }

    @Override public void onResume(){
        super.onResume();if(Ui.isDark(this)!=darkAtCreation){recreate();return;}if(store!=null){store.observe(plansChanged);rebuild();}handler.removeCallbacks(poll);handler.post(poll);
    }
    @Override public void onPause(){rememberScroll();if(store!=null)store.stopObserving(plansChanged);handler.removeCallbacks(refreshPlans);handler.removeCallbacks(poll);super.onPause();}
    @Override public void onSaveInstanceState(Bundle state){
        rememberScroll();state.putInt("tab",selectedTab);state.putIntArray("scroll",scrollPositions.clone());super.onSaveInstanceState(state);
    }
    @Override public void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(intent.hasExtra("tab")){int tab=Math.max(0,Math.min(2,intent.getIntExtra("tab",EDITOR)));intent.removeExtra("tab");showTab(tab);}
    }

    private void rememberScroll(){if(scroll!=null&&scroll.isLaidOut())scrollPositions[selectedTab]=scroll.getScrollY();}
    private void rebuild(){rememberScroll();render();}
    private void showTab(int tab){showTab(tab,false);}
    private void showTab(int tab,boolean fromTop){
        rememberScroll();selectedTab=tab;if(fromTop)scrollPositions[tab]=0;
        getPreferences(MODE_PRIVATE).edit().putInt("last_tab",tab).apply();
        scroll=null;render();
    }

    private void render(){
        try{plan=store.selected();}
        catch(IllegalStateException ex){new AlertDialog.Builder(this).setTitle("无法读取方案").setMessage(ex.getMessage()).setPositiveButton("关闭",(d,w)->finish()).show();return;}
        accessStatus=runStatus=planInfo=stepInfo=null;accessButton=null;steps=null;planSelector=null;
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=Ui.column(this);page.setPadding(dp(20),dp(24),dp(20),dp(12));scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(selectedTab==ACCESS)renderAccess();
        else if(selectedTab==EDITOR)renderEditor();
        else renderPlans();
        View divider=new View(this);divider.setBackgroundColor(Ui.BORDER);
        root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout navigation=Ui.row(this);navigation.setPadding(dp(10),dp(8),dp(10),dp(8));navigation.setBackgroundColor(Ui.BG);
        for(int i=0;i<TAB_LABELS.length;i++){
            final int target=i;boolean active=i==selectedTab;
            Button tab=Ui.button(this,TAB_LABELS[i],active,()->{if(selectedTab!=target)showTab(target);});
            if(!active){tab.setBackground(Ui.bg(Ui.BG,dp(13)));tab.setTextColor(Ui.MUTED);}
            tab.setSelected(active);tab.setContentDescription(TAB_LABELS[i]+(active?"，当前页面":""));
            Ui.weight(navigation,tab);
        }
        root.addView(navigation,new LinearLayout.LayoutParams(-1,-2));setContentView(root);
        final ScrollView current=scroll;final int restore=scrollPositions[selectedTab];
        current.post(()->{if(scroll==current)current.scrollTo(0,restore);});updateStatus();
    }

    private void pageTitle(String title){page.addView(Ui.title(this,title,26));Ui.space(page,20);}

    private void renderAccess(){
        pageTitle("设置");
        LinearLayout appearance=card();appearance.addView(Ui.title(this,"外观",17));Ui.space(appearance,12);
        LinearLayout modes=Ui.row(this);String[] labels={"浅色","深色","跟随系统"};
        for(int i=0;i<labels.length;i++){
            final int mode=i;
            Ui.weight(modes,Ui.button(this,labels[i],Ui.mode(this)==i,()->{
                if(Ui.mode(this)!=mode){Ui.setMode(this,mode);recreate();}
            }));
        }
        appearance.addView(modes);

        LinearLayout serviceCard=card();serviceCard.addView(Ui.title(this,"无障碍服务",17));Ui.space(serviceCard,10);
        accessStatus=Ui.text(this,"",15,Ui.MUTED);serviceCard.addView(accessStatus);Ui.space(serviceCard,16);
        accessButton=Ui.button(this,"开启无障碍服务",true,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        serviceCard.addView(accessButton);Ui.space(serviceCard,10);
        serviceCard.addView(Ui.text(this,"在系统设置中选择「离线连点器」。",13,Ui.MUTED));
        LinearLayout restricted=card();restricted.addView(Ui.title(this,"无法开启时",17));Ui.space(restricted,10);
        restricted.addView(Ui.text(this,"若系统提示受限设置，请在应用信息页查看“允许受限设置”，完成系统确认后再开启服务。",14,Ui.MUTED));Ui.space(restricted,14);
        restricted.addView(Ui.button(this,"应用信息",false,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));
    }

    private void renderEditor(){
        pageTitle("动作编辑");
        LinearLayout selected=card();selected.addView(Ui.text(this,"方案",13,Ui.MUTED));Ui.space(selected,6);
        List<Plan> available=store.all();String[] choices=new String[available.size()+1];int selectedIndex=0;
        for(int i=0;i<available.size();i++){choices[i]=available.get(i).name;if(available.get(i).id.equals(plan.id))selectedIndex=i;}
        choices[available.size()]="＋ 新建方案";
        Spinner selector=new Spinner(this,Spinner.MODE_DROPDOWN);planSelector=selector;
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);selector.setAdapter(adapter);
        selector.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));selector.setPadding(dp(8),0,dp(8),0);selector.setContentDescription("选择或新建方案");
        LinearLayout selectionFrame=Ui.row(this);selectionFrame.setBackground(Ui.selector(this));selectionFrame.setPadding(dp(6),0,dp(6),0);
        selectionFrame.addView(selector,new LinearLayout.LayoutParams(-1,dp(54)));selector.setPopupBackgroundDrawable(Ui.bg(Ui.SURFACE,dp(12)));
        selected.addView(selectionFrame,new LinearLayout.LayoutParams(-1,-2));selector.setSelection(selectedIndex,false);
        final int currentIndex=selectedIndex;
        selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(planSelector!=selector)return;
                if(position==available.size()){selector.setSelection(currentIndex,false);createPlanDialog(null,true);}
                else if(!available.get(position).id.equals(plan.id)){selectPlan(available.get(position).id,true);}
            }
        });
        Ui.space(selected,12);planInfo=Ui.text(this,"",14,Ui.MUTED);selected.addView(planInfo);Ui.space(selected,12);
        selected.addView(Ui.button(this,"修改参数",false,this::editPlan));
        LinearLayout actions=card();actions.addView(Ui.title(this,"动作顺序",17));Ui.space(actions,8);
        stepInfo=Ui.text(this,"",13,Ui.MUTED);actions.addView(stepInfo);Ui.space(actions,10);steps=Ui.column(this);actions.addView(steps);
        actions.addView(Ui.button(this,"添加动作 / 录制",false,this::addingHelp));
        LinearLayout controls=card();controls.addView(Ui.title(this,"悬浮控制",17));Ui.space(controls,8);
        runStatus=Ui.text(this,"",13,Ui.MUTED);controls.addView(runStatus);Ui.space(controls,14);
        controls.addView(Ui.button(this,"显示悬浮控制",true,()->{ClickService service=service();if(service!=null)service.showControls();}));Ui.space(controls,9);
        LinearLayout tools=Ui.row(this);
        Ui.weight(tools,Ui.button(this,"练习面板",false,()->startActivity(new Intent(this,PracticeActivity.class))));
        Ui.weight(tools,Ui.button(this,"操作说明",false,this::help));controls.addView(tools);renderSteps();
    }

    private void renderPlans(){
        LinearLayout heading=Ui.row(this);heading.addView(Ui.title(this,"方案管理",26),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(Ui.button(this,"新建",false,()->createPlanDialog(null,true)),new LinearLayout.LayoutParams(dp(72),dp(48)));
        page.addView(heading);Ui.space(page,18);
        List<Plan> available=store.all();page.addView(Ui.text(this,"共 "+available.size()+" 个方案",13,Ui.MUTED));Ui.space(page,12);
        for(Plan item:available){
            LinearLayout box=card();LinearLayout title=Ui.row(this);
            TextView name=Ui.title(this,item.name,17);name.setMaxLines(2);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(item.id.equals(plan.id)){TextView current=Ui.text(this,"当前",12,Ui.GREEN);current.setPadding(dp(10),dp(5),dp(10),dp(5));current.setBackground(Ui.bg(Ui.PALE,dp(8)));title.addView(current);}
            box.addView(title);Ui.space(box,8);
            box.addView(Ui.text(this,item.actions.size()+" 个动作  ·  "+(item.rounds==0?"无限循环":item.rounds+" 轮"),13,Ui.MUTED));Ui.space(box,12);
            LinearLayout operations=Ui.row(this);
            Ui.weight(operations,Ui.button(this,"编辑",false,()->selectPlan(item.id,true)));
            Ui.weight(operations,Ui.button(this,"重命名",false,()->renamePlan(item)));
            Ui.weight(operations,Ui.button(this,"复制",false,()->createPlanDialog(item,false)));
            Button delete=Ui.button(this,"删除",false,()->deletePlan(item));delete.setTextColor(Ui.RED);Ui.weight(operations,delete);
            box.addView(operations);
        }
        LinearLayout backups=card();backups.addView(Ui.title(this,"导入与导出",17));Ui.space(backups,12);
        LinearLayout buttons=Ui.row(this);Ui.weight(buttons,Ui.button(this,"导入备份",false,this::importPlans));Ui.weight(buttons,Ui.button(this,"导出全部",false,this::exportPlans));backups.addView(buttons);
    }

    private LinearLayout card(){
        LinearLayout card=Ui.column(this);card.setPadding(dp(17),dp(17),dp(17),dp(17));card.setBackground(Ui.card(this));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(14));page.addView(card,params);return card;
    }

    private void updateStatus(){
        ClickService service=ClickService.get();
        if(accessStatus!=null){accessStatus.setText(service==null?"未开启":"已开启");accessStatus.setTextColor(service==null?Ui.MUTED:Ui.GREEN);}
        if(accessButton!=null)accessButton.setText(service==null?"开启无障碍服务":"管理无障碍服务");
        if(runStatus!=null)runStatus.setText(service==null?"请先开启无障碍服务":service.status());
    }
    private void renderSteps(){
        if(steps==null)return;
        steps.removeAllViews();
        planInfo.setText((plan.rounds==0?"无限循环":plan.rounds+" 轮")+"  ·  开始等待 "+seconds(plan.startDelayMs)+" 秒\n轮间等待 "+plan.roundDelayMs+" 毫秒");
        stepInfo.setText(plan.actions.isEmpty()?"暂无动作":"共 "+plan.actions.size()+" 个动作");
        for(int i=0;i<plan.actions.size();i++){
            final int index=i;Plan.Action a=plan.actions.get(i);LinearLayout box=Ui.column(this);box.setPadding(dp(10),dp(10),dp(10),dp(10));box.setBackground(Ui.bg(Ui.BG,dp(12)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);steps.addView(box,lp);
            TextView name=Ui.title(this,String.format("%02d",i+1)+"   "+a.label,15);box.addView(name);TextView desc=Ui.text(this,a.durationMs()+" ms  ·  之后等 "+a.waitAfterMs+" ms  ·  重复 "+a.repeat+" 次"+(a.strokes.size()>1?"  ·  "+a.strokes.size()+" 指":""),12,Ui.MUTED);box.addView(desc);name.setOnClickListener(v->editAction(index));desc.setOnClickListener(v->editAction(index));
            LinearLayout row=Ui.row(this);Ui.weight(row,Ui.button(this,"编辑",false,()->editAction(index)));Button up=Ui.button(this,"上移",false,()->move(index,-1));up.setEnabled(i>0);Ui.weight(row,up);Button down=Ui.button(this,"下移",false,()->move(index,1));down.setEnabled(i+1<plan.actions.size());Ui.weight(row,down);Ui.weight(row,Ui.button(this,"删除",false,()->new AlertDialog.Builder(this).setTitle("删除这个动作？").setMessage(a.label).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{plan.actions.remove(index);save();renderSteps();}).show()));box.addView(row);
        }
    }
    private void move(int index,int offset){Collections.swap(plan.actions,index,index+offset);save();renderSteps();}
    private void save(){store.save(plan);ClickService s=ClickService.get();if(s!=null)s.reload();}
    private ClickService service(){ClickService s=ClickService.get();if(s==null){new AlertDialog.Builder(this).setTitle("无障碍服务未开启").setMessage("请先在设置页面开启无障碍服务。").setNegativeButton("取消",null).setPositiveButton("去设置",(d,w)->showTab(ACCESS)).show();}return s;}
    private void createPlanDialog(Plan source, boolean openEditor) {
        LinearLayout fields=form();
        String initial=source==null?"新的方案":source.name.substring(0,Math.min(76,source.name.length()))+" 副本";
        EditText name=input(fields,"方案名称",initial,false);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(source==null?"新建方案":"复制方案")
                .setView(fields).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            try {
                Plan created=source==null?new Plan():source.copy();
                created.id=java.util.UUID.randomUUID().toString();
                created.name=name.getText().toString().trim();
                created.validate();store.save(created);
                if(openEditor){
                    store.select(created.id);ClickService service=ClickService.get();
                    if(service!=null)service.reload();
                }
                dialog.dismiss();
                if(openEditor)showTab(EDITOR,true);else rebuild();
            } catch(IllegalArgumentException ex){toast(ex.getMessage());}
        }));
        dialog.show();
    }

    private void renamePlan(Plan target) {
        LinearLayout fields=form();EditText name=input(fields,"方案名称",target.name,false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("重命名方案")
                .setView(fields).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            try {
                Plan renamed=target.copy();renamed.name=name.getText().toString().trim();renamed.validate();
                boolean current=store.selected().id.equals(target.id);store.save(renamed);
                if(current){ClickService service=ClickService.get();if(service!=null)service.reload();}
                dialog.dismiss();rebuild();
            } catch(IllegalArgumentException ex){toast(ex.getMessage());}
        }));dialog.show();
    }

    private void selectPlan(String id, boolean openEditor) {
        if(!store.selected().id.equals(id)){
            store.select(id);ClickService service=ClickService.get();if(service!=null)service.reload();
        }
        if(openEditor)showTab(EDITOR,true);else rebuild();
    }

    private void editPlan(){
        LinearLayout form=form();EditText name=input(form,"方案名称",plan.name,false),rounds=input(form,"循环轮数（0 表示无限循环）",String.valueOf(plan.rounds),true),delay=input(form,"开始前等待（秒，最多 86400）",seconds(plan.startDelayMs),true),gap=input(form,"每轮结束后额外等待（毫秒）",String.valueOf(plan.roundDelayMs),true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("修改参数").setView(wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{Plan p=plan.copy();p.name=name.getText().toString().trim();p.rounds=integer(rounds);p.startDelayMs=millis(delay);p.roundDelayMs=number(gap);p.validate();plan=p;save();d.dismiss();rebuild();}catch(IllegalArgumentException ex){toast(ex.getMessage());}}));d.show();
    }
    private void editAction(int index){
        Plan.Action action=plan.actions.get(index);LinearLayout form=form();EditText name=input(form,"动作名称",action.label,false),duration=input(form,"手势时长（毫秒，1–59000）",String.valueOf(action.durationMs()),true),gap=input(form,"每次执行后等待（毫秒）",String.valueOf(action.waitAfterMs),true),repeats=input(form,"本动作重复次数（1–10000）",String.valueOf(action.repeat),true);
        final EditText x,y;
        if(action.strokes.size()==1){Plan.Point p=action.strokes.get(0).points.get(0);x=input(form,"起点 X（屏幕宽度百分比，0–100）",String.format(java.util.Locale.ROOT,"%.2f",p.x*100),true);y=input(form,"起点 Y（屏幕高度百分比，0–100）",String.format(java.util.Locale.ROOT,"%.2f",p.y*100),true);form.addView(Ui.text(this,"修改起点会平移整条路径。多指动作可在悬浮控制中重新录制。",12,Ui.MUTED));}else{x=y=null;}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("动作 "+(index+1)).setView(wrap(form)).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{Plan.Action a=action.copy();a.label=name.getText().toString().trim();long t=number(duration);if(t<1||t>59000)throw new IllegalArgumentException("时长需要在 1–59000 毫秒之间");a.setDuration(t);a.waitAfterMs=number(gap);a.repeat=integer(repeats);if(x!=null){float nx=(float)decimal(x)/100,ny=(float)decimal(y)/100;Plan.Stroke s=a.strokes.get(0);float dx=nx-s.points.get(0).x,dy=ny-s.points.get(0).y;for(Plan.Point p:s.points){p.x+=dx;p.y+=dy;}}a.validate();plan.actions.set(index,a);save();d.dismiss();renderSteps();}catch(IllegalArgumentException ex){toast(ex.getMessage());}}));d.show();
    }
    private void addingHelp(){new AlertDialog.Builder(this).setTitle("在目标应用上添加动作").setMessage("1. 显示悬浮控制，再切换到目标应用。\n\n2. 点「添加操作」，选择点击、滑动、长按或多指，在屏幕上选取位置或划出路径。\n\n3. 或点「录制动作」，逐个操作；每个手势会保存并转发，等提示恢复“录制中”再做下一个手势。\n\n4. 完成后点「完成录制」，再点「开始」。点击停止可随时结束。\n\n可先在练习面板试用。悬浮条挡住目标时，拖动标题将它移开。").setNegativeButton("知道了",null).setPositiveButton("显示悬浮控制",(d,w)->{ClickService s=service();if(s!=null)s.showControls();}).show();}
    private void deletePlan(Plan target) {
        if(store.all().size()==1){new AlertDialog.Builder(this).setTitle("无法删除方案").setMessage("至少需要保留一个方案。").setPositiveButton("知道了",null).show();return;}
        new AlertDialog.Builder(this).setTitle("删除方案「"+target.name+"」？")
                .setMessage("该方案中的动作也会删除。")
                .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                    boolean current=store.selected().id.equals(target.id);store.delete(target.id);
                    if(current){ClickService service=ClickService.get();if(service!=null)service.reload();}
                    rebuild();
                }).show();
    }

    private void help() {
        new AlertDialog.Builder(this).setTitle("操作说明")
                .setMessage("选点与录制\n显示悬浮控制后，切到目标应用。点添加操作，再选择点击、滑动、长按或多指，或录制动作。转发手势期间请等待提示恢复录制中，再做下一个手势。\n\n运行\n默认执行 1 轮、开始前等待 3 秒。修改参数中可修改，循环次数 0 表示无限。间隔从上个手势完成后开始计算。\n\n停止\n回放时控制会缩到小入口并避开触点。点击入口可暂停并展开，再点击停止或继续。暂停保留进度，被触摸中断的动作在继续时重试。锁屏或屏幕方向变化时也会停止。\n\n位置\n拖动悬浮条标题可以移动它。屏幕方向、分屏、缩放或目标布局变化后，请重新检查动作位置。")
                .setPositiveButton("关闭",null).show();
    }

    private void exportPlans(){try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"离线连点器-方案备份.json");startActivityForResult(i,EXPORT);}catch(RuntimeException ex){toast("当前系统没有可用的文件选择器");}}
    private void importPlans(){try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,IMPORT);}catch(RuntimeException ex){toast("当前系统没有可用的文件选择器");}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(request==EXPORT){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("无法写入文件");out.write(store.backup().getBytes(StandardCharsets.UTF_8));}toast("全部方案已导出");}
        else if(request==IMPORT){String text;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new java.io.IOException("无法读取文件");byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1048576)throw new java.io.IOException("备份文件不能超过 1 MB");out.write(buffer,0,n);}text=new String(out.toByteArray(),StandardCharsets.UTF_8);}final String imported=text;new AlertDialog.Builder(this).setTitle("导入方案？").setMessage("将新增备份中的方案，已有方案不会覆盖。").setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->{try{int count=store.importBackup(imported);ClickService s=ClickService.get();if(s!=null)s.reload();render();toast("已导入 "+count+" 个方案");}catch(Exception ex){toast("导入失败："+ex.getMessage());}}).show();}
    }catch(Exception ex){toast("操作失败："+ex.getMessage());}}
    private LinearLayout form(){LinearLayout l=Ui.column(this);l.setPadding(dp(22),dp(12),dp(22),dp(10));return l;}
    private ScrollView wrap(View v){ScrollView s=new ScrollView(this);s.addView(v);return s;}
    private EditText input(LinearLayout parent,String title,String value,boolean numeric){parent.addView(Ui.text(this,title,13,Ui.MUTED));EditText e=new EditText(this);e.setSingleLine();e.setText(value);e.setTextSize(16);e.setInputType(numeric?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));Ui.space(parent,9);return e;}
    private static long number(EditText e){try{return Long.parseLong(e.getText().toString().trim());}catch(NumberFormatException ex){throw new IllegalArgumentException("请输入范围内的整数");}}
    private static int integer(EditText e){long n=number(e);if(n<0||n>Integer.MAX_VALUE)throw new IllegalArgumentException("数值超出范围");return(int)n;}
    private static double decimal(EditText e){try{double n=Double.parseDouble(e.getText().toString().trim());if(!Double.isFinite(n)||n<0)throw new IllegalArgumentException("请输入有效的非负数");return n;}catch(NumberFormatException ex){throw new IllegalArgumentException("请输入有效数字");}}
    private static long millis(EditText e){double n=decimal(e);if(n>86400)throw new IllegalArgumentException("开始前等待最多 86400 秒");return Math.round(n*1000);}
    private static String seconds(long ms){return ms%1000==0?String.valueOf(ms/1000):String.format(java.util.Locale.ROOT,"%.2f",ms/1000.0);}
    private int dp(float n){return Ui.dp(this,n);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
}
