package com.jahirtrap.vosk;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.json.JSONException;
import org.json.JSONObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity implements RecognitionListener {
    private static final int STATE_START = 0, STATE_READY = 1, STATE_DONE = 2, STATE_MIC = 3;
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private final HashMap<String, EditText> editTextMap = new HashMap<>();
    private ArrayList<String> resultP = new ArrayList<>(), result = new ArrayList<>();
    private Model model;
    private ProgressBar progressBar;
    private Toast toast;
    private TextToSpeechManager tts;
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
    private String format;
    private boolean narrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        narrator = preferences.getBoolean("narrator_switch", false);
        lineCommand = preferences.getString("line_command_preference", "línea");

        // Click listeners
        findViewById(R.id.btn_record).setOnClickListener(view -> recognizeMicrophone());
        findViewById(R.id.btn_pause).setOnClickListener(view -> togglePause());

        LibVosk.setLogLevel(LogLevel.INFO);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }

        // Load default template
        generateForm("Default.json");

        // Narrator
        tts = new TextToSpeechManager(this);

        // Preferences listener
        preferenceListener = (sharedPrefs, key) -> {
            if (key == null) return;
            switch (key) {
                case "theme_preference":
                    recreate();
                    break;
                case "visualizer_switch":
                    if (visualizer != null) {
                        if (speechService != null) {
                            setUiState(STATE_DONE);
                            speechService.stop();
                            speechService = null;
                        }
                        visualizer.clear();
                        visualizer_container.setVisibility(sharedPrefs.getBoolean(key, true) ? View.VISIBLE : View.GONE);
                    }
                    break;
                case "narrator_switch":
                    narrator = preferences.getBoolean("narrator_switch", false);
                    if (narrator) tts = new TextToSpeechManager(this);
                    else tts.stop();
                    break;
                case "line_command_preference":
                    lineCommand = sharedPrefs.getString(key, "línea");
                    break;
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
                copyToClipboard();
                return true;
            case 101:
                clear();
                return true;
            case 102:
                showTemplateDialog();
                return true;
            case 104:
                format = "pdf";
                exportTo(format);
                return true;
            case 105:
                format = "docx";
                exportTo(format);
                return true;
            case 106:
                format = "xlsx";
                exportTo(format);
                return true;
            case 107:
                format = "txt";
                exportTo(format);
                return true;
            case 108:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case 109:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void exportTo(String format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportData(format);
        } else {
            if (checkPermission()) {
                exportData(format);
            }
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    exportData(format);
                }
            });

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return false;
        } else {
            return true;
        }
    }

    private void exportData(String format) {
        String label = capitalizeFirstLetter(((TextView) formContainer.getChildAt(0)).getText().toString().trim());
        switch (format) {
            case "pdf":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createPdfForAndroid10AndAbove(label);
                } else {
                    createPdfForBelowAndroid10(label);
                }
                break;
            case "docx":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createDocxForAndroid10AndAbove(label);
                } else {
                    createDocxForBelowAndroid10(label);
                }
                break;
            case "xlsx":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createXlsxForAndroid10AndAbove(label);
                } else {
                    createXlsxForBelowAndroid10(label);
                }
                break;
            case "txt":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    createTxtForAndroid10AndAbove(label);
                } else {
                    createTxtForBelowAndroid10(label);
                }
                break;
        }
    }

    private void createPdfForAndroid10AndAbove(String label) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, label + ".pdf");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        OutputStream outputStream;
        try {
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            if (uri != null) {
                outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    writePdfContent(outputStream, label);
                    outputStream.close();
                    showToast((String) this.getResources().getText(R.string.export_success));
                }
            } else {
                showToast((String) this.getResources().getText(R.string.export_error));
            }
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void createPdfForBelowAndroid10(String label) {
        File pdfDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!pdfDir.exists()) return;

        File pdfFile = new File(pdfDir, label + ".pdf");
        try {
            OutputStream outputStream = Files.newOutputStream(pdfFile.toPath());
            writePdfContent(outputStream, label);
            outputStream.close();
            showToast((String) this.getResources().getText(R.string.export_success));
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void writePdfContent(OutputStream outputStream, String label) {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDocument = new PdfDocument(writer);
        pdfDocument.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdfDocument);
        document.setMargins(28.35f, 28.35f, 28.35f, 28.35f);

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 2})).useAllAvailableWidth();
        Cell cell = new Cell(1, 2).add(new Paragraph(label));
        cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
        table.addCell(cell);

        for (int i = 1; i < formContainer.getChildCount(); i++) {
            View child = formContainer.getChildAt(i);
            if (child instanceof EditText) {
                String field = capitalizeFirstLetter(((EditText) child).getHint().toString());
                String value = capitalizeFirstLetter(((EditText) child).getText().toString().trim());
                table.addCell(new Cell().add(new Paragraph(field)));
                table.addCell(new Cell().add(new Paragraph(value)));
            }
        }

        document.add(table);
        document.close();
    }

    private void createDocxForAndroid10AndAbove(String label) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, label + ".docx");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        try {
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        writeDocxContent(outputStream, label);
                        showToast((String) this.getResources().getText(R.string.export_success));
                    }
                }
            } else {
                showToast((String) this.getResources().getText(R.string.export_error));
            }
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void createDocxForBelowAndroid10(String label) {
        File docxDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!docxDir.exists()) return;

        File docxFile = new File(docxDir, label + ".docx");
        try (FileOutputStream outputStream = new FileOutputStream(docxFile)) {
            writeDocxContent(outputStream, label);
            showToast((String) this.getResources().getText(R.string.export_success));
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    public void writeDocxContent(OutputStream outputStream, String label) {
        XWPFDocument document = new XWPFDocument();

        CTSectPr sectPr = document.getDocument().getBody().addNewSectPr();

        CTPageSz pageSz = sectPr.addNewPgSz();
        pageSz.setW(BigInteger.valueOf(11906));
        pageSz.setH(BigInteger.valueOf(16838));

        sectPr.addNewPgMar().setLeft(BigInteger.valueOf(567));
        sectPr.addNewPgMar().setRight(BigInteger.valueOf(567));
        sectPr.addNewPgMar().setTop(BigInteger.valueOf(567));
        sectPr.addNewPgMar().setBottom(BigInteger.valueOf(567));

        XWPFTable table = document.createTable(1, 2);

        CTTblWidth tblWidth = table.getCTTbl().addNewTblPr().addNewTblW();
        tblWidth.setType(STTblWidth.DXA);
        tblWidth.setW(BigInteger.valueOf(10772));

        XWPFTableRow headerRow = table.getRow(0);

        XWPFTableCell headerCell = headerRow.getCell(0);
        setCellText(headerCell, label);
        headerCell.setColor("C0C0C0");

        CTTcPr tcPr = headerCell.getCTTc().addNewTcPr();
        tcPr.addNewGridSpan().setVal(BigInteger.valueOf(2));
        headerRow.removeCell(1);

        for (int i = 1; i < formContainer.getChildCount(); i++) {
            View child = formContainer.getChildAt(i);
            if (child instanceof EditText) {
                String field = capitalizeFirstLetter(((EditText) child).getHint().toString());
                String value = capitalizeFirstLetter(((EditText) child).getText().toString().trim());
                XWPFTableRow row = table.createRow();
                XWPFTableCell cell1 = row.getCell(0);
                if (cell1 == null) {
                    cell1 = row.createCell();
                }
                XWPFTableCell cell2 = row.getCell(1);
                if (cell2 == null) {
                    cell2 = row.createCell();
                }

                setCellWidth(cell1, 3590);
                setCellWidth(cell2, 7182);

                setCellText(cell1, field);
                setCellText(cell2, value);
            }
        }

        try {
            document.write(outputStream);
            outputStream.close();
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void setCellWidth(XWPFTableCell cell, int width) {
        CTTcPr tcPr = cell.getCTTc().addNewTcPr();
        CTTblWidth cellWidth = tcPr.addNewTcW();
        cellWidth.setType(STTblWidth.DXA);
        cellWidth.setW(BigInteger.valueOf(width));
    }

    private void setCellText(XWPFTableCell cell, String text) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        para.setIndentationLeft(42);
        para.setIndentationRight(42);
        para.setSpacingBefore(68);
        para.setSpacingAfter(68);
        para.setWordWrap(true);
        XWPFRun run = para.createRun();
        run.setFontFamily("Arial");
        run.setFontSize(12);
        run.setText(text);
    }

    private void createXlsxForAndroid10AndAbove(String label) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, label + ".xlsx");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        try {
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        writeXlsxContent(outputStream, label);
                        showToast((String) this.getResources().getText(R.string.export_success));
                    }
                }
            } else {
                showToast((String) this.getResources().getText(R.string.export_error));
            }
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void createXlsxForBelowAndroid10(String label) {
        File xlsxDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!xlsxDir.exists()) return;

        File xlsxFile = new File(xlsxDir, label + ".xlsx");
        try (FileOutputStream outputStream = new FileOutputStream(xlsxFile)) {
            writeXlsxContent(outputStream, label);
            showToast((String) this.getResources().getText(R.string.export_success));
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void writeXlsxContent(OutputStream outputStream, String label) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(label);

        Row headerRow = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Cell headerCell1 = headerRow.createCell(0);
        headerCell1.setCellValue(label);

        int rowCount = 1;
        for (int i = 1; i < formContainer.getChildCount(); i++) {
            View child = formContainer.getChildAt(i);
            if (child instanceof EditText) {
                String field = capitalizeFirstLetter(((EditText) child).getHint().toString());
                String value = capitalizeFirstLetter(((EditText) child).getText().toString().trim());

                Row row = sheet.createRow(rowCount++);
                org.apache.poi.ss.usermodel.Cell cell1 = row.createCell(0);
                cell1.setCellValue(field);
                org.apache.poi.ss.usermodel.Cell cell2 = row.createCell(1);
                cell2.setCellValue(value);
            }
        }

        workbook.write(outputStream);
        workbook.close();
    }

    private void createTxtForAndroid10AndAbove(String label) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, label + ".txt");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/");

        try {
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), contentValues);
            if (uri != null) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                    writeTxtContent(writer, label);
                    showToast((String) this.getResources().getText(R.string.export_success));
                }
            } else {
                showToast((String) this.getResources().getText(R.string.export_error));
            }
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void createTxtForBelowAndroid10(String label) {
        File txtDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        if (!txtDir.exists()) return;

        File txtFile = new File(txtDir, label + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(txtFile))) {
            writeTxtContent(writer, label);
            showToast((String) this.getResources().getText(R.string.export_success));
        } catch (IOException e) {
            e.fillInStackTrace();
            showToast((String) this.getResources().getText(R.string.export_error));
        }
    }

    private void writeTxtContent(BufferedWriter writer, String label) throws IOException {
        writer.write(label + "\n");

        for (int i = 1; i < formContainer.getChildCount(); i++) {
            View child = formContainer.getChildAt(i);
            if (child instanceof EditText) {
                String field = capitalizeFirstLetter(((EditText) child).getHint().toString());
                String value = capitalizeFirstLetter(((EditText) child).getText().toString().trim());
                writer.write(field + ": " + value + "\n");
            }
        }
    }

    public String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private void showTemplateDialog() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> templates = new HashSet<>(sharedPreferences.getStringSet("templates", new HashSet<>()));

        try {
            String[] files = getAssets().list("templates");
            if (files != null) {
                for (String filename : files) {
                    templates.add(filename.replace(".json", ""));
                }
            }
        } catch (IOException e) {
            e.fillInStackTrace();
        }

        String[] templateArray = templates.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.templates_alert_title)
                .setItems(templateArray, (dialog, which) -> {
                    String selectedTemplate = templateArray[which];
                    clear();
                    generateForm(selectedTemplate);
                }).show();
    }

    private void generateForm(String templateName) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String templateJson = sharedPreferences.getString(templateName, null);

        if (templateJson != null) {
            generateFormFromPreferencesOrAssets(templateJson, false);
        } else {
            generateFormFromPreferencesOrAssets(templateName + ".json", true);
        }

        addFocusChangeListener();
    }

    private void generateFormFromPreferencesOrAssets(String source, boolean isAsset) {
        JSONObject jsonObject = isAsset ? loadTemplate("templates/" + source) : loadTemplateFromString(source);
        if (jsonObject == null) {
            jsonObject = loadTemplate("templates/Default.json");
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

    private void addFocusChangeListener() {
        for (EditText editText : editTextMap.values()) {
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    currentEditText = editText;
                }
            });
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

    private JSONObject loadTemplateFromString(String templateJson) {
        try {
            return new JSONObject(templateJson);
        } catch (JSONException ex) {
            return null;
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

    private void copyToClipboard() {
        String label = capitalizeFirstLetter(((TextView) formContainer.getChildAt(0)).getText().toString().trim());
        StringBuilder text = new StringBuilder();
        text.append(label).append("\n");

        for (int i = 1; i < formContainer.getChildCount(); i++) {
            View child = formContainer.getChildAt(i);
            if (child instanceof EditText) {
                String field = capitalizeFirstLetter(((EditText) child).getHint().toString());
                String value = capitalizeFirstLetter(((EditText) child).getText().toString().trim());
                text.append(field).append(": ").append(value).append("\n");
            }
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied", text.toString().trim());
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

        if (tts != null) {
            tts.stop();
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
                            String value = textToNumber(normalizedText);
                            resultP.set(index, result.get(index) + value + " ");
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
                            String value = textToNumber(normalizedText);
                            result.set(index, result.get(index) + value + " ");
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

    private static String textToNumber(String text) {
        HashMap<String, Integer> mapNumber = getStringIntegerHashMap();
        StringTokenizer tokenizer = new StringTokenizer(text);
        StringBuilder result = new StringBuilder();
        int currentNumber = 0;
        int finalNumber = 0;
        boolean numberFound = false;
        boolean isPreviousWordNumber = false;
        boolean isNegative = false;

        while (tokenizer.hasMoreTokens()) {
            String word = tokenizer.nextToken();

            if (word.equals("menos")) {
                if (tokenizer.hasMoreTokens()) {
                    String nextWord = tokenizer.nextToken();
                    if (mapNumber.containsKey(nextWord)) {
                        isNegative = true;
                        word = nextWord;
                    } else {
                        result.append("menos ").append(nextWord).append(" ");
                        continue;
                    }
                } else {
                    result.append("menos ");
                    continue;
                }
            }

            if (word.equals("y") && isPreviousWordNumber) {
                continue;
            }

            if (mapNumber.containsKey(word)) {
                int value = mapNumber.get(word);
                numberFound = true;
                isPreviousWordNumber = true;

                if (value == 1000 || value == 1000000) {
                    if (currentNumber == 0) {
                        currentNumber = 1;
                    }
                    currentNumber *= value;
                    finalNumber += currentNumber;
                    currentNumber = 0;
                } else if (value == 100 && currentNumber != 0) {
                    currentNumber *= value;
                } else {
                    currentNumber += value;
                }
            } else {
                if (numberFound) {
                    finalNumber += currentNumber;
                    if (isNegative) {
                        finalNumber *= -1;
                        isNegative = false;
                    }
                    result.append(finalNumber).append(" ");
                    finalNumber = 0;
                    currentNumber = 0;
                    numberFound = false;
                }
                result.append(word).append(" ");
                isPreviousWordNumber = false;
            }
        }

        if (numberFound) {
            finalNumber += currentNumber;
            if (isNegative) {
                finalNumber *= -1;
            }
            result.append(finalNumber);
        }

        return result.toString().trim();
    }

    @NonNull
    private static HashMap<String, Integer> getStringIntegerHashMap() {
        HashMap<String, Integer> mapNumber = new HashMap<>();
        mapNumber.put("cero", 0);
        mapNumber.put("uno", 1);
        mapNumber.put("dos", 2);
        mapNumber.put("tres", 3);
        mapNumber.put("cuatro", 4);
        mapNumber.put("cinco", 5);
        mapNumber.put("seis", 6);
        mapNumber.put("siete", 7);
        mapNumber.put("ocho", 8);
        mapNumber.put("nueve", 9);
        mapNumber.put("diez", 10);
        mapNumber.put("once", 11);
        mapNumber.put("doce", 12);
        mapNumber.put("trece", 13);
        mapNumber.put("catorce", 14);
        mapNumber.put("quince", 15);
        mapNumber.put("dieciseis", 16);
        mapNumber.put("diecisiete", 17);
        mapNumber.put("dieciocho", 18);
        mapNumber.put("diecinueve", 19);
        mapNumber.put("veinte", 20);
        mapNumber.put("veintiuno", 21);
        mapNumber.put("veintidos", 22);
        mapNumber.put("veintitres", 23);
        mapNumber.put("veinticuatro", 24);
        mapNumber.put("veinticinco", 25);
        mapNumber.put("veintiseis", 26);
        mapNumber.put("veintisiete", 27);
        mapNumber.put("veintiocho", 28);
        mapNumber.put("veintinueve", 29);
        mapNumber.put("treinta", 30);
        mapNumber.put("cuarenta", 40);
        mapNumber.put("cincuenta", 50);
        mapNumber.put("sesenta", 60);
        mapNumber.put("setenta", 70);
        mapNumber.put("ochenta", 80);
        mapNumber.put("noventa", 90);
        mapNumber.put("cien", 100);
        mapNumber.put("ciento", 100);
        mapNumber.put("doscientos", 200);
        mapNumber.put("trescientos", 300);
        mapNumber.put("cuatrocientos", 400);
        mapNumber.put("quinientos", 500);
        mapNumber.put("seiscientos", 600);
        mapNumber.put("setecientos", 700);
        mapNumber.put("ochocientos", 800);
        mapNumber.put("novecientos", 900);
        mapNumber.put("mil", 1000);
        mapNumber.put("millon", 1000000);
        mapNumber.put("millones", 1000000);
        return mapNumber;
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                progressBar.setVisibility(View.VISIBLE);
                findViewById(R.id.btn_record).setEnabled(false);
                findViewById(R.id.btn_pause).setEnabled(false);
                break;
            case STATE_READY:
                progressBar.setVisibility(View.GONE);
                ((Button) findViewById(R.id.btn_record)).setText(R.string.record);
                findViewById(R.id.btn_record).setEnabled(true);
                findViewById(R.id.btn_pause).setEnabled(false);
                break;
            case STATE_DONE:
                if (narrator) tts.speak("Grabación detenida");
                stopRecording();
                ((Button) findViewById(R.id.btn_record)).setText(R.string.record);
                findViewById(R.id.btn_record).setEnabled(true);
                findViewById(R.id.btn_pause).setEnabled(false);
                isPaused = false;
                ((Button) findViewById(R.id.btn_pause)).setText(R.string.pause);
                break;
            case STATE_MIC:
                if (narrator) tts.speak("Grabación iniciada");
                startRecording();
                ((Button) findViewById(R.id.btn_record)).setText(R.string.stop);
                if (!preferences.getBoolean("visualizer_switch", true))
                    showToast((String) this.getResources().getText(R.string.recording));
                findViewById(R.id.btn_record).setEnabled(true);
                findViewById(R.id.btn_pause).setEnabled(true);
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
        ((Button) findViewById(R.id.btn_record)).setText(R.string.record);
        findViewById(R.id.btn_record).setEnabled(false);
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
            ((Button) findViewById(R.id.btn_pause)).setText(isPaused ? R.string.resume : R.string.pause);
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
