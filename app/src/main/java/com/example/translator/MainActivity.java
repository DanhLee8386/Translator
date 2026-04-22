package com.example.translator;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText originalText, translatedText;
    private FloatingActionButton btnMicro, btnPicture, btnCamera;
    private TextView tvSourceLang, tvTargetLang;
    private ImageButton btnSwap;
    private MaterialSwitch switchOfflineMode;
    private MaterialButton btnDownloadModel;

    private Translator mTranslator;
    private boolean isOfflineMode = false;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    private static final int PERMISSION_CODE_RECORD = 1;
    private static final int REQUEST_CODE_PICK_IMAGE = 2000;
    private static final int REQUEST_CODE_CAMERA = 3000;

    private String sourceLangCode = TranslateLanguage.ENGLISH;
    private String targetLangCode = TranslateLanguage.VIETNAMESE;

    private final String[] languageNames = {"Tiếng Anh", "Tiếng Việt", "Tiếng Nhật", "Tiếng Hàn", "Tiếng Pháp", "Tiếng Trung"};
    private final String[] languageCodes = {
            TranslateLanguage.ENGLISH, TranslateLanguage.VIETNAMESE, TranslateLanguage.JAPANESE,
            TranslateLanguage.KOREAN, TranslateLanguage.FRENCH, TranslateLanguage.CHINESE
    };

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
        btnPicture = findViewById(R.id.btnPicture);
        btnCamera = findViewById(R.id.btnCamera);
        switchOfflineMode = findViewById(R.id.switchOfflineMode);
        btnDownloadModel = findViewById(R.id.btnDownloadModel);

        btnMicro.setOnClickListener(v -> checkPermissionAndStartSpeech());
        btnSwap.setOnClickListener(v -> swapLanguages());
        tvSourceLang.setOnClickListener(v -> showLanguageDialog(true));
        tvTargetLang.setOnClickListener(v -> showLanguageDialog(false));
        btnPicture.setOnClickListener(v -> openGallery());
        btnCamera.setOnClickListener(v -> checkCameraPermission());

        switchOfflineMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isOfflineMode = isChecked;
            if (isOfflineMode) {
                checkIfModelsDownloaded();
            } else {
                btnDownloadModel.setVisibility(View.GONE);
            }
            triggerTranslation();
        });

        btnDownloadModel.setOnClickListener(v -> downloadOfflineModels());
    }

    private void checkIfModelsDownloaded() {
        RemoteModelManager modelManager = RemoteModelManager.getInstance();
        TranslateRemoteModel sourceModel = new TranslateRemoteModel.Builder(sourceLangCode).build();
        TranslateRemoteModel targetModel = new TranslateRemoteModel.Builder(targetLangCode).build();

        Task<Boolean> sourceTask = modelManager.isModelDownloaded(sourceModel);
        Task<Boolean> targetTask = modelManager.isModelDownloaded(targetModel);

        Tasks.whenAllComplete(sourceTask, targetTask).addOnCompleteListener(task -> {
            boolean isSourceDownloaded = sourceTask.isSuccessful() && sourceTask.getResult();
            boolean isTargetDownloaded = targetTask.isSuccessful() && targetTask.getResult();

            if (isOfflineMode && (!isSourceDownloaded || !isTargetDownloaded)) {
                btnDownloadModel.setVisibility(View.VISIBLE);
                translatedText.setHint("Cần tải mô hình để dịch Offline");
            } else {
                btnDownloadModel.setVisibility(View.GONE);
                translatedText.setHint("Bản dịch sẽ hiện ở đây...");
            }
        });
    }

    private void downloadOfflineModels() {
        btnDownloadModel.setEnabled(false);
        btnDownloadModel.setText("Đang tải...");

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        mTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    btnDownloadModel.setVisibility(View.GONE);
                    btnDownloadModel.setEnabled(true);
                    btnDownloadModel.setText("Tải mô hình Offline");
                    Toast.makeText(this, "Tải mô hình thành công", Toast.LENGTH_SHORT).show();
                    triggerTranslation();
                })
                .addOnFailureListener(e -> {
                    btnDownloadModel.setEnabled(true);
                    btnDownloadModel.setText("Tải lại mô hình");
                    Toast.makeText(this, "Lỗi tải mô hình: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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

        if (isOfflineMode) {
            checkIfModelsDownloaded();
        }
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
            triggerTranslation();
        });
        builder.show();
    }

    private void swapLanguages() {
        String tempCode = sourceLangCode;
        sourceLangCode = targetLangCode;
        targetLangCode = tempCode;

        String txtSource = tvSourceLang.getText().toString();
        String txtTarget = tvTargetLang.getText().toString();
        tvSourceLang.setText(txtTarget);
        tvTargetLang.setText(txtSource);

        String currentOriginal = originalText.getText().toString();
        String currentTranslated = translatedText.getText().toString();
        originalText.setText(currentTranslated);
        translatedText.setText(currentOriginal);

        setupTranslator();
        triggerTranslation();
    }

    private void setupTextWatcher() {
        originalText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerTranslation();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void triggerTranslation() {
        String input = originalText.getText().toString().trim();
        if (input.isEmpty()) {
            translatedText.setText("");
            return;
        }

        if (isOfflineMode) {
            performOfflineTranslation(input);
        } else {
            performOnlineTranslation(input);
        }
    }

    private void performOfflineTranslation(String text) {
        if (btnDownloadModel.getVisibility() == View.VISIBLE) {
            translatedText.setText("Vui lòng tải mô hình trước khi dịch offline.");
            return;
        }

        mTranslator.translate(text)
                .addOnSuccessListener(result -> translatedText.setText(result))
                .addOnFailureListener(e -> translatedText.setText("Lỗi dịch Offline..."));
    }
    private void performOnlineTranslation(String text) {
        // translatedText là EditText trong layout của bạn
        translatedText.setHint("Đang dịch Online...");

        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                // URL khớp với @RequestMapping("/api/translations") và @PostMapping("/translate")
                URL url = new URL("http://10.0.2.2:8080/api/translate");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                // Tạo JSON body khớp với TranslationRequestDTO {text, source, target}
                JSONObject jsonInput = new JSONObject();
                jsonInput.put("sourceText", text);
                jsonInput.put("sourceLangId", sourceLangCode);
                jsonInput.put("targetLangId", targetLangCode);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    try (java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream(), "UTF-8")) {
                        String response = scanner.useDelimiter("\\A").next();
                        JSONObject jsonResponse = new JSONObject(response);

                        // Lấy field "translatedText" từ TranslationResponseDTO
                        String result = jsonResponse.getString("targetText");

                        mainHandler.post(() -> translatedText.setText(result));
                    }
                } else {
                    mainHandler.post(() -> translatedText.setText("Lỗi Backend: " + code));
                }
            } catch (Exception e) {
                mainHandler.post(() -> translatedText.setText("Lỗi kết nối: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2);
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

    private void checkPermissionAndStartSpeech() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CODE_RECORD);
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
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                originalText.setText(result.get(0));
            }
        }
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            android.net.Uri imageUri = data.getData();
            if (imageUri != null) recognizeTextFromImage(imageUri);
        }
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            android.graphics.Bitmap imageBitmap = (android.graphics.Bitmap) extras.get("data");
            if (imageBitmap != null) recognizeTextFromBitmap(imageBitmap);
        }
    }

    private void recognizeTextFromBitmap(android.graphics.Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        processImageForText(image);
    }

    private void recognizeTextFromImage(android.net.Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            processImageForText(image);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void processImageForText(InputImage image) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        translatedText.setHint("Đang phân tích...");
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String resultText = visionText.getText();
                    if (!resultText.isEmpty()) {
                        originalText.setText(resultText);
                    } else {
                        Toast.makeText(this, "Không nhận diện được chữ", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
        networkExecutor.shutdown();
    }
}