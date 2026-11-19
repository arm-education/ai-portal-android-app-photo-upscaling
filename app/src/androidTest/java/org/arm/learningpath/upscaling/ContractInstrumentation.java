package org.arm.learningpath.upscaling;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.net.Uri;
import java.io.*;

/** Real native-runtime integration checks. Test weights are NOT in the app APK. */
public class ContractInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
 private void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);}
 @Override public void onStart() {
  StringBuilder log=new StringBuilder(); Bundle result=new Bundle();
  try(UpscaleAdapter adapter=new ExecuTorchUpscaleAdapter()) {
   File dir=new File(getTargetContext().getCacheDir(),"contract-tests");dir.mkdirs();
   for(ModelProfile p:ModelProfile.ALL) {
    File source=new File(dir,"source-"+p.id+".pte");
    try(InputStream in=getContext().getAssets().open(p.id+".pte");OutputStream out=new FileOutputStream(source)) {byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
    File installed=ModelImporter.copy(getTargetContext().getContentResolver(),Uri.fromFile(source),dir,p);
    adapter.load(installed,p);
    for(int[] size:new int[][]{{96,64},{249,133},{63,257},{512,512}}) {
     Bitmap input=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
     Canvas c=new Canvas(input);c.drawColor(Color.rgb(25,90,180));Paint paint=new Paint();paint.setColor(Color.rgb(230,40,60));c.drawRect(7,11,35,43,paint);
     int[] last={-1};
     Bitmap output=adapter.upscale(input,(done,total)->{check(done>=last[0] && total>0,"progress order");last[0]=done;});
     check(output.getWidth()==size[0]*4 && output.getHeight()==size[1]*4,"dimensions");
     check(output.getPixel(80,80)!=output.getPixel(output.getWidth()-10,output.getHeight()-10),"image detail / input dependence");
     File saved=new File(dir,p.id+"-"+size[0]+".png");
     try(OutputStream out=getTargetContext().getContentResolver().openOutputStream(Uri.fromFile(saved))) {check(output.compress(Bitmap.CompressFormat.PNG,100,out),"PNG encode");}
     Bitmap decoded=BitmapFactory.decodeFile(saved.toString());check(decoded!=null && output.sameAs(decoded),"saved PNG round trip");
     log.append(p.name+" "+size[0]+"x"+size[1]+" -> "+output.getWidth()+"x"+output.getHeight()+" PASS\n");
     input.recycle();output.recycle();decoded.recycle();
    }
    adapter.close();
    try {ModelImporter.copy(getTargetContext().getContentResolver(),Uri.fromFile(source),dir,ModelProfile.ALL[(java.util.Arrays.asList(ModelProfile.ALL).indexOf(p)+1)%3]);throw new AssertionError("wrong profile accepted");} catch(IOException expected) {}
   }
   File broken=new File(dir,"broken.pte");try(FileOutputStream out=new FileOutputStream(broken)){out.write(new byte[]{1,2,3});}
   try {ModelImporter.copy(getTargetContext().getContentResolver(),Uri.fromFile(broken),dir,ModelProfile.ALL[0]);throw new AssertionError("corruption accepted");} catch(IOException expected) {}
   // Switching back to the first model proves a released native runtime can be reopened.
   adapter.load(ModelImporter.installed(dir,ModelProfile.ALL[0]),ModelProfile.ALL[0]);
   log.append("PASS all imports, wrong-profile/corruption rejection, native CPU inference, switching, detail variation, dimensions, progress, PNG round trip\n");
   DetailGestureChecks.verify(this);
   log.append("PASS multi-touch pinch changes rendered detail; toggle, reset, and drag pan\n");
   result.putString("stream",log.toString());finish(Activity.RESULT_OK,result);
  } catch(Throwable e) {result.putString("stream",log+"FAIL "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
 }
}
