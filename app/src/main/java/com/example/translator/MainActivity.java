package com.example.translator;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private EditText originalText, translatedText;
    private FloatingActionButton btnMicro;
    private TextView tvSourceLang, tvTargetLang;
    private ImageButton btnSwap;

    // Đổi tên biến để tránh trùng tên Class Translator
    private Translator mTranslator;

    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    private static final int PERMISSION_CODE_RECORD = 1;

    private String sourceLangCode = TranslateLanguage.ENGLISH;
    private String targetLangCode = TranslateLanguage.VIETNAMESE;

    private final String[] languageNames = {"Tiếng Anh", "Tiếng Việt", "Tiếng Nhật", "Tiếng Hàn", "Tiếng Pháp", "Tiếng Trung"};
    private final String[] languageCodes = {
            TranslateLanguage.ENGLISH,
            TranslateLanguage.VIETNAMESE,
            TranslateLanguage.JAPANESE,
            TranslateLanguage.KOREAN,
            TranslateLanguage.FRENCH,
            TranslateLanguage.CHINESE
    };

    private FloatingActionButton btnPicture;
    private static final int REQUEST_CODE_PICK_IMAGE = 2000;

    private FloatingActionButton btnCamera;
    private static final int REQUEST_CODE_CAMERA = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupTranslator();
        setupTextWatcher();
    }

    private void initViews() {
        originalText = findViewById(R.id.originalText);
        translatedText = findViewById(R.id.translatedText);
        btnMicro = findViewById(R.id.btnMicro);
        tvSourceLang = findViewById(R.id.tvSourceLang);
        tvTargetLang = findViewById(R.id.tvTargetLang);
        btnSwap = findViewById(R.id.btnSwap);

        //Tính năng mic
        btnMicro.setOnClickListener(v -> checkPermissionAndStartSpeech());
        btnSwap.setOnClickListener(v -> swapLanguages());
        tvSourceLang.setOnClickListener(v -> showLanguageDialog(true));
        tvTargetLang.setOnClickListener(v -> showLanguageDialog(false));

        //Tính năng img
        btnPicture = findViewById(R.id.btnPicture);
        btnPicture.setOnClickListener(v -> openGallery());

        //Tính năng camera
        btnCamera = findViewById(R.id.btnCamera);
        btnCamera.setOnClickListener(v -> checkCameraPermission());
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, 2);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    private void showLanguageDialog(boolean isSource) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn ngôn ngữ");
        builder.setItems(languageNames, (dialog, which) -> {
            if (isSource) {
                sourceLangCode = languageCodes[which];
                tvSourceLang.setText(languageNames[which]);
            } else {
                targetLangCode = languageCodes[which];
                tvTargetLang.setText(languageNames[which]);
            }
            setupTranslator();
        });
        builder.show();
    }

    private void swapLanguages() {
        // LỖI LOGIC ĐÃ SỬA: Phải hoán đổi cả Code và Text
        String tempCode = sourceLangCode;
        sourceLangCode = targetLangCode;
        targetLangCode = tempCode;

        String txtSource = tvSourceLang.getText().toString();
        String txtTarget = tvTargetLang.getText().toString();
        tvSourceLang.setText(txtTarget);
        tvTargetLang.setText(txtSource);

        // Đảo văn bản (nếu có) để tiện cho người dùng
        String currentOriginal = originalText.getText().toString();
        String currentTranslated = translatedText.getText().toString();
        originalText.setText(currentTranslated);
        translatedText.setText(currentOriginal);

        setupTranslator();
    }

    private void setupTranslator() {
        if (mTranslator != null) {
            mTranslator.close();
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode)
                .setTargetLanguage(targetLangCode)
                .build();

        mTranslator = Translation.getClient(options);

        translatedText.setHint("Đang tải dữ liệu ngôn ngữ...");

        // LỖI LOGIC ĐÃ SỬA: Bỏ .requireWifi() để app có thể tải model bằng mạng di động
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        mTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translatedText.setHint("Bản dịch sẽ hiện ở đây...");
                    String currentText = originalText.getText().toString();
                    if (!currentText.isEmpty()) performTranslation(currentText);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi kết nối bộ dịch", Toast.LENGTH_SHORT).show());
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
        if (mTranslator != null) {
            mTranslator.translate(text)
                    .addOnSuccessListener(result -> translatedText.setText(result))
                    .addOnFailureListener(e -> translatedText.setText("Đang dịch..."));
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLangCode);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Mời bạn nói " + tvSourceLang.getText().toString());

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Không hỗ trợ giọng nói", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Xử lý Speech To Text (đã làm trước đó)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                originalText.setText(result.get(0));
            }
        }

        // MỚI: Xử lý chọn ảnh từ Gallery
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            android.net.Uri imageUri = data.getData();
            if (imageUri != null) {
                recognizeTextFromImage(imageUri);
            }
        }

        // 3. MỚI: Xử lý Chụp ảnh trực tiếp từ Camera
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            android.graphics.Bitmap imageBitmap = (android.graphics.Bitmap) extras.get("data");
            if (imageBitmap != null) {
                recognizeTextFromBitmap(imageBitmap);
            }
        }
    }

    private void recognizeTextFromBitmap(android.graphics.Bitmap bitmap) {
        // Chuyển Bitmap thành InputImage cho ML Kit
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        translatedText.setHint("Đang phân tích ảnh chụp...");

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String resultText = visionText.getText();
                    if (!resultText.isEmpty()) {
                        originalText.setText(resultText);
                        // Sau khi điền text, TextWatcher sẽ tự động gọi hàm dịch
                    } else {
                        Toast.makeText(this, "Không nhận diện được chữ", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void recognizeTextFromImage(android.net.Uri imageUri) {
        try {
            // Tạo đối tượng Image từ Uri
            InputImage image = InputImage.fromFilePath(this, imageUri);

            // Khởi tạo trình nhận diện (Sử dụng Latin làm mặc định)
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            translatedText.setHint("Đang phân tích hình ảnh...");

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String resultText = visionText.getText();
                        if (!resultText.isEmpty()) {
                            originalText.setText(resultText);
                            // Hàm TextWatcher sẽ tự kích hoạt dịch
                        } else {
                            Toast.makeText(this, "Không tìm thấy chữ", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE_RECORD && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startSpeechToText();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTranslator != null) {
            mTranslator.close();
        }
    }
}