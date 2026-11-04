package local.offline.clicker;

/** Finds a control handle that none of an action's complete touch paths can hit. */
public final class OverlayPosition {
    public static int[] find(Plan.Action action,int width,int height,int dockWidth,int dockHeight,int inset,int margin,int preferredX,int preferredY){
        int maxX=width-dockWidth,maxY=height-dockHeight-inset;
        if(maxX<0||maxY<inset)return null;
        int y=Math.max(inset,Math.min(maxY,preferredY));
        int first=preferredX<width/2?0:maxX,second=first==0?maxX:0;
        if(safe(action,width,height,first,y,dockWidth,dockHeight,margin))return new int[]{first,y};
        if(safe(action,width,height,second,y,dockWidth,dockHeight,margin))return new int[]{second,y};
        for(int yy=inset;yy<=maxY;yy+=Math.max(1,dockHeight+margin)){
            if(safe(action,width,height,first,yy,dockWidth,dockHeight,margin))return new int[]{first,yy};
            if(safe(action,width,height,second,yy,dockWidth,dockHeight,margin))return new int[]{second,yy};
        }
        // For a gesture along both edges, use an unoccupied interior position.
        for(int yy=inset;yy<=maxY;yy+=Math.max(1,dockHeight+margin))for(int xx=0;xx<=maxX;xx+=Math.max(1,dockWidth+margin))
            if(safe(action,width,height,xx,yy,dockWidth,dockHeight,margin))return new int[]{xx,yy};
        return null;
    }
    public static boolean safe(Plan.Action action,int width,int height,int x,int y,int w,int h,int margin){
        double left=x-margin,top=y-margin,right=x+w+margin,bottom=y+h+margin;
        for(Plan.Stroke stroke:action.strokes){
            if(stroke.points.isEmpty())continue;
            Plan.Point previous=stroke.points.get(0);
            if(inside(previous.x*width,previous.y*height,left,top,right,bottom))return false;
            for(int i=1;i<stroke.points.size();i++){
                Plan.Point next=stroke.points.get(i);
                if(intersects(previous.x*width,previous.y*height,next.x*width,next.y*height,left,top,right,bottom))return false;
                previous=next;
            }
        }
        return true;
    }
    private static boolean inside(double x,double y,double l,double t,double r,double b){return x>=l&&x<=r&&y>=t&&y<=b;}
    private static int code(double x,double y,double l,double t,double r,double b){return (x<l?1:x>r?2:0)|(y<t?4:y>b?8:0);}
    private static boolean intersects(double x1,double y1,double x2,double y2,double l,double t,double r,double b){
        for(int i=0;i<10;i++){
            int a=code(x1,y1,l,t,r,b),c=code(x2,y2,l,t,r,b);
            if((a|c)==0)return true;if((a&c)!=0)return false;
            int out=a!=0?a:c;double x,y;
            if((out&8)!=0){x=x1+(x2-x1)*(b-y1)/(y2-y1);y=b;}
            else if((out&4)!=0){x=x1+(x2-x1)*(t-y1)/(y2-y1);y=t;}
            else if((out&2)!=0){y=y1+(y2-y1)*(r-x1)/(x2-x1);x=r;}
            else{y=y1+(y2-y1)*(l-x1)/(x2-x1);x=l;}
            if(out==a){x1=x;y1=y;}else{x2=x;y2=y;}
        }
        return true; // Conservative rejection if clipping did not converge.
    }
}
