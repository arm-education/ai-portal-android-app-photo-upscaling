package org.arm.learningpath.upscaling;

// Shared screen/workflow adapted from Photo Insight, revision 761054faad49394f95d8f1eec8d5c428eaddaf08.
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int IMPORT_MODEL_REQUEST = 100;
    private static final int OPEN_IMAGE_REQUEST = 101;
    private static final int SAVE_IMAGE_REQUEST = 102;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final UpscaleAdapter runner = new ExecuTorchUpscaleAdapter();
    private ModelProfile selectedProfile = ModelProfile.ALL[0];
    private boolean ready;
    private volatile boolean destroyed;
    private Bitmap selectedBitmap;
    private Bitmap outputBitmap;
    private ImageView imagePreview;
    private ImageView outputPreview;
    private TextView modelStatus;
    private TextView modelExplanation;
    private TextView results;
    private ProgressBar progress;
    private Button importModel;
    private Button chooseImage;
    private Button runModel;
    private Button inspectDetail;
    private Button saveImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imagePreview = findViewById(R.id.image_preview);
        imagePreview.setClipToOutline(true);
        outputPreview = findViewById(R.id.output_preview);
        outputPreview.setClipToOutline(true);
        modelStatus = findViewById(R.id.model_status);
        modelExplanation = findViewById(R.id.model_explanation);
        results = findViewById(R.id.results);
        progress = findViewById(R.id.progress);
        importModel = findViewById(R.id.import_model);
        chooseImage = findViewById(R.id.choose_image);
        runModel = findViewById(R.id.run_model);
        inspectDetail = findViewById(R.id.inspect_detail);
        saveImage = findViewById(R.id.save_image);
        String storedProfile = getPreferences(MODE_PRIVATE).getString("active_model", "");
        for (ModelProfile profile : ModelProfile.ALL) {
            if (profile.id.equals(storedProfile)) selectedProfile = profile;
        }
        importModel.setOnClickListener(view -> openModelPicker());
        chooseImage.setOnClickListener(view -> openImagePicker());
        runModel.setOnClickListener(view -> runInference());
        inspectDetail.setOnClickListener(view -> openDetail());
        saveImage.setOnClickListener(view -> openSavePicker());
        loadStoredModel();
    }

    private void loadStoredModel() {
        ready = false;
        applyModeState();
        setBusy(getString(R.string.loading_model));
        ModelProfile profile = selectedProfile;
        executor.execute(() -> {
            runner.close();
            File file = ModelImporter.installed(getFilesDir(), profile);
            if (!file.isFile()) {
                post(() -> { results.setText(R.string.initial_instructions); setBusy(false); });
                return;
            }
            try {
                runner.load(file, profile);
                post(() -> modelReady(profile));
            } catch (Exception | OutOfMemoryError error) {
                runner.close();
                postError(R.string.model_import_failed, error);
            }
        });
    }

    private void openModelPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        startActivityForResult(intent, IMPORT_MODEL_REQUEST);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_IMAGE_REQUEST);
    }

    private void openSavePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "upscaled-" + selectedProfile.id + ".png");
        startActivityForResult(intent, SAVE_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == IMPORT_MODEL_REQUEST) importSelectedModel(data.getData());
        else if (requestCode == OPEN_IMAGE_REQUEST) loadSelectedImage(data.getData());
        else if (requestCode == SAVE_IMAGE_REQUEST) saveSelectedImage(data.getData());
    }

    private void importSelectedModel(Uri modelUri) {
        ready = false;
        clearOutput();
        setBusy(getString(R.string.importing_model));
        executor.execute(() -> {
            try {
                runner.close();
                File file = ModelImporter.copy(getContentResolver(), modelUri, getFilesDir(), null);
                ModelProfile profile = null;
                for (ModelProfile candidate : ModelProfile.ALL) {
                    if (file.equals(ModelImporter.installed(getFilesDir(), candidate))) profile = candidate;
                }
                if (profile == null) throw new IOException("Unsupported model.");
                final ModelProfile imported = profile;
                runner.load(file, profile);
                post(() -> modelReady(imported));
            } catch (Exception | OutOfMemoryError error) {
                runner.close();
                postError(R.string.model_import_failed, error);
            }
        });
    }

    private void modelReady(ModelProfile profile) {
        selectedProfile = profile;
        getPreferences(MODE_PRIVATE).edit().putString("active_model", profile.id).apply();
        ready = true;
        applyModeState();
        results.setText(getString(R.string.upscale_ready_result, profile.name));
        setBusy(false);
    }

    private void loadSelectedImage(Uri imageUri) {
        setBusy(getString(R.string.opening_photo));
        executor.execute(() -> {
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), imageUri),
                        (decoder, info, source) -> {
                            if (info.getSize().getWidth() > 512 || info.getSize().getHeight() > 512)
                                throw new IllegalArgumentException(getString(R.string.photo_limit));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                        }
                );
                Bitmap rgb = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(rgb);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(bitmap, 0, 0, null);
                bitmap.recycle();
                post(() -> {
                    replaceSelectedBitmap(rgb);
                    clearOutput();
                    results.setText(R.string.image_ready);
                    setBusy(false);
                });
            } catch (Exception | OutOfMemoryError error) {
                postError(R.string.image_open_failed, error);
            }
        });
    }

    private void replaceSelectedBitmap(Bitmap bitmap) {
        if (selectedBitmap != null && selectedBitmap != bitmap && !selectedBitmap.isRecycled()) {
            selectedBitmap.recycle();
        }
        selectedBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        imagePreview.setContentDescription(getString(R.string.image_dimensions,
                getString(R.string.your_photo), bitmap.getWidth(), bitmap.getHeight()));
    }

    private void runInference() {
        if (!ready) { showError(getString(R.string.import_before_running)); return; }
        if (selectedBitmap == null) { showError(getString(R.string.choose_before_running)); return; }
        clearOutput();
        setBusy(getString(R.string.tile_progress, 0, 1));
        Bitmap input = selectedBitmap;
        ModelProfile profile = selectedProfile;
        executor.execute(() -> {
            try {
                long start = SystemClock.elapsedRealtimeNanos();
                Bitmap output = runner.upscale(input, (done, total) -> post(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(total);
                    progress.setProgress(done);
                    results.setText(getString(R.string.tile_progress, done, total));
                }));
                double elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6;
                post(() -> {
                    outputBitmap = output;
                    outputPreview.setImageBitmap(output);
                    outputPreview.setVisibility(View.VISIBLE);
                    outputPreview.setContentDescription(getString(R.string.image_dimensions,
                            getString(R.string.output_image), output.getWidth(), output.getHeight()));
                    inspectDetail.setVisibility(View.VISIBLE);
                    saveImage.setVisibility(View.VISIBLE);
                    results.setText(getString(R.string.result_summary, profile.name,
                            input.getWidth(), input.getHeight(), output.getWidth(), output.getHeight(), elapsed));
                    setBusy(false);
                });
            } catch (Exception | OutOfMemoryError error) {
                postError(R.string.inference_failed, error);
            }
        });
    }

    private void clearOutput() {
        outputPreview.setImageDrawable(null);
        outputPreview.setVisibility(View.GONE);
        inspectDetail.setVisibility(View.GONE);
        saveImage.setVisibility(View.GONE);
        if (outputBitmap != null && !outputBitmap.isRecycled()) outputBitmap.recycle();
        outputBitmap = null;
    }

    private void applyModeState() {
        if (!ready) {
            modelStatus.setText(R.string.model_not_imported);
            modelExplanation.setText(R.string.upscale_missing);
        } else {
            modelStatus.setText(selectedProfile.name);
            modelExplanation.setText(R.string.upscale_ready);
        }
        updateControls(false);
    }

    private void setBusy(String message) {
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        results.setText(message);
        updateControls(true);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        updateControls(busy);
    }

    private void updateControls(boolean busy) {
        importModel.setEnabled(!busy);
        chooseImage.setEnabled(!busy);
        runModel.setEnabled(!busy && ready && selectedBitmap != null);
        inspectDetail.setEnabled(!busy && outputBitmap != null);
        saveImage.setEnabled(!busy && outputBitmap != null);
    }

    private void showError(String message) {
        setBusy(false);
        results.setText(message == null ? getString(R.string.inference_failed, "Unknown error") : message);
    }

    private void post(Runnable action) {
        runOnUiThread(() -> { if (!destroyed) action.run(); });
    }

    private void postError(int resource, Throwable error) {
        post(() -> showError(getString(resource, error instanceof OutOfMemoryError
                ? getString(R.string.out_of_memory) : error.getMessage())));
    }

    private void saveSelectedImage(Uri uri) {
        Bitmap bitmap = outputBitmap;
        if (bitmap == null) return;
        CharSequence summary = results.getText();
        setBusy(getString(R.string.saving_image));
        executor.execute(() -> {
            try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IOException("Cannot write PNG. Choose a writable location.");
                stream.flush();
                post(() -> { results.setText(summary + "\n" + getString(R.string.saved_image)); setBusy(false); });
            } catch (Exception error) { postError(R.string.save_failed, error); }
        });
    }

    private void openDetail() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPixels(18), dpToPixels(18), dpToPixels(18), dpToPixels(18));
        root.setBackgroundResource(R.drawable.bg_screen);
        TextView help = new TextView(this);
        help.setText(R.string.detail_help);
        help.setTextColor(getColor(R.color.ink));
        help.setTextSize(16);
        root.addView(help);
        DetailView detail = new DetailView(this, selectedBitmap, outputBitmap);
        root.addView(detail, new LinearLayout.LayoutParams(-1, 0, 1));
        Button toggle = detailButton(root, R.string.show_original);
        toggle.setOnClickListener(new View.OnClickListener() {
            boolean showingOutput = true;
            @Override public void onClick(View view) {
                detail.toggle(); showingOutput = !showingOutput;
                toggle.setText(showingOutput ? R.string.show_original : R.string.show_upscaled);
            }
        });
        detailButton(root, R.string.reset_zoom).setOnClickListener(view -> detail.reset());
        detailButton(root, R.string.close_detail).setOnClickListener(view -> dialog.dismiss());
        dialog.setContentView(root);
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private Button detailButton(LinearLayout root, int label) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false);
        button.setTextColor(getColor(R.color.teal));
        button.setTypeface(button.getTypeface(), android.graphics.Typeface.BOLD);
        button.setBackgroundResource(R.drawable.bg_secondary_button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dpToPixels(50));
        params.topMargin = dpToPixels(12); root.addView(button, params);
        return button;
    }

    private int dpToPixels(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.execute(runner::close);
        executor.shutdown();
        super.onDestroy();
    }
}
