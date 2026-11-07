package local.trueconf.pinch;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private EditText name;
    private TextView status;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        public void run() {
            status.setText((PinchService.connected ? "Служба подключена" : "Служба не включена") + "\n\n" + prefs.getString("status", "Укажите имя и включите службу.") + "\n\n" + (prefs.getBoolean("enabled", false) ? "Автопоиск включён" : "Автопоиск выключен"));
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(int x) { return (int)(getResources().getDisplayMetrics().density*x); }
    private TextView text(String s, int size) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.rgb(27,39,53)); t.setPadding(0,dp(10),0,dp(10)); return t;
    }
    private void button(LinearLayout box, String title, View.OnClickListener action) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setOnClickListener(action); box.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); prefs=getSharedPreferences("settings",0);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(dp(22),dp(26),dp(22),dp(24)); box.setBackgroundColor(Color.rgb(242,246,250));
        scroll.addView(box); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets; });
        box.addView(text("TrueConf Щипок",28));
        box.addView(text("Автоматическое увеличение по имени",17));
        box.addView(text("Имя участника — точно как в TrueConf",14));
        name=new EditText(this); name.setSingleLine(true); name.setText(prefs.getString("name","Луна")); name.setTextSize(21); box.addView(name);
        button(box,"Сохранить имя и включить поиск",v-> {
            String target=name.getText().toString().trim();
            if(target.length()==0) { name.setError("Введите имя"); return; }
            prefs.edit().putString("name",target).putBoolean("enabled",true).putLong("generation",System.currentTimeMillis()).putString("status","Ожидаю TrueConf: «"+target+"»").apply();
            Toast.makeText(this,"Имя сохранено. Откройте конференцию TrueConf.",Toast.LENGTH_LONG).show();
        });
        button(box,"1. Включить специальные возможности",v->new AlertDialog.Builder(this).setTitle("Доступ для автоматического жеста")
            .setMessage("Разрешите службе «TrueConf Щипок» читать элементы экрана и выполнять жесты. Приложение обрабатывает только TrueConf; доступ к интернету отсутствует. В настройках откройте загруженные приложения / установленные службы и включите «TrueConf Щипок».")
            .setPositiveButton("Открыть настройки",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Отмена",null).show());
        button(box,"2. Открыть TrueConf",v->openTrueConf());
        button(box,"Повторить увеличение",v->{prefs.edit().putLong("generation",System.currentTimeMillis()).putBoolean("enabled",true).putString("status","Повторный поиск включён. Откройте сетку TrueConf.").apply();});
        button(box,"Остановить",v->{prefs.edit().putBoolean("enabled",false).putString("status","Остановлено пользователем.").apply();});
        status=text("",16); box.addView(status);
        box.addView(text("Тестовая версия • Android 7 и новее\n\nИщет точное имя без учёта регистра только на видимом экране. Если имена повторяются, жест не выполняется. После одного жеста приложение ждёт: это защищает от бесконечного увеличения. Для повторной попытки нажмите «Повторить увеличение».\n\nЕсли TrueConf не передаёт имена или границы плиток, автоматический поиск не сработает. Статус поможет определить причину. Работа внутри TrueConf ещё требует проверки на вашем телефоне.",14));
    }
    private void openTrueConf() {
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(query,0);
        ArrayList<ResolveInfo> found=new ArrayList<>();
        for(ResolveInfo r:apps) if(PinchService.isTrueConf(r.activityInfo.packageName)) found.add(r);
        if(found.isEmpty()) { Toast.makeText(this,"Откройте установленный TrueConf вручную",Toast.LENGTH_LONG).show(); return; }
        if(found.size()==1) { launch(found.get(0)); return; }
        String[] labels=new String[found.size()]; for(int i=0;i<labels.length;i++) labels[i]=found.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Выберите TrueConf").setItems(labels,(d,w)->launch(found.get(w))).show();
    }
    private void launch(ResolveInfo r) { startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name))); }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
