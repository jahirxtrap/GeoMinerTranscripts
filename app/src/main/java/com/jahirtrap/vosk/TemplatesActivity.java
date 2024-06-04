package com.jahirtrap.vosk;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class TemplatesActivity extends AppCompatActivity {
    private static final int ADD_EDIT_TEMPLATE_REQUEST_CODE = 1;
    private LinearLayout templatesContainer;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.templates);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FloatingActionButton fabAddTemplate = findViewById(R.id.fab_add_template);
        fabAddTemplate.setOnClickListener(view -> {
            Intent intent = new Intent(TemplatesActivity.this, AddEditTemplateActivity.class);
            startActivityForResult(intent, ADD_EDIT_TEMPLATE_REQUEST_CODE);
        });

        templatesContainer = findViewById(R.id.templates_container);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        loadTemplates();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void loadTemplates() {
        templatesContainer.removeAllViews();

        // Load templates from assets
        try {
            String[] files = getAssets().list("templates");
            for (String filename : files) {
                InputStream is = getAssets().open("templates/" + filename);
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String json = new String(buffer, StandardCharsets.UTF_8);
                JSONObject template = new JSONObject(json);
                addTemplateView(template, true); // default template
            }
        } catch (Exception e) {
            e.fillInStackTrace();
        }

        // Load templates from preferences
        try {
            Set<String> templateNames = sharedPreferences.getStringSet("templates", new HashSet<>());
            for (String templateName : templateNames) {
                String json = sharedPreferences.getString(templateName, null);
                if (json != null && isValidJSONObject(json)) {
                    JSONObject template = new JSONObject(json);
                    addTemplateView(template, false);
                }
            }
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    private boolean isValidJSONObject(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException ex) {
            return false;
        }
    }

    private void addTemplateView(final JSONObject template, boolean isDefault) {
        try {
            View templateView = getLayoutInflater().inflate(R.layout.template_item, templatesContainer, false);
            TextView textLabel = templateView.findViewById(R.id.text_label);
            textLabel.setText(template.getString("label"));
            ImageView btnEdit = templateView.findViewById(R.id.btn_edit);
            ImageView btnDelete = templateView.findViewById(R.id.btn_delete);

            btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(TemplatesActivity.this, AddEditTemplateActivity.class);
                intent.putExtra("template", template.toString());
                startActivityForResult(intent, ADD_EDIT_TEMPLATE_REQUEST_CODE);
            });

            if (isDefault) {
                btnDelete.setVisibility(View.GONE);
            } else {
                btnDelete.setOnClickListener(v -> {
                    try {
                        deleteTemplate(template.getString("label"));
                        loadTemplates();
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            templatesContainer.addView(templateView);
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    private void deleteTemplate(String templateName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(templateName);
        Set<String> templates = sharedPreferences.getStringSet("templates", new HashSet<>());
        templates.remove(templateName);
        editor.putStringSet("templates", templates);

        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_EDIT_TEMPLATE_REQUEST_CODE && resultCode == RESULT_OK) {
            loadTemplates();
        }
    }
}
