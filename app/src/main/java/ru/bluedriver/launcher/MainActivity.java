package ru.bluedriver.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.res.ColorStateList;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;
import org.vosk.android.RecognitionListener;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQ_MIC = 71;
    private final int bg = Color.rgb(5, 12, 27);
    private final int panel = Color.rgb(13, 25, 47);
    private final int panel2 = Color.rgb(20, 35, 64);
    private final int blue = Color.rgb(31, 103, 196);
    private final int purple = Color.rgb(111, 76, 190);

    private TextView clock, date, assistantStatus, assistantOrb;
    private TextToSpeech tts;
    private Model voiceModel;
    private SpeechService speechService;
    private boolean ttsReady = false;
    private boolean listening = false;
    private boolean assistantOpen = false;
    private LinearLayout root;
    private AlertDialog assistantDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        initTts();
        buildUi();
        updateClock();
        prepareOfflineModel();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.rgb(39, 62, 101));
        return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h), weight);
    }

    private void buildUi() {
        root = column();
        root.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.setBackgroundColor(bg);
        setContentView(root);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("BLUEDRIVER", 22, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        brand.setLetterSpacing(.12f);
        top.addView(brand, lp(0, 48, 1));
        LinearLayout timeBox = column();
        clock = text("--:--", 22, Color.WHITE, true);
        date = text("", 11, 0xff9fb5d7, false);
        timeBox.addView(clock);
        timeBox.addView(date);
        top.addView(timeBox, lp(120, 48, 0));
        root.addView(top, lp(-1, 48, 0));

        LinearLayout content = row();
        content.setPadding(0, dp(10), 0, dp(10));

        // Большое основное окно навигации
        LinearLayout nav = column();
        nav.setPadding(dp(18), dp(14), dp(18), dp(14));
        nav.setBackground(shape(panel, 22));
        TextView navLabel = text("НАВИГАЦИЯ", 13, 0xff9db9e9, true);
        navLabel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nav.addView(navLabel, lp(-1, 30, 0));
        TextView navGraphic = text("➤", 62, Color.WHITE, true);
        nav.addView(navGraphic, lp(-1, 0, 1));
        TextView navHint = text("Маршрут • Карты • Дорога", 13, 0xffc4d4ee, false);
        nav.addView(navHint, lp(-1, 30, 0));
        nav.setOnClickListener(v -> openMaps());
        content.addView(nav, lp(0, -1, 1.32f));

        View gap = new View(this);
        content.addView(gap, lp(12, 1, 0));

        // Музыка компактнее навигации — отдельная небольшая карточка
        LinearLayout right = column();
        LinearLayout music = column();
        music.setGravity(Gravity.CENTER);
        music.setPadding(dp(14), dp(10), dp(14), dp(10));
        music.setBackground(shape(panel2, 20));
        TextView musicLabel = text("♫  МУЗЫКА", 14, 0xffc5b4ff, true);
        TextView album = text("♪", 38, Color.WHITE, true);
        TextView track = text("Музыкальный плеер", 13, Color.WHITE, true);
        TextView trackHint = text("Нажмите, чтобы открыть", 10, 0xff9fb5d7, false);
        music.addView(musicLabel, lp(-1, 25, 0));
        music.addView(album, lp(-1, 0, 1));
        music.addView(track, lp(-1, 24, 0));
        music.addView(trackHint, lp(-1, 20, 0));
        music.setOnClickListener(v -> launchMusic());
        right.addView(music, lp(-1, 0, 1.15f));

        LinearLayout quick = row();
        quick.setPadding(0, dp(10), 0, 0);
        quick.addView(action("РАДИО", "◉", blue, v -> launchRadio()), lp(0, -1, 1));
        View qgap = new View(this);
        quick.addView(qgap, lp(8, 1, 0));
        quick.addView(action("ПРИЛОЖЕНИЯ", "▦", purple, v -> showApps()), lp(0, -1, 1));
        right.addView(quick, lp(-1, 0, .85f));
        content.addView(right, lp(0, -1, 1));
        root.addView(content, lp(-1, 0, 1));

        LinearLayout bottom = row();
        bottom.setGravity(Gravity.CENTER);
        bottom.addView(action("ГОЛОСОВОЙ ПОМОЩНИК", "🎙", purple, v -> openAssistant()), lp(0, 58, 1.4f));
        View bgap = new View(this);
        bottom.addView(bgap, lp(10, 1, 0));
        bottom.addView(action("НАСТРОЙКИ", "⚙", panel2, v -> showSettings()), lp(0, 58, .8f));
        root.addView(bottom, lp(-1, 58, 0));
    }

    private View action(String title, String icon, int color, View.OnClickListener click) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(4), dp(5), dp(4));
        box.setBackground(shape(color, 15));
        TextView i = text(icon, 20, Color.WHITE, true);
        TextView t = text(title, 11, Color.WHITE, true);
        box.addView(i, lp(-1, 25, 0));
        box.addView(t, lp(-1, 22, 0));
        box.setOnClickListener(click);
        return box;
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("ru", "RU"));
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED;
                // Просим женский голос, если движок Android предоставляет такой вариант.
                try {
                    for (android.speech.tts.Voice voice : tts.getVoices()) {
                        if (voice.getLocale().getLanguage().equals("ru") &&
                            (voice.getName().toLowerCase(Locale.ROOT).contains("female") ||
                             voice.getName().toLowerCase(Locale.ROOT).contains("woman"))) {
                            tts.setVoice(voice);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void prepareOfflineModel() {
        // Модель Vosk копируется из app/src/main/assets/model при сборке.
        try {
            StorageService.unpack(this, "model", "model",
                model -> {
                    voiceModel = model;
                    if (assistantStatus != null) assistantStatus.setText("Готова слушать");
                },
                exception -> {
                    voiceModel = null;
                    if (assistantStatus != null)
                        assistantStatus.setText("Добавьте русскую модель Vosk (инструкция в README)");
                });
        } catch (Exception e) {
            voiceModel = null;
        }
    }

    private void openAssistant() {
        assistantOpen = true;
        LinearLayout pane = column();
        pane.setPadding(dp(22), dp(18), dp(22), dp(18));
        pane.setGravity(Gravity.CENTER);
        pane.setBackground(shape(panel, 24));

        assistantOrb = text("◉", 62, 0xffbca6ff, true);
        assistantStatus = text("Сейчас отвечу…", 16, Color.WHITE, true);
        TextView sub = text("Голосовой помощник BlueDriver", 12, 0xff9fb5d7, false);
        Button mic = new Button(this);
        mic.setText("НАЧАТЬ СЛУШАТЬ");
        mic.setTextColor(Color.WHITE);
        mic.setBackgroundTintList(ColorStateList.valueOf(purple));
        mic.setOnClickListener(v -> startListening());
        Button close = new Button(this);
        close.setText("ЗАКРЫТЬ");
        close.setOnClickListener(v -> {
            stopListening();
            assistantDialog.dismiss();
        });

        pane.addView(assistantOrb, lp(-1, 100, 0));
        pane.addView(assistantStatus, lp(-1, 42, 0));
        pane.addView(sub, lp(-1, 28, 0));
        pane.addView(mic, lp(-1, 52, 0));
        pane.addView(close, lp(-1, 48, 0));

        assistantDialog = new AlertDialog.Builder(this).setView(pane).create();
        assistantDialog.getWindow();
        assistantDialog.show();
        assistantDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        assistantDialog.getWindow().setLayout(dp(430), -2);

        speak("Слушаю, мой господин", () -> {
            if (assistantOpen) startListening();
        });
    }

    private void speak(String phrase, Runnable after) {
        if (ttsReady && tts != null) {
            tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "bluedriver_prompt");
            if (after != null) {
                // Небольшая пауза после приветствия; распознавание запускается после неё.
                assistantStatus.postDelayed(after, 1800);
            }
        } else {
            if (assistantStatus != null) assistantStatus.setText(phrase);
            if (after != null) assistantStatus.postDelayed(after, 1800);
        }
    }

    private void startListening() {
        if (voiceModel == null) {
            assistantStatus.setText("Нет офлайн-модели речи. См. README.");
            speak("Для распознавания речи нужна русская языковая модель", null);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        try {
            stopListening();
            Recognizer recognizer = new Recognizer(voiceModel, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            listening = true;
            assistantStatus.setText("Слушаю вас…");
            assistantOrb.setText("◉");
            assistantOrb.setTextColor(0xff8f7cff);
        } catch (Exception e) {
            assistantStatus.setText("Не удалось включить микрофон");
        }
    }

    private void stopListening() {
        listening = false;
        if (speechService != null) {
            try { speechService.stop(); } catch (Exception ignored) {}
            speechService = null;
        }
    }

    @Override public void onResult(String hypothesis) { handleRecognition(hypothesis); }
    @Override public void onFinalResult(String hypothesis) { handleRecognition(hypothesis); }
    @Override public void onPartialResult(String hypothesis) {
        try {
            JSONObject o = new JSONObject(hypothesis);
            String partial = o.optString("partial", "");
            if (!partial.isEmpty() && assistantStatus != null)
                assistantStatus.setText("Слышу: " + partial);
        } catch (Exception ignored) {}
    }
    @Override public void onError(Exception e) {
        listening = false;
        if (assistantStatus != null) assistantStatus.setText("Ошибка распознавания. Нажмите слушать.");
    }
    @Override public void onTimeout() {
        listening = false;
        if (assistantStatus != null) assistantStatus.setText("Я здесь. Скажите команду ещё раз.");
    }

    private void handleRecognition(String json) {
        if (!listening) return;
        listening = false;
        stopListening();
        String words = "";
        try { words = new JSONObject(json).optString("text", "").toLowerCase(new Locale("ru", "RU")); }
        catch (Exception ignored) {}
        if (words.trim().isEmpty()) {
            assistantStatus.setText("Не расслышала. Попробуйте ещё раз.");
            speak("Не расслышала. Повторите, пожалуйста", null);
            return;
        }
        assistantStatus.setText("Вы сказали: " + words);
        executeCommand(words);
    }

    private void executeCommand(String command) {
        if (command.contains("навиг") || command.contains("карты") || command.contains("маршрут")) {
            speak("Открываю навигацию", null); openMaps();
        } else if (command.contains("музык") || command.contains("песн") || command.contains("трек")) {
            speak("Открываю музыку", null); launchMusic();
        } else if (command.contains("радио")) {
            speak("Включаю радио", null); launchRadio();
        } else if (command.contains("приложен")) {
            speak("Открываю приложения", null); showApps();
        } else if (command.contains("закрой") || command.contains("стоп")) {
            speak("Хорошо", null);
        } else {
            speak("Пока я не знаю эту команду. Попробуйте сказать: открой навигатор, музыку или радио", null);
        }
    }

    private void openMaps() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q="));
            i.setPackage("com.google.android.apps.maps");
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))); }
            catch (Exception ignored) { toast("Приложение навигации не найдено"); }
        }
    }

    private void launchMusic() {
        String[] actions = {"android.intent.action.MUSIC_PLAYER"};
        for (String action : actions) {
            Intent i = new Intent(action);
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
        }
        showApps();
    }

    private void launchRadio() {
        String[] packages = {"com.tw.radio", "com.android.fmradio", "com.microntek.radio", "com.ts.radio"};
        for (String p : packages) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { startActivity(i); return; }
        }
        toast("Радио не найдено автоматически. Выберите его в приложениях.");
        showApps();
    }

    private void showApps() {
        PackageManager pm = getPackageManager();
        List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        Collections.sort(apps, (a,b) -> pm.getApplicationLabel(a).toString()
            .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
        LinearLayout list = column();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        for (android.content.pm.ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue;
            TextView item = text(pm.getApplicationLabel(app).toString(), 16, Color.WHITE, false);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            item.setPadding(dp(16), 0, dp(16), 0);
            item.setBackground(shape(panel2, 10));
            LinearLayout.LayoutParams p = lp(-1, 48, 0);
            p.bottomMargin = dp(5);
            list.addView(item, p);
            item.setOnClickListener(v -> {
                Intent i = pm.getLaunchIntentForPackage(app.packageName);
                if (i != null) startActivity(i);
            });
        }
        new AlertDialog.Builder(this).setTitle("Установленные приложения")
            .setView(scroll).setNegativeButton("Закрыть", null).show();
    }

    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("Настройки BlueDriver")
            .setMessage("Версия 1.1\\nГолосовой помощник использует офлайн-движок Vosk.\\nДля распознавания нужна русская модель речи. Подробности — в README.")
            .setPositiveButton("Понятно", null).show();
    }

    private void updateClock() {
        if (clock == null) return;
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        date.setText(new SimpleDateFormat("EEE, d MMM", new Locale("ru", "RU")).format(new Date()));
        clock.postDelayed(this::updateClock, 30000);
    }
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            startListening();
        else if (requestCode == REQ_MIC && assistantStatus != null)
            assistantStatus.setText("Нужно разрешить доступ к микрофону");
    }

    @Override protected void onDestroy() {
        stopListening();
        if (voiceModel != null) voiceModel.close();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
