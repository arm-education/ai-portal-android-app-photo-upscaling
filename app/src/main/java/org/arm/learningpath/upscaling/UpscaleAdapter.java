package org.arm.learningpath.upscaling;
import android.graphics.Bitmap;
import java.io.File;
public interface UpscaleAdapter extends AutoCloseable {
    interface Progress { void update(int done, int total); }
    void load(File file, ModelProfile profile) throws Exception;
    Bitmap upscale(Bitmap input, Progress progress) throws Exception;
    @Override void close();
}
