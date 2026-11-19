package org.arm.learningpath.upscaling;
import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;

/** Identity and integrity validation before replacing any installed model. */
public final class ModelImporter {
    public static File installed(File dir, ModelProfile p) { return new File(dir, p.id + ".pte"); }
    public static File copy(ContentResolver resolver, Uri uri, File dir, ModelProfile p) throws Exception {
        File temp = File.createTempFile("import-", ".pte", dir);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total=0;
            try (InputStream in=resolver.openInputStream(uri); OutputStream out=new FileOutputStream(temp)) {
                if (in==null) throw new IOException("Cannot open the selected model.");
                byte[] buffer=new byte[65536]; int n;
                while ((n=in.read(buffer))!=-1) {
                    total+=n;
                    if (total>16*1024*1024) throw new IOException("This is not a supported model (file too large).");
                    digest.update(buffer,0,n); out.write(buffer,0,n);
                }
            }
            StringBuilder hash=new StringBuilder();
            for (byte b:digest.digest()) hash.append(String.format(java.util.Locale.ROOT,"%02x", b&255));
            if (p == null) {
                for (ModelProfile candidate : ModelProfile.ALL) {
                    if (hash.toString().equals(candidate.sha256)) { p = candidate; break; }
                }
                if (p == null) throw new IOException("Unsupported model. Import QuickSRNetSmall, SESR-M5, or RealESR-general-x4v3 from the supplied downloader.");
            }
            if (!hash.toString().equals(p.sha256)) throw new IOException("Model does not match " + p.name + ". Select its matching model, or download the pinned package again.");
            File dest=installed(dir,p);
            java.nio.file.Files.move(temp.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } finally { if (temp.exists() && !temp.delete()) temp.deleteOnExit(); }
    }
}
