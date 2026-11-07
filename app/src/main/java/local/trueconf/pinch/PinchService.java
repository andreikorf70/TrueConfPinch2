package local.trueconf.pinch;

import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;

public class PinchService extends AccessibilityService {
    public static volatile boolean connected=false;
    private final Handler handler=new Handler();
    private SharedPreferences prefs;
    private long generation=-1;
    private boolean attempted=false, busy=false, fullSeen=false;
    private String stableKey="";
    private int stableCount=0;
    private long lastAttempt=0;
    private final Runnable scan=new Runnable() { public void run() { try { tick(); } catch(Exception e) { report("Не удалось прочитать экран. Вернитесь в сетку TrueConf."); } handler.postDelayed(this,1200); } };
    public static boolean isTrueConf(String s) { return s!=null && (s.startsWith("com.trueconf.") || s.equals("com.trueconf") || s.startsWith("org.trueconf.")); }
    @Override protected void onServiceConnected() { prefs=getSharedPreferences("settings",0); connected=true; handler.removeCallbacks(scan); handler.post(scan); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { busy=false; stableCount=0; }
    @Override public void onDestroy() { connected=false; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void report(String s) { if(prefs!=null && !s.equals(prefs.getString("status",""))) prefs.edit().putString("status",s).apply(); }
    private static String norm(CharSequence s) { return s==null ? "" : s.toString().replace('\u00a0',' ').trim().toLowerCase(Locale.ROOT); }
    private void collect(AccessibilityNodeInfo n,String name,ArrayList<AccessibilityNodeInfo> out,int[] count,int depth) {
        if(n==null || depth>45 || ++count[0]>1800) return;
        if(n.isVisibleToUser() && (norm(n.getText()).equals(name) || norm(n.getContentDescription()).equals(name))) out.add(AccessibilityNodeInfo.obtain(n));
        for(int i=0;i<n.getChildCount();i++) { AccessibilityNodeInfo child=n.getChild(i); if(child!=null) { collect(child,name,out,count,depth+1); child.recycle(); } }
    }
    // Only a real ancestor rectangle is used. No coordinates are inferred from the sample image.
    private Rect tile(AccessibilityNodeInfo match, Rect window) {
        AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain(match);
        float density=getResources().getDisplayMetrics().density;
        try {
            for(int i=0;i<14 && n!=null;i++) {
                Rect b=new Rect(); n.getBoundsInScreen(b);
                boolean inside=window.contains(b);
                if(inside && b.width()>=90*density && b.height()>=90*density && b.height()>b.width()*0.45 && b.width()>b.height()*0.22) {
                    // Exclude the application root and almost-full-width rows from tile selection.
                    if(b.width()<window.width()*0.92 || b.height()>window.height()*0.60) return b;
                }
                AccessibilityNodeInfo p=n.getParent(); n.recycle(); n=p;
            }
            return null;
        } finally { if(n!=null) n.recycle(); }
    }
    private void tick() {
        if(prefs==null || !prefs.getBoolean("enabled",false) || busy) return;
        long g=prefs.getLong("generation",0);
        if(g!=generation) { generation=g; attempted=false; fullSeen=false; stableCount=0; stableKey=""; }
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) { stableCount=0; return; }
        ArrayList<AccessibilityNodeInfo> matches=new ArrayList<>();
        try {
            if(!isTrueConf(root.getPackageName()==null?null:root.getPackageName().toString())) { stableCount=0; return; }
            String name=norm(prefs.getString("name","Луна")); if(name.isEmpty()) return;
            collect(root,name,matches,new int[]{0},0);
            if(matches.isEmpty()) { stableCount=0; report("Имя «"+prefs.getString("name","Луна")+"» не найдено. Плитка должна быть видна; возможно, TrueConf не передаёт её имя."); return; }
            Rect window=new Rect(); root.getBoundsInScreen(window);
            ArrayList<Rect> tiles=new ArrayList<>();
            for(AccessibilityNodeInfo m:matches) { Rect b=tile(m,window); if(b!=null && !tiles.contains(b)) tiles.add(b); }
            if(tiles.size()!=1) { stableCount=0; report(tiles.isEmpty()?"Имя найдено, но границы плитки недоступны. Жест не выполнен.":"Найдено несколько плиток с таким именем. Укажите уникальное имя."); return; }
            Rect box=tiles.get(0);
            double fraction=(double)box.width()*box.height()/((double)window.width()*window.height());
            if(fraction>0.55) {
                // Large bounds may be the root rather than a tile; never gesture there.
                if(attempted && fraction<0.94) fullSeen=true;
                stableCount=0; report("Имя найдено в большой области: трансляция уже увеличена либо точные границы плитки недоступны."); return;
            }
            if(attempted && fullSeen && SystemClock.uptimeMillis()-lastAttempt>8000) { attempted=false; fullSeen=false; }
            if(attempted) return;
            String key=box.flattenToString()+name;
            if(!key.equals(stableKey)) { stableKey=key; stableCount=1; report("Имя найдено. Проверяю положение плитки…"); return; }
            if(++stableCount<3) return;
            // Recheck persisted toggle immediately before issuing the gesture.
            if(!prefs.getBoolean("enabled",false) || prefs.getLong("generation",0)!=generation) return;
            perform(box);
        } finally { for(AccessibilityNodeInfo m:matches) m.recycle(); root.recycle(); }
    }
    private void perform(Rect b) {
        float cx=b.exactCenterX(), cy=b.exactCenterY();
        float reachX=b.width()*0.36f, reachY=b.height()*0.25f;
        Path left=new Path(); left.moveTo(cx-b.width()*0.08f,cy-b.height()*0.05f); left.lineTo(cx-reachX,cy-reachY);
        Path right=new Path(); right.moveTo(cx+b.width()*0.08f,cy+b.height()*0.05f); right.lineTo(cx+reachX,cy+reachY);
        GestureDescription gesture=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(left,0,550))
            .addStroke(new GestureDescription.StrokeDescription(right,0,550)).build();
        attempted=true; busy=true; lastAttempt=SystemClock.uptimeMillis();
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { busy=false; report("Разведение двух пальцев выполнено. Проверьте увеличение в TrueConf."); }
            @Override public void onCancelled(GestureDescription g) { busy=false; report("Жест прерван. Нажмите «Повторить увеличение» для новой попытки."); }
        },handler);
        if(!accepted) { busy=false; report("Android не принял жест. Проверьте специальные возможности."); }
    }
}
