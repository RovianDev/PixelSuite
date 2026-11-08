package io.github.pixelsuite;
import android.graphics.*;
import android.text.*;
import android.text.style.*;
final class SettingsText {
 private SettingsText(){}
 static CharSequence format(String raw){
  raw=I18n.t(raw);
  SpannableStringBuilder out=new SpannableStringBuilder();int at=0;
  while(at<raw.length()){
   int open=raw.indexOf("**",at);if(open<0){out.append(raw.substring(at));break;}
   int close=raw.indexOf("**",open+2);if(close<0){out.append(raw.substring(at));break;}
   out.append(raw.substring(at,open));int start=out.length();out.append(raw.substring(open+2,close));out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);at=close+2;
  }
  for(int i=0;i<out.length();i++)if(out.charAt(i)=='→'||out.charAt(i)=='>')out.setSpan(new Arrow(),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  return out;
 }
 private static final class Arrow extends ReplacementSpan {
  public int getSize(Paint p,CharSequence text,int start,int end,Paint.FontMetricsInt fm){return Math.round(p.getTextSize()*.85f);}
  public void draw(Canvas c,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint p){
   Paint line=new Paint(p);line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(p.getTextSize()*.075f);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);
   Paint.FontMetrics f=p.getFontMetrics();float cy=y+(f.ascent+f.descent)*.5f,size=p.getTextSize(),left=x+size*.15f,right=x+size*.7f;
   c.drawLine(left,cy,right,cy,line);c.drawLine(right-size*.19f,cy-size*.17f,right,cy,line);c.drawLine(right-size*.19f,cy+size*.17f,right,cy,line);
  }
 }
}
