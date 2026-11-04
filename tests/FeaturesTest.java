package local.offline.clicker;

import java.util.Random;

public final class FeaturesTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static Plan.Action path(float...xy){
        Plan.Action a=new Plan.Action();Plan.Stroke stroke=new Plan.Stroke();
        for(int i=0;i<xy.length;i+=2)stroke.points.add(new Plan.Point(xy[i],xy[i+1]));a.strokes.add(stroke);return a;
    }
    private static void themes(){
        check(!Appearance.isDark(Appearance.LIGHT,false),"light mode on light system");
        check(!Appearance.isDark(Appearance.LIGHT,true),"light mode must override dark system");
        check(Appearance.isDark(Appearance.DARK,false),"dark mode must override light system");
        check(Appearance.isDark(Appearance.DARK,true),"dark mode on dark system");
        check(!Appearance.isDark(Appearance.SYSTEM,false),"follow light system");
        check(Appearance.isDark(Appearance.SYSTEM,true),"follow dark system");
        check(!Appearance.isDark(99,true),"invalid stored mode uses light fallback");
    }
    private static void pathIntersections(){
        check(!OverlayPosition.safe(path(.0f,.5f,1f,.5f),100,100,40,40,20,20,0),"crossing a button between sampled endpoints must be rejected");
        check(!OverlayPosition.safe(path(.5f,0,.5f,1),100,100,40,40,20,20,0),"vertical swipe crossing handle");
        check(!OverlayPosition.safe(path(.4f,.4f),100,100,40,40,20,20,0),"touch on boundary is not safe");
        check(!OverlayPosition.safe(path(.35f,.5f),100,100,40,40,20,20,6),"extra touch margin");
        check(OverlayPosition.safe(path(0,0,.2f,.2f),100,100,40,40,20,20,0),"distant stroke is safe");
        check(OverlayPosition.safe(path(0,.4f,.4f,0),100,100,30,30,20,20,0),"overlapping bounding boxes alone must not reject a diagonal");
        Plan.Action multi=path(.9f,.9f);multi.strokes.add(path(.5f,.5f).strokes.get(0));
        check(!OverlayPosition.safe(multi,100,100,40,40,20,20,0),"every finger is considered");
    }
    private static void placement(){
        Plan.Action tap=path(.02f,.26f);int[] p=OverlayPosition.find(tap,500,900,44,64,28,10,0,200);
        check(p!=null&&p[0]==456,"a tap under the preferred edge handle moves it to the other side");
        Plan.Action bothEdges=path(0,0,0,1);bothEdges.strokes.add(path(1,0,1,1).strokes.get(0));
        p=OverlayPosition.find(bothEdges,500,900,44,64,28,10,0,200);
        check(p!=null&&p[0]>0&&p[0]<456,"both edges occupied uses an unoccupied interior position");
        check(OverlayPosition.find(path(.5f,.5f),20,30,44,64,10,8,0,10)==null,"no handle fits a tiny display");
        Plan.Action filled=new Plan.Action();Plan.Stroke grid=new Plan.Stroke();
        for(int y=0;y<=300;y+=15){if((y/15)%2==0){grid.points.add(new Plan.Point(0,y/300f));grid.points.add(new Plan.Point(1,y/300f));}else{grid.points.add(new Plan.Point(1,y/300f));grid.points.add(new Plan.Point(0,y/300f));}}filled.strokes.add(grid);
        check(OverlayPosition.find(filled,200,300,44,64,20,10,0,100)==null,"a screen-covering gesture cannot be dispatched through the controls");
    }
    private static void independentSamples(){
        Random random=new Random(5012);
        for(int trial=0;trial<40;trial++){
            Plan.Action action=new Plan.Action();
            for(int finger=0;finger<3;finger++){
                Plan.Stroke stroke=new Plan.Stroke();for(int n=0;n<4;n++)stroke.points.add(new Plan.Point(random.nextFloat(),random.nextFloat()));action.strokes.add(stroke);
            }
            int[] p=OverlayPosition.find(action,1080,2400,132,192,84,30,0,400);
            check(p!=null,"ordinary multi-finger paths leave a safe handle location");
            boolean hit=false;
            for(Plan.Stroke stroke:action.strokes)for(int i=1;i<stroke.points.size();i++){
                Plan.Point a=stroke.points.get(i-1),b=stroke.points.get(i);
                for(int sample=0;sample<=1000;sample++){
                    float t=sample/1000f,x=(a.x+(b.x-a.x)*t)*1080,y=(a.y+(b.y-a.y)*t)*2400;
                    if(x>=p[0]-30&&x<=p[0]+132+30&&y>=p[1]-30&&y<=p[1]+192+30)hit=true;
                }
            }
            check(!hit,"independent path sampling must not hit the chosen handle");
        }
    }
    public static void main(String[] args){themes();pathIntersections();placement();independentSamples();System.out.println("Feature tests passed: "+checks+" checks");}
}
