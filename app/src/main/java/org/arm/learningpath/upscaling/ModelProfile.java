package org.arm.learningpath.upscaling;

/** Explicit profiles for inspected artifacts, not model-family guesses. */
public final class ModelProfile {
    public final String id, name, sha256;
    public final int tile, scale, overlap;
    private ModelProfile(String id, String name, String hash, int tile, int scale, int overlap) {
        this.id=id; this.name=name; sha256=hash;
        this.tile=tile; this.scale=scale; this.overlap=overlap;
    }
    public static final ModelProfile[] ALL = {
        new ModelProfile("quicksr", "QuickSRNetSmall", "6dac47949ee9cc513d9c577e55823cef4d2eaa8d69edbd8643aa92497f8c351f", 128, 4, 8),
        new ModelProfile("sesr", "SESR-M5", "b9e3f53508d77f3a187562b8e902cb4d84d12a53dcb8f0b166182baf403b18a6", 128, 4, 8),
        new ModelProfile("realesr", "RealESR-general-x4v3", "9b8823cfbf5137283bee704d2d721e90502060f4d6ce41470ec9739fa0bd4304", 128, 4, 8)
    };
    @Override public String toString() { return name + " · 4× · ExecuTorch CPU"; }
}
