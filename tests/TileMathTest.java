package org.arm.learningpath.upscaling;
/** Run with the JDK; no Android dependencies. Proves complete finite coverage. */
public class TileMathTest {
 public static void main(String[] args) {
  int cases=0;
  for(int n:new int[]{1,7,127,128,129,133,239,240,241,249,257,511,512}) {
   int[] pos=TileMath.positions(n,128,8);
   float[] weights=new float[n*4];
   for(int k=0;k<pos.length;k++) for(int i=0;i<512 && pos[k]*4+i<weights.length;i++)
    weights[pos[k]*4+i]+=TileMath.ramp(i,512,32,k==0,k==pos.length-1);
   for(float w:weights) if(!Float.isFinite(w)||w<=0) throw new AssertionError("coverage "+n);
   if(pos[0]!=0 || (n>128 && pos[pos.length-1]!=n-128)) throw new AssertionError("edge");
   cases++;
  }
  if(TileMath.channel(-1)!=0 || TileMath.channel(2)!=255 || TileMath.channel(.5f)!=127) throw new AssertionError("conversion");
  try {TileMath.channel(Float.NaN);throw new AssertionError("NaN accepted");} catch(IllegalStateException expected){}
  System.out.println("PASS "+cases+" edge/overlap coverage cases; clamping and NaN rejection");
 }
}
