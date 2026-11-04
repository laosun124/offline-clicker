package local.offline.clicker;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An entirely local target for checking taps, holds, swipes and multi-touch. */
public final class PracticeActivity extends Activity {
    private boolean darkAtCreation;private TextView summary;private final int[] clicks=new int[3];private int holds,gestures,maxFingers;private Button[] targets=new Button[3];
    @Override public void onCreate(Bundle saved){Ui.applyTheme(this);super.onCreate(saved);Ui.systemBars(this);darkAtCreation=Ui.isDark(this);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);LinearLayout root=Ui.column(this);root.setPadding(dp(20),dp(20),dp(20),dp(20));root.setBackgroundColor(Ui.BG);scroll.addView(root);setContentView(scroll);
        root.addView(Ui.title(this,"练习面板",27));Ui.space(root,8);root.addView(Ui.text(this,"在这里选点、录制、回放。请先把悬浮条拖到不挡目标的位置。",14,Ui.MUTED));Ui.space(root,15);
        summary=Ui.text(this,"",14,Ui.GREEN);root.addView(summary);Ui.space(root,15);
        for(int i=0;i<3;i++){final int index=i;targets[i]=Ui.button(this,"",i==1,()->{clicks[index]++;refresh();});targets[i].setOnLongClickListener(v->{holds++;refresh();return true;});root.addView(targets[i],new LinearLayout.LayoutParams(-1,dp(58)));Ui.space(root,10);}
        root.addView(Ui.text(this,"下方区域用于滑动 / 多指测试",13,Ui.MUTED));Ui.space(root,8);TouchPad pad=new TouchPad();root.addView(pad,new LinearLayout.LayoutParams(-1,dp(220)));Ui.space(root,10);
        LinearLayout buttons=Ui.row(this);Ui.weight(buttons,Ui.button(this,"清零",false,()->{for(int i=0;i<3;i++)clicks[i]=0;holds=gestures=maxFingers=0;pad.path.reset();pad.invalidate();refresh();}));Ui.weight(buttons,Ui.button(this,"悬浮控制",true,()->{ClickService s=ClickService.get();if(s!=null)s.showControls();else android.widget.Toast.makeText(this,"请先返回主页开启无障碍服务",0).show();}));Ui.weight(buttons,Ui.button(this,"返回",false,this::finish));root.addView(buttons);refresh();}
    @Override public void onResume(){super.onResume();if(Ui.isDark(this)!=darkAtCreation)recreate();}
    private void refresh(){for(int i=0;i<3;i++)targets[i].setText("目标 "+(char)('A'+i)+"    "+clicks[i]+" 次");summary.setText("长按 "+holds+" 次  ·  手势 "+gestures+" 次  ·  最多 "+maxFingers+" 指");}
    private int dp(int n){return Ui.dp(this,n);}
    private final class TouchPad extends View{final Path path=new Path();final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int fingers;
        TouchPad(){super(PracticeActivity.this);setBackground(Ui.card(PracticeActivity.this));setContentDescription("手势练习区");setMinimumHeight(dp(100));}
        @Override public boolean onTouchEvent(MotionEvent e){getParent().requestDisallowInterceptTouchEvent(true);int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){path.reset();path.moveTo(e.getX(),e.getY());fingers=1;}if(action==MotionEvent.ACTION_POINTER_DOWN)fingers=Math.max(fingers,e.getPointerCount());if(action==MotionEvent.ACTION_MOVE)path.lineTo(e.getX(),e.getY());if(action==MotionEvent.ACTION_UP){gestures++;maxFingers=Math.max(maxFingers,fingers);refresh();performClick();}invalidate();return true;}
        @Override public boolean performClick(){return super.performClick();}
        @Override protected void onDraw(Canvas c){paint.setColor(Ui.GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(5));c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(15));c.drawText("滑动轨迹会显示在这里",dp(15),dp(30),paint);}
    }
}
