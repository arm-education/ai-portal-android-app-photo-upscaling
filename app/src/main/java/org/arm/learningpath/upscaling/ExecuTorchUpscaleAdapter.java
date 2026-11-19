package org.arm.learningpath.upscaling;
import android.graphics.Bitmap;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.DType;
import java.io.File;
import java.util.Arrays;

/** Fixed-size RGB tiles, replicate padding, normalized feather accumulation. */
public final class ExecuTorchUpscaleAdapter implements UpscaleAdapter {
    private Module module;
    private ModelProfile p;
    @Override public void load(File file, ModelProfile profile) {
        close(); p=profile;
        try {
            module=Module.load(file.getAbsolutePath(), Module.LOAD_MODE_MMAP);
            if (!Arrays.asList(module.getMethods()).contains("forward")) throw new IllegalArgumentException("Missing forward method.");
            module.loadMethod("forward");
            if (!Arrays.asList(module.getMethodMetadata("forward").getBackends()).contains("XnnpackBackend")) throw new IllegalArgumentException("Model requires the XNNPACK CPU backend.");
            infer(new float[3*p.tile*p.tile]); // validate output and prime runtime outside processing timing
        } catch (RuntimeException e) { close(); throw e; }
    }
    private float[] infer(float[] input) {
        EValue[] values=module.execute("forward", EValue.from(Tensor.fromBlob(input,new long[]{1,3,p.tile,p.tile})));
        if(values==null || values.length!=1 || !values[0].isTensor()) throw new IllegalStateException("Expected one RGB output tensor.");
        Tensor t=values[0].toTensor(); int side=p.tile*p.scale;
        if(t.dtype()!=DType.FLOAT || !Arrays.equals(t.shape(),new long[]{1,3,side,side})) throw new IllegalStateException("Unsupported output tensor: " + Arrays.toString(t.shape()));
        return t.getDataAsFloatArray();
    }
    @Override public Bitmap upscale(Bitmap input, Progress progress) throws InterruptedException {
        if(module==null) throw new IllegalStateException("Import a model first.");
        int w=input.getWidth(), h=input.getHeight(), ow=w*p.scale, oh=h*p.scale;
        // Bounded input prevents huge allocation from a phone photograph. No silent resizing.
        if(w>512 || h>512) throw new IllegalArgumentException("Choose a photo at most 512 × 512 pixels. Resize it before importing.");
        int[] pixels=new int[w*h]; input.getPixels(pixels,0,w,0,0,w,h);
        int area=ow*oh, side=p.tile*p.scale, tileArea=side*side;
        float[] sum=new float[area*3], weights=new float[area];
        int[] xs=TileMath.positions(w,p.tile,p.overlap), ys=TileMath.positions(h,p.tile,p.overlap);
        int done=0; progress.update(0,xs.length*ys.length);
        for(int yi=0;yi<ys.length;yi++) for(int xi=0;xi<xs.length;xi++) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedException("Upscaling cancelled.");
            int x=xs[xi], y=ys[yi];
            float[] rgb=new float[3*p.tile*p.tile]; int plane=p.tile*p.tile;
            for(int ty=0;ty<p.tile;ty++) for(int tx=0;tx<p.tile;tx++) {
                int c=pixels[Math.min(h-1,y+ty)*w+Math.min(w-1,x+tx)], i=ty*p.tile+tx;
                rgb[i]=((c>>16)&255)/255f; rgb[plane+i]=((c>>8)&255)/255f; rgb[2*plane+i]=(c&255)/255f;
            }
            float[] out=infer(rgb);
            for(int ty=0;ty<side && y*p.scale+ty<oh;ty++) {
                float wy=TileMath.ramp(ty,side,p.overlap*p.scale,yi==0,yi==ys.length-1);
                for(int tx=0;tx<side && x*p.scale+tx<ow;tx++) {
                    float weight=wy*TileMath.ramp(tx,side,p.overlap*p.scale,xi==0,xi==xs.length-1);
                    int dest=(y*p.scale+ty)*ow+x*p.scale+tx, src=ty*side+tx;
                    weights[dest]+=weight;
                    for(int c=0;c<3;c++) {
                        float v=out[c*tileArea+src];
                        if(!Float.isFinite(v)) throw new IllegalStateException("Model returned non-finite pixels.");
                        sum[c*area+dest]+=v*weight;
                    }
                }
            }
            progress.update(++done,xs.length*ys.length);
        }
        int[] result=new int[area];
        for(int i=0;i<area;i++) {
            if(weights[i]<=0) throw new IllegalStateException("Uncovered output pixel.");
            result[i]=0xff000000 | TileMath.channel(sum[i]/weights[i])<<16 | TileMath.channel(sum[area+i]/weights[i])<<8 | TileMath.channel(sum[2*area+i]/weights[i]);
        }
        return Bitmap.createBitmap(result,ow,oh,Bitmap.Config.ARGB_8888);
    }
    @Override public void close() { if(module!=null) { module.close(); module=null; } }
}
