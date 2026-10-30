package io.github.pixelsuite;
final class SurfaceDemoLayout {
    final float barLeft,barTop,barRight,barBottom,trackLeft,trackRight,trackY;
    SurfaceDemoLayout(float width,float height,float density,float captionHeight){
        float margin=16*density,barHeight=12*density+14*density+8*density+captionHeight+12*density;
        barLeft=margin;barRight=width-margin;barBottom=height-margin;barTop=barBottom-barHeight;
        float inset=Math.max(24*density,(barRight-barLeft)*.16f);
        trackLeft=barLeft+inset;trackRight=barRight-inset;trackY=barTop+19*density;
    }
    float position(float value){return trackLeft+(trackRight-trackLeft)*Math.max(0,Math.min(2,value))/2f;}
}
