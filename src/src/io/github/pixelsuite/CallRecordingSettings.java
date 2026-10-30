package io.github.pixelsuite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class CallRecordingSettings {
    static final int PICK_FOLDER = 408;
    static final String DEFAULT = "Recordings/Calls";
    interface Presenter { void show(AlertDialog dialog); }
    private final Activity activity;
    private final ExecutorService io;
    private final View row;
    private final TextView summary;
    private final Presenter presenter;
    private static String cachedFolder,cachedLabel,cachedMode;
    boolean cached(){return folderLoaded&&modeLoaded;}
    private String folder = DEFAULT;
    private View modeRow;
    private TextView modeSummary;
    private String mode = "auto";
    private boolean masterEnabled,folderLoaded,modeLoaded;
    private Runnable initialLoad;
    void onInitialLoad(Runnable done){initialLoad=done;}
    private void ready(){if(initialLoad!=null){Runnable done=initialLoad;initialLoad=null;done.run();}}

    CallRecordingSettings(Activity activity, ExecutorService io, View row, TextView summary, Presenter presenter) {
        this.activity=activity;this.io=io;this.row=row;this.summary=summary;this.presenter=presenter;
        if(cachedFolder!=null){folder=cachedFolder;summary.setText(cachedLabel);folderLoaded=true;}
        if(cachedMode!=null){mode=cachedMode;modeLoaded=true;}
    }
    void modeRow(View view,TextView label){modeRow=view;modeSummary=label;if(modeLoaded)showMode();}
    void enabled(boolean enabled) {
        masterEnabled=enabled;updateEnabled();
    }
    private void updateEnabled(){
        if(!PatronAccess.unlocked()){row.setEnabled(true);row.setClickable(true);row.setAlpha(.4f);if(modeRow!=null){modeRow.setEnabled(true);modeRow.setClickable(true);modeRow.setAlpha(.4f);}return;}
        boolean folderEnabled=masterEnabled&&folderLoaded;
        row.setEnabled(folderEnabled);row.setClickable(folderEnabled);row.setAlpha(folderEnabled?1f:.4f);
        if(modeRow!=null){boolean modeEnabled=masterEnabled&&modeLoaded;modeRow.setEnabled(modeEnabled);modeRow.setClickable(modeEnabled);modeRow.setAlpha(modeEnabled?1f:.4f);}
    }
    void reload() {
        io.execute(new Runnable(){public void run(){
            List<String> snapshot=Root.run("settings get secure "+CallRecording.FOLDER_KEY+"; settings get secure "+CallRecording.MODE_KEY);
            final boolean modeOk=snapshot!=null&&snapshot.size()>=2;
            final String value=modeOk?(snapshot.get(0).startsWith("tree:")?snapshot.get(0):DEFAULT):null;
            final String selected=modeOk&&"manual".equals(snapshot.get(1))?"manual":"auto";
            final String folderLabel=value==null?null:label(value);
            activity.runOnUiThread(new Runnable(){public void run(){if(!activity.isFinishing()&&!activity.isDestroyed()){
                if(value!=null){folder=value;cachedFolder=value;cachedLabel=folderLabel;summary.setText(folderLabel);folderLoaded=true;}else if(!folderLoaded)summary.setText("Couldn't load folder");
                if(modeOk){mode=selected;cachedMode=selected;showMode();modeLoaded=true;}else if(!modeLoaded&&modeSummary!=null)modeSummary.setText("Couldn't load recording mode");
                updateEnabled();ready();
            }}});
        }});
    }
    private void showMode(){if(modeSummary!=null)modeSummary.setText("manual".equals(mode)?"Manual: tap Record during a call":"Automatic: every call is recorded");}
    void chooseMode(){
        presenter.show(new AlertDialog.Builder(activity).setTitle("Recording mode")
            .setSingleChoiceItems(new String[]{"Automatic (default)","Manual"},"manual".equals(mode)?1:0,new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dialog,int which){
                    dialog.dismiss();final String selected=which==1?"manual":"auto";
                    io.execute(new Runnable(){public void run(){
                        final boolean ok=Root.run("settings put secure "+CallRecording.MODE_KEY+" "+selected)!=null;
                        activity.runOnUiThread(new Runnable(){public void run(){
                            if(activity.isFinishing()||activity.isDestroyed())return;
                            if(ok){mode=selected;cachedMode=selected;showMode();}else message("Couldn't save: root access needed");
                        }});
                    }});
                }
            }).setNegativeButton("Cancel",null).create());
    }
    void choose() {
        presenter.show(new AlertDialog.Builder(activity).setTitle("Recordings folder")
            .setItems(new String[]{"Recordings/Calls (default)","Choose folder…"},new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface dialog,int which){
                    if(which==0)save(DEFAULT,null,false);
                    else try {
                        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        activity.startActivityForResult(pick,PICK_FOLDER);
                    }catch(Exception unavailable){message("Folder picker isn't available.");}
                }
            }).create());
    }
    void result(int result,Intent data) {
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();boolean held=hasPermission(uri);
        try {
            activity.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            save("tree:"+uri.toString(),uri,!held);
        }catch(Exception denied){message("Couldn't keep access to that folder.");}
    }
    private String read(){
        List<String> lines=Root.run("settings get secure "+CallRecording.FOLDER_KEY);
        if(lines==null||lines.isEmpty())return null;
        String value=lines.get(0);return value.startsWith("tree:")?value:DEFAULT;
    }
    private void save(final String value,final Uri picked,final boolean newGrant) {
        io.execute(new Runnable(){public void run(){
            String previous=read();
            final boolean ok=Root.run("settings put secure "+CallRecording.FOLDER_KEY+" '"+value.replace("'","'\"'\"'")+"'")!=null;
            if(ok&&previous!=null&&previous.startsWith("tree:")&&!previous.equals(value))release(Uri.parse(previous.substring(5)));
            if(!ok&&newGrant&&picked!=null)release(picked);
            final String folderLabel=ok?label(value):null;
            activity.runOnUiThread(new Runnable(){public void run(){
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(ok){folder=value;cachedFolder=value;cachedLabel=folderLabel;folderLoaded=true;summary.setText(folderLabel);updateEnabled();}else message("Couldn't save: root access needed");
            }});
        }});
    }
    private void release(Uri uri){try{activity.getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}}
    private boolean hasPermission(Uri uri){
        for(UriPermission p:activity.getContentResolver().getPersistedUriPermissions())if(uri.equals(p.getUri())&&p.isReadPermission()&&p.isWritePermission())return true;
        return false;
    }
    private String label(String value){
        if(!value.startsWith("tree:"))return DEFAULT;
        try{
            Uri tree=Uri.parse(value.substring(5));
            if(!hasPermission(tree))return DEFAULT+" (folder not available)";
            Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            android.database.Cursor cursor=activity.getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null);
            if(cursor==null)return DEFAULT+" (folder not available)";
            try{if(!cursor.moveToFirst())return DEFAULT+" (folder not available)";}finally{cursor.close();}
            String id=DocumentsContract.getTreeDocumentId(tree);int colon=id.indexOf(':');
            String volume=colon<0?id:id.substring(0,colon),path=colon<0?"":id.substring(colon+1);
            return ("primary".equals(volume)?"Internal storage":"SD card")+(path.isEmpty()?"":"/"+path);
        }catch(Exception unavailable){return DEFAULT+" (folder not available)";}
    }
    private void message(String value){AppToast.makeText(activity,value,Toast.LENGTH_SHORT).show();}
}
