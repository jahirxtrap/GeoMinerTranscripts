package com.jahirtrap.vosk;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class AddEditTemplateActivity extends AppCompatActivity {
    private EditText editLabel;
    private LinearLayout fieldsContainer;
    private SharedPreferences sharedPreferences;
    private Button btnAddField;
    private Button btnSaveTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_edit_template);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        editLabel = findViewById(R.id.edit_label);
        fieldsContainer = findViewById(R.id.fields_container);
        btnAddField = findViewById(R.id.btn_add_field);
        btnSaveTemplate = findViewById(R.id.btn_save_template);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        btnAddField.setOnClickListener(v -> {
            addNewField();
            checkFormValidity();
        });

        btnSaveTemplate.setOnClickListener(v -> saveTemplate());

        editLabel.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkFormValidity();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Load template if editing
        String templateJson = getIntent().getStringExtra("template");
        if (templateJson != null) {
            loadTemplate(templateJson);
        }

        // Ensure there is at least one field
        if (fieldsContainer.getChildCount() == 0) {
            addNewField();
        }

        checkFormValidity();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void addNewField() {
        View fieldView = getLayoutInflater().inflate(R.layout.field_item, fieldsContainer, false);
        EditText editFieldName = fieldView.findViewById(R.id.edit_field_name);
        ImageButton btnDeleteField = fieldView.findViewById(R.id.btn_delete_field);

        editFieldName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkFormValidity();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnDeleteField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fieldsContainer.removeView(fieldView);
                checkFormValidity();
            }
        });

        fieldsContainer.addView(fieldView);
        checkFormValidity();
    }

    private void saveTemplate() {
        String label = editLabel.getText().toString().trim();
        try {
            JSONObject data = new JSONObject();
            for (int i = 0; i < fieldsContainer.getChildCount(); i++) {
                View fieldView = fieldsContainer.getChildAt(i);
                EditText editFieldName = fieldView.findViewById(R.id.edit_field_name);
                EditText editFieldValue = fieldView.findViewById(R.id.edit_field_value);
                String fieldName = editFieldName.getText().toString().trim();
                String fieldValue = editFieldValue.getText().toString().trim();
                data.put(fieldName, fieldValue);
            }

            JSONObject template = new JSONObject();
            template.put("label", label);
            template.put("data", data);

            // Save template to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(label, template.toString());

            // Add template name to the set of templates
            Set<String> templates = sharedPreferences.getStringSet("templates", new HashSet<>());
            templates.add(label);
            editor.putStringSet("templates", templates);

            editor.apply();

            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    private void loadTemplate(String templateJson) {
        try {
            JSONObject template = new JSONObject(templateJson);
            editLabel.setText(template.getString("label"));
            JSONObject data = template.getJSONObject("data");
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = data.getString(key);
                addFieldWithValue(key, value);
            }
        } catch (Exception e) {
            e.fillInStackTrace();
        }
    }

    private void addFieldWithValue(String fieldName, String fieldValue) {
        View fieldView = getLayoutInflater().inflate(R.layout.field_item, fieldsContainer, false);
        EditText editFieldName = fieldView.findViewById(R.id.edit_field_name);
        EditText editFieldValue = fieldView.findViewById(R.id.edit_field_value);
        ImageButton btnDeleteField = fieldView.findViewById(R.id.btn_delete_field);

        editFieldName.setText(fieldName);
        editFieldValue.setText(fieldValue);

        editFieldName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkFormValidity();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnDeleteField.setOnClickListener(v -> {
            fieldsContainer.removeView(fieldView);
            checkFormValidity();
        });

        fieldsContainer.addView(fieldView);
        checkFormValidity();
    }

    private void checkFormValidity() {
        boolean isFormValid = true;
        String label = editLabel.getText().toString().trim();

        if (label.isEmpty()) {
            isFormValid = false;
        }

        for (int i = 0; i < fieldsContainer.getChildCount(); i++) {
            View fieldView = fieldsContainer.getChildAt(i);
            EditText editFieldName = fieldView.findViewById(R.id.edit_field_name);
            if (editFieldName.getText().toString().trim().isEmpty()) {
                isFormValid = false;
                break;
            }
        }

        btnSaveTemplate.setEnabled(isFormValid);

        boolean enableAddFieldButton = true;
        if (fieldsContainer.getChildCount() > 0) {
            View lastFieldView = fieldsContainer.getChildAt(fieldsContainer.getChildCount() - 1);
            EditText lastFieldName = lastFieldView.findViewById(R.id.edit_field_name);
            enableAddFieldButton = !lastFieldName.getText().toString().trim().isEmpty();
        }
        btnAddField.setEnabled(enableAddFieldButton);
    }
}
