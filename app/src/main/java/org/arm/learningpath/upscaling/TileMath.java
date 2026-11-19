package org.arm.learningpath.upscaling;
import java.util.ArrayList;
/** Pure coordinate/feather math, shared by all independently checked profiles. */
public final class TileMath {
    public static int[] positions(int length, int tile, int overlap) {
        ArrayList<Integer> p=new ArrayList<>();
        p.add(0);
        while (p.get(p.size()-1)+tile<length) {
            int next=Math.min(p.get(p.size()-1)+tile-overlap,length-tile);
            p.add(next);
        }
        int[] result=new int[p.size()]; for(int i=0;i<result.length;i++) result[i]=p.get(i);
        return result;
    }
    public static float ramp(int pixel, int size, int feather, boolean first, boolean last) {
        float w=1;
        if(!first && pixel<feather) w=(float)pixel/(feather-1);
        if(!last && pixel>=size-feather) w=Math.min(w,(float)(size-1-pixel)/(feather-1));
        return w;
    }
    public static int channel(float value) {
        if (!Float.isFinite(value)) throw new IllegalStateException("Model returned non-finite pixels.");
        return (int)(Math.max(0,Math.min(1,value))*255); // matches torchvision to_pil_image truncation
    }
}
