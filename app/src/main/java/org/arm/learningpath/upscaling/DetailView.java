package org.arm.learningpath.upscaling;
import android.content.Context;
import android.graphics.*;
import android.view.*;

/** The two bitmaps share normalized scene coordinates, zoom, and pan. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed with two result bitmaps; never inflated from XML.
public final class DetailView extends View {
    private final Bitmap before, after;
    private boolean showAfter=true;
    private float zoom=1, panX=0, panY=0, lastX, lastY;
    private final RectF destination=new RectF();
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector pinch;
    public DetailView(Context context, Bitmap before, Bitmap after) {
        super(context); this.before=before; this.after=after;
        setContentDescription("Photo detail. Pinch to zoom and drag to pan. Toggle original and upscaled to compare the same area.");
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d) { zoom=Math.max(1,Math.min(16,zoom*d.getScaleFactor())); invalidate(); return true; }
        });
    }
    public void toggle() { showAfter=!showAfter; invalidate(); }
    public void reset() { zoom=1; panX=panY=0; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(20,25,29));
        Bitmap b=showAfter?after:before;
        float fit=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());
        float w=b.getWidth()*fit*zoom, h=b.getHeight()*fit*zoom;
        panX=Math.max(-w/2,Math.min(w/2,panX)); panY=Math.max(-h/2,Math.min(h/2,panY));
        float x=(getWidth()-w)/2+panX, y=(getHeight()-h)/2+panY;
        destination.set(x,y,x+w,y+h);
        canvas.drawBitmap(b,null,destination,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        pinch.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) { lastX=e.getX(); lastY=e.getY(); }
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE && !pinch.isInProgress() && e.getPointerCount()==1) { panX+=e.getX()-lastX; panY+=e.getY()-lastY; invalidate(); }
        lastX=e.getX(); lastY=e.getY();
        if(e.getActionMasked()==MotionEvent.ACTION_UP) performClick();
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
