package android.content.res;
/** Test stand-in: reads assets from the directory given by -Dassets=... */
public class AssetManager {
    public java.io.InputStream open(String p) throws java.io.IOException {
        return new java.io.FileInputStream(System.getProperty("assets") + "/" + p);
    }
}
