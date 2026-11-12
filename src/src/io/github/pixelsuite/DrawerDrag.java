package io.github.pixelsuite;

import android.app.Activity;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

final class DrawerDrag {
    private static Session session;
    private static Object get(Object o,String n){return LauncherFeatures.get(o,n);}
    private static void set(Object o,String n,Object v){LauncherFeatures.set(o,n,v);}
    private static Object call(Object o,String n,Object...v){return LauncherFeatures.call(o,n,v);}
    private static Class<?> type(String n){return LauncherFeatures.type(n);}
    private static Object constant(String name)throws Exception{return type("LauncherState").getField(name).get(null);}
    static void install(){
        Xp.hookAllMethods(type("statemanager.StateManager"),"goToState",new Xp.Callback(){
            protected void beforeHookedMethod(Xp.Param p)throws Throwable{
                if(session==null||session.exited||p.args.length==0)return;
                if(p.args[0]==constant("NORMAL")||p.args[0]==constant("SPRING_LOADED"))p.setResult(null);
            }
        });
        Xp.Callback keepDrawer=new Xp.Callback(){protected void beforeHookedMethod(Xp.Param p){if(session!=null&&!session.exited)p.setResult(null);}};
        Xp.hookAllMethods(type("Workspace"),"onDragStart",keepDrawer);
        Xp.hookAllMethods(type("Workspace"),"onDragEnd",keepDrawer);
        Xp.hookAllMethods(type("DropTargetBar"),"onDragStart",keepDrawer);
        Xp.hookAllMethods(type("dragndrop.DragController"),"handleMoveEvent",new Xp.Callback(){
            protected void beforeHookedMethod(Xp.Param p)throws Throwable{
                Session s=session;if(s==null||s.exited)return;
                int x=((Number)p.args[0]).intValue(),y=((Number)p.args[1]).intValue();
                s.lastX=x;s.lastY=y;
                if(s.stay!=null)s.stay.update(x,y);
                boolean remove=DrawerHomeTarget.updateRemove(x,y,s.layer);
                if((!remove&&DrawerHomeTarget.update(x,y,s.layer))||(!remove&&s.place==null&&y<s.exitY)){s.exitToHome();}
            }
        });
        try{
            final ThreadLocal<Object[]> hidden=new ThreadLocal<Object[]>();
            Xp.hookAllMethods(type("DeleteDropTarget"),"onDragStart",new Xp.Callback(){
                protected void beforeHookedMethod(Xp.Param p){
                    try{Object item=get(p.args[0],"dragInfo");if(item!=null&&DrawerFolders.isFolder(item)){hidden.set(new Object[]{item,get(item,"id")});set(item,"id",-1);}}catch(Throwable ignored){}
                }
                protected void afterHookedMethod(Xp.Param p){
                    Object[] h=hidden.get();hidden.remove();if(h!=null)try{set(h[0],"id",h[1]);}catch(Throwable ignored){}
                }
            });
        }catch(Throwable error){}
        Xp.hookAllMethods(type("DropTargetBar"),"onDragStart",new Xp.Callback(){
            protected void afterHookedMethod(Xp.Param p){
                Session s=session;if(s!=null&&!s.exited)return;
            }
        });
        Xp.hookAllMethods(type("dragndrop.DragController"),"callOnDragEnd",new Xp.Callback(){
            protected void afterHookedMethod(Xp.Param p){if(session!=null)finish();}
        });
    }
    static boolean active(){return session!=null;}
    static boolean fromFolder(){Session s=session;return s!=null&&s.stay!=null;}
    static boolean inHeldFolder(View v){
        Session s=session;if(s==null||s.stay==null)return false;
        for(android.view.ViewParent p=v.getParent();p!=null;p=p.getParent())if(p==s.stay.folder)return true;
        return false;
    }
    static boolean placing(){Session s=session;return s!=null&&!s.exited&&s.place!=null;}
    static void listScrolled(){Session s=session;if(s!=null&&!s.exited&&s.place!=null)s.place.externalScroll();}
    static void refreshHover(){Session s=session;if(s==null||s.exited)return;try{call(s.controller,"handleMoveEvent",s.lastX,s.lastY);}catch(Throwable ignored){}}
    static void pause(){if(session!=null&&!session.exited)cancel();}
    static boolean start(final View source){
        Activity a=LauncherFeatures.activity.get();if(a==null||session!=null)return false;
        Object info=source.getTag();String key=DrawerFolders.isFolder(info)?"folder:"+DrawerFolders.folderIds.get(info):LauncherFeatures.identity(info);
        if(key==null)return false;
        try{
            Session s=new Session(a,source,info,key);session=s;DrawerHomeTarget.arm(source);
            DrawerFolders.closePopup();
            Object options=Xp.newInstance(type("dragndrop.DragOptions"));
            try{set(options,"isMouseDrag",true);}catch(Throwable olderLauncher){}
            Object nativeInfo=info;
            if(!DrawerFolders.isFolder(info)&&DrawerFolders.isDrawerMember(info)){
                nativeInfo=DrawerFolders.known.get(key);if(nativeInfo==null)throw new IllegalStateException("App is no longer available");
            }
            source.setTag(nativeInfo);
            call(s.controller,"addDropTarget",s.target);
            try{call(get(a,"mWorkspace"),"beginDragShared",source,s.dragSource,options);}
            finally{source.setTag(info);set(get(a,"mWorkspace"),"mDragSourceInternal",null);}
            source.setVisibility(View.INVISIBLE);
            return true;
        }catch(Throwable error){finish();return false;}
    }
    static void cancel(){
        Session s=session;if(s==null)return;
        try{call(s.controller,"cancelDrag");}catch(Throwable error){}
        if(session!=null)finish();
    }
    static void move(float rawX,float rawY){
        Session s=session;if(s==null)return;int[] base=new int[2];s.layer.getLocationOnScreen(base);
        call(s.controller,"handleMoveEvent",(int)rawX-base[0],(int)rawY-base[1]);
    }
    private static void finish(){
        Session s=session;session=null;DrawerHomeTarget.hide();if(s==null)return;
        if(s.reorder!=null)s.reorder.restore();if(s.push!=null)s.push.restore();if(s.place!=null)s.place.restore();if(!s.landedOut)s.source.setVisibility(View.VISIBLE);s.clearHover();s.setMember(null);if(s.stay!=null)s.stay.stop();
        try{call(s.controller,"removeDropTarget",s.target);}catch(Throwable ignored){}
        if(!s.exited&&LauncherData.saves!=s.savesAtStart&&LauncherFeatures.refreshes==s.refreshesAtStart)LauncherFeatures.refresh();
    }
    private static final class Session implements InvocationHandler {
        final Activity activity;final View source;final Object info;final String key;
        final ViewGroup layer;final Object controller;final Object target;final Object dragSource;
        final int exitY;boolean exited,indicating,lifted,landedOut;View hovered;int localX,localY;
        final int savesAtStart=LauncherData.saves,refreshesAtStart=LauncherFeatures.refreshes;
        FolderDrag.Stay stay;FolderReorder reorder;FolderPush push;DrawerPlace place;View hoverMember;int lastX,lastY;
        Session(Activity a,View v,Object i,String k){
            activity=a;source=v;info=i;key=k;layer=(ViewGroup)call(a,"getDragLayer");controller=get(a,"mDragController");
            Object appsView=call(a,"getAppsView");View search=(View)get(appsView,"mSearchContainer");
            int[] top=new int[2],origin=new int[2];search.getLocationOnScreen(top);layer.getLocationOnScreen(origin);
            exitY=top[1]-origin[1];
            if(exitY<=0)throw new IllegalStateException("Search bar boundary is not laid out");
            if(!k.startsWith("folder:")&&DrawerFolders.isDrawerMember(i)){View open=FolderDrag.openFolder(a);if(open!=null){stay=new FolderDrag.Stay(open,layer);reorder=FolderReorder.start(open,v);}}
            if(k.startsWith("folder:")){
                try{DrawerPlace candidate=new DrawerPlace(v,layer);if(candidate.usable())place=candidate;}catch(Throwable unavailable){}
                if(place==null)push=new FolderPush(v,layer);
            }
            target=Proxy.newProxyInstance(LauncherFeatures.loader,new Class<?>[]{type("DropTarget")},this);
            dragSource=Proxy.newProxyInstance(LauncherFeatures.loader,new Class<?>[]{type("DragSource")},new InvocationHandler(){
                public Object invoke(Object self,Method method,Object[] args){
                    if(method.getName().equals("onDropCompleted")){
                        if(!landedOut)source.setVisibility(View.VISIBLE);
                        if(!exited)set(args[1],"deferDragViewCleanupPostAnimation",false);
                        return null;
                    }
                    return objectMethod(self,method,args);
                }
            });
        }
        public Object invoke(Object self,Method method,Object[] args)throws Throwable{
            String name=method.getName();
            if(name.equals("isDropEnabled"))return !exited;
            if(name.equals("getDropView"))return layer;
            if(name.equals("getHitRectRelativeToDragLayer")){((Rect)args[0]).set(0,place!=null?0:exitY,layer.getWidth(),layer.getHeight());return null;}
            if(name.equals("onDragOver")||name.equals("onDragEnter")){
                int x=((Number)get(args[0],"x")).intValue(),y=((Number)get(args[0],"y")).intValue();
                if(DrawerHomeTarget.overRemove){clearHover();setMember(null);return null;}
                if(stay!=null&&!stay.left){
                    clearHover();
                    if(reorder!=null){setMember(null);reorder.over(args[0],x,y);return null;}
                    View m=stay.memberAt(x,y);setMember(m==source?null:m);return null;
                }
                if(stay!=null&&reorder!=null)reorder.leave();
                if(place!=null){clearHover();setMember(null);place.over(x,y);return null;}
                if(push!=null){clearHover();setMember(null);push.over(x,y);return null;}
                setMember(null);
                View candidate=findCell((View)call(activity,"getAppsView"),x,y,layer);
                if(candidate==source)candidate=null;
                if(candidate!=hovered){clearHover();hovered=candidate;}
                if(hovered!=null){int[] location=new int[2],base=new int[2];hovered.getLocationOnScreen(location);layer.getLocationOnScreen(base);localX=x-(location[0]-base[0]);localY=y-(location[1]-base[1]);}
                boolean folderDrop=hovered!=null&&LauncherFeatures.on(LauncherFeatures.DRAWER)&&intoFolder(hovered);
                if(folderDrop!=indicating){indicating=folderDrop;try{if(folderDrop)DropIndicator.show(hovered);else DropIndicator.hide(hovered);}catch(Throwable error){}}
                boolean lift=hovered!=null&&key.startsWith("folder:")&&DrawerFolders.isFolder(hovered.getTag());
                if(lift!=lifted&&hovered!=null){lifted=lift;hovered.animate().scaleX(lift?1.06f:1f).scaleY(lift?1.06f:1f).setDuration(150).start();}
                return null;
            }
            if(name.equals("onDragExit")){if(!Boolean.TRUE.equals(get(args[0],"dragComplete")))clearHover();return null;}
            if(name.equals("acceptDrop")&&DrawerHomeTarget.overRemove&&stay!=null)return true;
            if(name.equals("acceptDrop")&&stay!=null&&!stay.left)return true;
            if(name.equals("acceptDrop")&&place!=null){boolean move=place.wouldMove();if(!move){place.restore();DropThud.later(key,120);}return move;}
            if(name.equals("acceptDrop")&&push!=null)return push.active();
            if(name.equals("acceptDrop")){boolean on=LauncherFeatures.on(LauncherFeatures.DRAWER);boolean ok=on&&hovered!=null&&hovered!=source;if(on&&!ok)DropThud.later(key,120);return ok;}
            if(name.equals("prepareAccessibilityDrop"))return null;
            if(name.equals("onDrop")&&DrawerHomeTarget.overRemove&&stay!=null){
                clearHover();setMember(null);set(args[0],"deferDragViewCleanupPostAnimation",false);takeOut();return null;
            }
            if(name.equals("onDrop")&&stay!=null&&!stay.left){
                View m=hoverMember;setMember(null);set(args[0],"deferDragViewCleanupPostAnimation",false);
                if(reorder!=null){reorder.commit(args[0]);MultiDrag.handledInFolder();return null;}
                dropInside(m);MultiDrag.drawerDrop();return null;
            }
            if(name.equals("onDrop")&&place!=null){clearHover();set(args[0],"deferDragViewCleanupPostAnimation",false);if(place.commit(args[0]))MultiDrag.handledInFolder();else DropThud.later(key,120);return null;}
            if(name.equals("onDrop")&&push!=null){clearHover();set(args[0],"deferDragViewCleanupPostAnimation",false);push.commit();return null;}
            if(name.equals("onDrop")){
                if(reorder!=null)reorder.restore();
                View destination=hovered;clearHover();set(args[0],"deferDragViewCleanupPostAnimation",false);
                if(destination==null||destination==source){DropThud.later(key,120);return null;}
                DrawerFolders.DragSource origin=new DrawerFolders.DragSource(key,info,source);
                Object targetInfo=destination.getTag();
                int created=DrawerFolders.animateId;String landing=null;final int savesBefore=LauncherData.saves;
                if(!DrawerFolders.isFolder(targetInfo)&&!key.startsWith("folder:")&&!inCenter(destination)){if(DrawerFolders.reorder(origin,targetInfo))landing=key;}
                else if(DrawerFolders.drop(origin,targetInfo)){
                    if(DrawerFolders.isFolder(targetInfo))landing="folder:"+DrawerFolders.folderIds.get(targetInfo);
                    else if(DrawerFolders.animateId!=0&&DrawerFolders.animateId!=created)landing="folder:"+DrawerFolders.animateId;
                }
                MultiDrag.drawerDrop();
                if(stay!=null&&(landing!=null||LauncherData.saves!=savesBefore)){
                    landedOut=true;source.setVisibility(View.INVISIBLE);
                    final View gone=source;gone.postDelayed(new Runnable(){public void run(){gone.setVisibility(View.VISIBLE);}},1500);
                    try{DrawerFolders.closeDrawerFolder(true);}catch(Throwable ignored){}
                }
                DropThud.later(landing,120);
                return null;
            }
            return objectMethod(self,method,args);
        }
        boolean intoFolder(View target){
            if(key.startsWith("folder:"))return false;
            return DrawerFolders.isFolder(target.getTag())||inCenter(target);
        }
        boolean inCenter(View target){
            float cx=target.getWidth()/2f,cy=target.getPaddingTop()+LauncherFeatures.dp(activity,28),radius=LauncherFeatures.dp(activity,25);
            return Math.abs(localX-cx)<=radius&&Math.abs(localY-cy)<=radius;
        }
        void setMember(View m){
            if(m==hoverMember)return;
            if(hoverMember!=null)hoverMember.animate().scaleX(1).scaleY(1).setDuration(120).start();
            hoverMember=m;
            if(m!=null)m.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
        }
        void dropInside(View target){
            try{
                Object folderInfo=get(stay.folder,"mInfo");Integer id=DrawerFolders.folderIds.get(folderInfo);
                LauncherData.Folder f=DrawerFolders.record(folderInfo);
                if(id==null||f==null||target==null||target==source||f.sort==FolderSort.AUTO)return;
                String targetKey=LauncherFeatures.identity(target.getTag());if(targetKey==null)return;
                int to=f.apps.indexOf(targetKey);if(to<0)return;
                if(FolderSort.drawerMove(id,key,to)){DrawerFolders.reopenId=id;try{call(stay.folder,"close",false);}catch(Throwable ignored){}}
            }catch(Throwable error){}
        }
        void takeOut(){
            try{
                if(reorder!=null)reorder.restore();
                DrawerFolders.closeDrawerFolder(false);
                MultiDrag.takeOut(key);
                DropThud.later(key,260);
            }catch(Throwable error){}
        }
        void clearHover(){
            if(hovered!=null){
                if(indicating)try{DropIndicator.hide(hovered);}catch(Throwable ignored){}
                if(lifted)hovered.animate().scaleX(1).scaleY(1).setDuration(150).start();
                hovered=null;
            }
            indicating=false;lifted=false;
        }
        void exitToHome()throws Exception{
            exited=true;DrawerHomeTarget.hide();clearHover();call(controller,"removeDropTarget",target);
            if(reorder!=null)reorder.restore();
            if(push!=null)push.restore();
            if(place!=null)place.restore();
            if(stay!=null){
                stay.stop();final View open=stay.folder;
                open.animate().cancel();
                open.animate().alpha(0f).setDuration(90).withEndAction(new Runnable(){public void run(){
                    try{DrawerFolders.closeDrawerFolder(false);}catch(Throwable ignored){}
                    open.setAlpha(1f);
                }}).start();
            }
            Object drag=get(controller,"mDragObject");
            if(DrawerFolders.isFolder(info)){
                LauncherData.Folder f=DrawerFolders.record(info);Object copy=Xp.newInstance(type("model.data.FolderInfo"));
                set(copy,"title",get(info,"title"));set(copy,"user",get(info,"user"));
                try{set(copy,"id",-1);}catch(Throwable ignored){}
                List<Object> content=(List<Object>)get(copy,"contents");
                if(f!=null)for(String appKey:f.apps){Object app=DrawerFolders.known.get(appKey);if(app!=null)content.add(LauncherFeatures.workspaceItem(app,activity));}
                set(drag,"dragInfo",copy);set(drag,"originalDragInfo",call(copy,"makeShallowCopy"));
                PrivacyFolders.draggedToHome(activity,info,copy);
            }
            Object workspace=get(activity,"mWorkspace");set(workspace,"mDragSourceInternal",null);
            call(workspace,"onDragStart",drag,get(controller,"mOptions"));
            call(get(activity,"mDropTargetBar"),"onDragStart",drag,get(controller,"mOptions"));
        }
    }
    static Object objectMethod(Object self,Method m,Object[] args){
        if(m.getName().equals("hashCode"))return System.identityHashCode(self);
        if(m.getName().equals("equals"))return self==args[0];
        if(m.getName().equals("toString"))return "PixelSuiteDrawerDrag";return null;
    }
    private static View findCell(View root,int x,int y,ViewGroup layer){
        if(root.getVisibility()!=View.VISIBLE||Xp.getAdditionalInstanceField(root,"pixelsuite_picked")!=null)return null;
        int[] base=new int[2];layer.getLocationOnScreen(base);Rect bounds=new Rect();if(!root.getGlobalVisibleRect(bounds)||!bounds.contains(x+base[0],y+base[1]))return null;
        Object tag=root.getTag();if(tag!=null&&(DrawerFolders.isFolder(tag)||type("model.data.AppInfo").isInstance(tag))&&!LauncherFeatures.privateItem(tag))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=group.getChildCount()-1;i>=0;i--){View result=findCell(group.getChildAt(i),x,y,layer);if(result!=null)return result;}}
        return null;
    }
}
