# The native bridge resolves this callback by its Java method name.
# Keep both the SAM contract and generated implementation method stable in minified apps.
-keep interface com.hartmann.pixeldream.diffusion.StableDiffusion$NativeProgressListener { *; }
-keepclassmembers class * implements com.hartmann.pixeldream.diffusion.StableDiffusion$NativeProgressListener {
    public void onProgress(int, int);
}
