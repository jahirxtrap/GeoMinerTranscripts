package com.jahirtrap.vosk;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements RecognitionListener {
    private static final int STATE_START = 0, STATE_READY = 1, STATE_DONE = 2, STATE_MIC = 3;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private final HashMap<String, EditText> editTextMap = new HashMap<>();
    private ArrayList<String> resultP = new ArrayList<>(), result = new ArrayList<>();
    private Model model;
    private ProgressBar progressBar;
    private Toast toast;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private LinearLayout formContainer;
    private AudioVisualizerView visualizer;
    private boolean isPaused = false, isRecording = false;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private EditText currentEditText;
    private String lineCommand;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        progressBar = findViewById(R.id.progress_bar);
        formContainer = findViewById(R.id.form_container);
        visualizer = findViewById(R.id.visualizer);
        View visualizer_container = findViewById(R.id.visualizer_container);
        setUiState(STATE_START);

        // Load preferences
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        setAppTheme(preferences.getString("theme_preference", "system"));
        visualizer_container.setVisibility(preferences.getBoolean("visualizer_switch", true) ? View.VISIBLE : View.GONE);
        lineCommand = preferences.getString("line_command_preference", "línea");

        findViewById(R.id.record).setOnClickListener(view -> recognizeMicrophone());
        findViewById(R.id.pause).setOnClickListener(view -> togglePause());

        LibVosk.setLogLevel(LogLevel.INFO);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }

        // Load default template
        generateForm("default.json");

        // Preferences listener
        preferenceListener = (sharedPrefs, key) -> {
            if (key == null) return;
            if (key.equals("theme_preference")) {
                recreate();
            } else if (key.equals("visualizer_switch")) {
                boolean enabled = sharedPrefs.getBoolean(key, true);
                if (visualizer != null) {
                    if (speechService != null) {
                        setUiState(STATE_DONE);
                        speechService.stop();
                        speechService = null;
                    }
                    visualizer.clear();
                    visualizer_container.setVisibility(enabled ? View.VISIBLE : View.GONE);
                }
            } else if (key.equals("line_command_preference")) {
                lineCommand = sharedPrefs.getString(key, "línea");
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    private void initModel() {
        StorageService.unpack(this, "model-es-es", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                }, (exception) -> setErrorState(R.string.failed + ": " + exception.getMessage()));
    }

    private void setAppTheme(String themePreference) {
        HashMap<String, Integer> themeMap = new HashMap<>();
        themeMap.put("light", AppCompatDelegate.MODE_NIGHT_NO);
        themeMap.put("dark", AppCompatDelegate.MODE_NIGHT_YES);

        Integer mode = themeMap.getOrDefault(themePreference, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (mode != null) AppCompatDelegate.setDefaultNightMode(mode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getOrder()) {
            case 100:
                String text = currentEditText.getText().toString().trim();
                if (!text.isEmpty()) copyToClipboard(text);
                return true;
            case 101:
                clear();
                return true;
            case 102:
                clear();
                generateForm("template.json");
                return true;
            case 103:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case 104:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void clear() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        }
        Collections.fill(resultP, "");
        Collections.fill(result, "");
        for (EditText editText : editTextMap.values()) editText.setText("");
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied", text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    private String normalizeString(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = text.replaceAll("\\p{InCombiningDiacriticalMarks}", "");
        return text.toLowerCase().trim();
    }

    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            if (json.has("partial") && !json.get("partial").toString().isEmpty()) {
                String text = json.getString("partial");
                if (!text.trim().isEmpty()) {
                    String normalizedText = normalizeString(text);
                    String normalizedLineCommand = normalizeString(lineCommand);
                    if (normalizedText.startsWith(normalizedLineCommand) || normalizedText.startsWith(normalizedLineCommand + " ")) {
                        String lineName = normalizedText.replace(normalizedLineCommand, "").trim();
                        if (editTextMap.containsKey(lineName)) {
                            currentEditText = editTextMap.get(lineName);
                            showToast(lineName);
                            focusText();
                        }
                    } else {
                        int index = getIndex();
                        if (index != -1) {
                            resultP.set(index, result.get(index) + text + " ");
                            fillText(resultP);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.fillInStackTrace();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            if (json.has("text") && !json.get("text").toString().isEmpty()) {
                String text = json.getString("text");
                if (!text.trim().isEmpty()) {
                    String normalizedText = normalizeString(text);
                    String normalizedLineCommand = normalizeString(lineCommand);
                    if (normalizedText.startsWith(normalizedLineCommand) || normalizedText.startsWith(normalizedLineCommand + " ")) {
                        String lineName = normalizedText.replace(normalizedLineCommand, "").trim();
                        if (editTextMap.containsKey(lineName)) {
                            currentEditText = editTextMap.get(lineName);
                            showToast(lineName);
                            focusText();
                        }
                    } else {
                        int index = getIndex();
                        if (index != -1) {
                            result.set(index, result.get(index) + text + " ");
                            resultP.set(index, result.get(index));
                            fillText(resultP);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.fillInStackTrace();
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        int index = getIndex();
        if (index != -1) {
            result.set(index, resultP.get(index));
            resultP = resultP.stream().map(String::trim).collect(Collectors.toCollection(ArrayList::new));
            result = result.stream().map(String::trim).collect(Collectors.toCollection(ArrayList::new));
            fillText(result);
            setUiState(STATE_DONE);
            if (speechStreamService != null) {
                speechStreamService = null;
            }
        }
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                progressBar.setVisibility(View.VISIBLE);
                findViewById(R.id.record).setEnabled(false);
                findViewById(R.id.pause).setEnabled(false);
                break;
            case STATE_READY:
                progressBar.setVisibility(View.GONE);
                ((Button) findViewById(R.id.record)).setText(R.string.record);
                findViewById(R.id.record).setEnabled(true);
                findViewById(R.id.pause).setEnabled(false);
                break;
            case STATE_DONE:
                stopRecording();
                ((Button) findViewById(R.id.record)).setText(R.string.record);
                findViewById(R.id.record).setEnabled(true);
                findViewById(R.id.pause).setEnabled(false);
                isPaused = false;
                ((Button) findViewById(R.id.pause)).setText(R.string.pause);
                break;
            case STATE_MIC:
                startRecording();
                ((Button) findViewById(R.id.record)).setText(R.string.stop);
                if (!preferences.getBoolean("visualizer_switch", true))
                    showToast((String) this.getResources().getText(R.string.recording));
                findViewById(R.id.record).setEnabled(true);
                findViewById(R.id.pause).setEnabled(true);
                int index = getIndex();
                if (index != -1)
                    result.set(index, !result.get(index).isEmpty() ? result.get(index) + "\n" : result.get(index));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        showToast(message);
        ((Button) findViewById(R.id.record)).setText(R.string.record);
        findViewById(R.id.record).setEnabled(false);
    }

    private int getIndex() {
        for (HashMap.Entry<String, EditText> entry : editTextMap.entrySet()) {
            if (entry.getValue().equals(currentEditText))
                return new ArrayList<>(editTextMap.values()).indexOf(entry.getValue());
        }
        return -1;
    }

    private void fillText(ArrayList<String> list) {
        int i = 0;
        for (HashMap.Entry<String, EditText> entry : editTextMap.entrySet()) {
            if (i >= list.size()) break;
            entry.getValue().setText(list.get(i++));
        }
        focusText();
    }

    private void focusText() {
        if (currentEditText != null) {
            currentEditText.requestFocus();
            currentEditText.setSelection(currentEditText.getText().length());
        }
    }

    public void showToast(String message) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private void generateForm(String filename) {
        JSONObject jsonObject = loadTemplate("templates/" + filename);
        if (jsonObject == null) {
            jsonObject = loadTemplate("templates/default.json");
            if (jsonObject == null) return;
        }

        formContainer.removeAllViews();
        editTextMap.clear();

        try {
            String label = jsonObject.getString("label");
            JSONObject data = jsonObject.getJSONObject("data");

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextSize(20);
            formContainer.addView(labelView);

            int index = 0;
            for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                String key = it.next();
                EditText editText = new EditText(this);
                editText.setHint(key);
                editText.setId(View.generateViewId());
                editTextMap.put(key.toLowerCase(), editText);
                result.add("");
                resultP.add("");
                formContainer.addView(editText);
                if (index == 0) currentEditText = editText;
                index++;
            }
        } catch (JSONException e) {
            e.fillInStackTrace();
        }
    }

    private JSONObject loadTemplate(String path) {
        try (InputStream is = getAssets().open(path)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (IOException | JSONException ex) {
            return null;
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void togglePause() {
        if (speechService != null) {
            isPaused = !isPaused;
            speechService.setPause(isPaused);
            if (isPaused) stopRecording();
            else startRecording();
            ((Button) findViewById(R.id.pause)).setText(isPaused ? R.string.resume : R.string.pause);
        }
    }

    private void startRecording() {
        if (!preferences.getBoolean("visualizer_switch", true)) return;
        int sampleRate = 16000;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        }

        audioRecord.startRecording();
        isRecording = true;

        recordingThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    float amplitude = calculateAmplitude(buffer, read);
                    runOnUiThread(() -> visualizer.addAmplitude(amplitude));
                }
            }
        });

        recordingThread.start();
    }

    private void stopRecording() {
        if (!preferences.getBoolean("visualizer_switch", true)) return;
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            recordingThread = null;
        }
    }

    private float calculateAmplitude(short[] buffer, int read) {
        float sum = 0;
        for (int i = 0; i < read; i++) {
            sum += Math.abs(buffer[i]);
        }
        return sum / read;
    }
}
