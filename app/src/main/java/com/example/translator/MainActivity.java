package com.example.translator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Khai báo các View
    private EditText originalText, translatedText;
    private FloatingActionButton btnMicro;

    // Khai báo bộ dịch
    private Translator englishVietnameseTranslator;

    // Mã định danh cho Speech To Text
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    private static final int PERMISSION_CODE_RECORD = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Ánh xạ View (Quan trọng: Phải ánh xạ trước khi sử dụng để tránh lỗi Out/Crash)
        initViews();

        // 2. Cấu hình bộ dịch ML Kit
        setupTranslator();

        // 3. Sự kiện nút Micro
        btnMicro.setOnClickListener(v -> checkPermissionAndStartSpeech());

        // 4. Lắng nghe thay đổi văn bản để tự động dịch
        setupTextWatcher();
    }

    private void initViews() {
        originalText = findViewById(R.id.originalText);
        translatedText = findViewById(R.id.translatedText);
        btnMicro = findViewById(R.id.btnMicro);
    }

    private void setupTranslator() {
        // Cấu hình dịch từ Tiếng Anh sang Tiếng Việt
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                .build();

        englishVietnameseTranslator = Translation.getClient(options);

        // Điều kiện tải model (Nên dùng Wifi để tránh tốn dung lượng của khách)
        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        englishVietnameseTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    // Sẵn sàng dịch - Có thể thông báo cho người dùng
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Vui lòng bật Wifi để tải bộ dịch", Toast.LENGTH_LONG).show();
                });
    }

    private void setupTextWatcher() {
        originalText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                if (!input.isEmpty()) {
                    performTranslation(input);
                } else {
                    translatedText.setText("");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performTranslation(String text) {
        if (englishVietnameseTranslator != null) {
            englishVietnameseTranslator.translate(text)
                    .addOnSuccessListener(result -> {
                        translatedText.setText(result);
                    })
                    .addOnFailureListener(e -> {
                        translatedText.setText("Đang xử lý...");
                    });
        }
    }

    private void checkPermissionAndStartSpeech() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CODE_RECORD);
        } else {
            startSpeechToText();
        }
    }

    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Mời bạn nói...");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                // Điền kết quả vào ô nhập liệu, TextWatcher sẽ tự kích hoạt dịch
                originalText.setText(result.get(0));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE_RECORD && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeechToText();
        } else {
            Toast.makeText(this, "Bạn cần cấp quyền Micro để sử dụng tính năng này", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Giải phóng tài nguyên
        if (englishVietnameseTranslator != null) {
            englishVietnameseTranslator.close();
        }
    }
}