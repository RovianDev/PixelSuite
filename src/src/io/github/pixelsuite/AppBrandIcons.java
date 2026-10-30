package io.github.pixelsuite;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

final class AppBrandIcons {
    private AppBrandIcons() {}
    private static final android.util.LruCache<String,Bitmap> marks=new android.util.LruCache<String,Bitmap>(16);
    static Drawable load(Context context,String pkg,int color) throws PackageManager.NameNotFoundException {
        String key=pkg+":"+context.getPackageManager().getPackageInfo(pkg,0).lastUpdateTime;
        Bitmap cached=marks.get(key);
        if(cached!=null){Drawable result=new InsetDrawable(new BitmapDrawable(context.getResources(),cached),Math.round(context.getResources().getDisplayMetrics().density));result.setTint(color);return result;}
        Drawable source=context.getPackageManager().getApplicationIcon(pkg);
        Drawable mark=null;
        try{mark=(Drawable)source.getClass().getMethod("getMonochrome").invoke(source);}catch(Exception unavailable){}
        if(mark==null)try{mark=(Drawable)source.getClass().getMethod("getForeground").invoke(source);}catch(Exception flatIcon){}
        if(mark!=null)source=mark;
        source=source.mutate();
        final int size=192;Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        source.setBounds(0,0,size,size);source.draw(new Canvas(bitmap));
        int[] pixels=new int[size*size];bitmap.getPixels(pixels,0,size,0,0,size,size);
        int left=size,top=size,right=-1,bottom=-1;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)if((pixels[y*size+x]>>>24)>24){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
        if(right>=left){
            Bitmap cropped=Bitmap.createBitmap(bitmap,left,top,right-left+1,bottom-top+1);
            marks.put(key,cropped);
            int inset=Math.round(context.getResources().getDisplayMetrics().density);
            source=new InsetDrawable(new BitmapDrawable(context.getResources(),cropped),inset);
        }
        source.setTint(color);return source;
    }
}
