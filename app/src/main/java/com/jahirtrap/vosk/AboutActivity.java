package com.jahirtrap.vosk;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView versionText = findViewById(R.id.version_text);
        ImageView profileDevImage = findViewById(R.id.profile_dev_image);
        ImageView profileWebImage = findViewById(R.id.profile_web_image);
        Button appInfoButton = findViewById(R.id.app_info_button);

        Glide.with(this)
                .load(R.drawable.profile_dev)
                .transform(new CircleCrop())
                .into(profileDevImage);

        Glide.with(this)
                .load(R.drawable.profile_web)
                .transform(new CircleCrop())
                .into(profileWebImage);

        try {
            versionText.setText(getString(R.string.version, getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
        } catch (PackageManager.NameNotFoundException e) {
            e.fillInStackTrace();
        }

        appInfoButton.setOnClickListener(v -> {
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(uri));
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
