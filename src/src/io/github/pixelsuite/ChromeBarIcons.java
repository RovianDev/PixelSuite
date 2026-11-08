package io.github.pixelsuite;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

final class ChromeBarIcons {
    private ChromeBarIcons() {}
    static void extensions(Canvas c,Path path,Paint p,float cx,float cy,float s){
        float l=cx-s*.85f,tp=cy-s*.55f,r=cx+s*.55f,b=cy+s*.85f,k=s*.3f;
        path.reset();path.moveTo(l,tp);path.lineTo(cx-k*.9f,tp);
        path.cubicTo(cx-k*.9f,tp-k*1.6f,cx+k*.9f-s*.3f,tp-k*1.6f,cx+k*.9f-s*.3f,tp);
        path.lineTo(r,tp);path.lineTo(r,cy-k*.9f+s*.15f);
        path.cubicTo(r+k*1.6f,cy-k*.9f+s*.15f,r+k*1.6f,cy+k*.9f+s*.15f,r,cy+k*.9f+s*.15f);
        path.lineTo(r,b);path.lineTo(l,b);path.close();c.drawPath(path,p);
    }
    static void shield(Canvas c,Path path,Paint p,float cx,float cy,float size){
        float w=size*.78f,h=size*.9f,l=cx-w/2,t=cy-h/2;
        path.reset();path.moveTo(cx,t);path.lineTo(l+w,t+h*.16f);path.lineTo(l+w,t+h*.46f);
        path.cubicTo(l+w,t+h*.74f,cx+w*.22f,t+h*.9f,cx,t+h);
        path.cubicTo(cx-w*.22f,t+h*.9f,l,t+h*.74f,l,t+h*.46f);path.lineTo(l,t+h*.16f);path.close();c.drawPath(path,p);
    }
}
