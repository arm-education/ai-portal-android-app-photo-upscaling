package org.arm.learningpath.upscaling;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.*;
import android.view.*;

/** Dispatch real multi-pointer events through the view's ScaleGestureDetector. */
public final class DetailGestureChecks {
    private static MotionEvent event(long down, int offset, int action, float... xs) {
        int count=xs.length;
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i;
            properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=600;
            coords[i].pressure=1;coords[i].size=1;
        }
        return MotionEvent.obtain(down,down+offset,action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    private static Bitmap render(DetailView view) {
        Bitmap b=Bitmap.createBitmap(1080,1600,Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(b));return b;
    }
    private static void send(DetailView view, MotionEvent e) {view.onTouchEvent(e);e.recycle();}
    public static void verify(Instrumentation instrumentation) {
            instrumentation.runOnMainSync(()->{
                Bitmap input=Bitmap.createBitmap(96,64,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(input);canvas.drawColor(Color.BLUE);
                Paint p=new Paint();p.setColor(Color.RED);canvas.drawRect(10,10,30,30,p);
                Bitmap output=Bitmap.createScaledBitmap(input,384,256,true);
                DetailView view=new DetailView(instrumentation.getTargetContext(),input,output);view.layout(0,0,1080,1600);
                Bitmap before=render(view);long down=SystemClock.uptimeMillis();
                send(view,event(down,0,MotionEvent.ACTION_DOWN,400));
                send(view,event(down,20,MotionEvent.ACTION_POINTER_DOWN | 1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT,400,600));
                send(view,event(down,40,MotionEvent.ACTION_MOVE,390,610));
                send(view,event(down,60,MotionEvent.ACTION_MOVE,200,800));
                send(view,event(down,80,MotionEvent.ACTION_MOVE,100,900));
                send(view,event(down,100,MotionEvent.ACTION_POINTER_UP | 1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT,100,900));
                send(view,event(down,120,MotionEvent.ACTION_UP,100));
                Bitmap zoomed=render(view);
                if(before.sameAs(zoomed)) throw new AssertionError("Pinch did not change rendered detail");
                view.toggle();render(view);view.toggle();view.reset();
                if(!before.sameAs(render(view))) throw new AssertionError("Reset did not restore fitted image");
                down=SystemClock.uptimeMillis();
                send(view,event(down,0,MotionEvent.ACTION_DOWN,400));
                send(view,event(down,20,MotionEvent.ACTION_MOVE,650));
                send(view,event(down,40,MotionEvent.ACTION_UP,650));
                if(before.sameAs(render(view))) throw new AssertionError("Drag did not pan image");
            });
    }
}
