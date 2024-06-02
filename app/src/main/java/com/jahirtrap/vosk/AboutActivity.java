package com.jahirtrap.vosk;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView developerLink = findViewById(R.id.developer_link);
        TextView repositoryLink = findViewById(R.id.repository_link);
        TextView websiteLink = findViewById(R.id.website_link);
        TextView creditsLink = findViewById(R.id.credits_link);

        developerLink.setOnClickListener(v -> openLink("https://github.com/jahirxtrap"));
        repositoryLink.setOnClickListener(v -> openLink("https://github.com/jahirxtrap/GeoVoiceTranscriptor"));
        websiteLink.setOnClickListener(v -> openLink("https://diegofernandolojantn.github.io/GeoVoiceTranscriptorWeb/"));
        creditsLink.setOnClickListener(v -> openLink("https://github.com/alphacep/vosk-android-demo"));
    }

    private void openLink(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
