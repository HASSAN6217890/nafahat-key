import com.nafahat.key.AutoCorrect;
import com.nafahat.key.Dictionary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Runs the real AutoCorrect policy against tools/autocorrect_cases.txt. Exit 1 on any failure. */
public class AutoCorrectTest {
    public static void main(String[] a) throws Exception {
        Dictionary ar = Dictionary.load(new android.content.res.AssetManager(), "dict/ar.txt", true);
        LinkedHashSet<String> keep = new LinkedHashSet<>();
        List<String[]> fix = new ArrayList<>();
        String section = "";
        for (String line : Files.readAllLines(Paths.get(a[0]), StandardCharsets.UTF_8)) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.equals("[keep]") || l.equals("[fix]")) { section = l; continue; }
            if (section.equals("[keep]")) for (String w : l.split("\\s+")) keep.add(w);
            else fix.add(l.split("\t"));
        }
        int bad = 0, badKeep = 0;
        for (String w : keep) {
            String r = AutoCorrect.arabic(ar, w, x -> false);
            if (r != null) { bad++; badKeep++; System.out.println("WRONGLY CORRECTED: " + w + " -> " + r); }
        }
        int fixed = 0;
        for (String[] f : fix) {
            String r = AutoCorrect.arabic(ar, f[0], x -> false);
            if (f[1].equals(r)) fixed++;
            else { bad++; System.out.println("NOT FIXED: " + f[0] + " -> " + r + " (expected " + f[1] + ")"); }
        }
        System.out.println("Java policy — keep: " + (keep.size() - badKeep) + "/" + keep.size()
            + " untouched; fix: " + fixed + "/" + fix.size() + " corrected");
        if (bad > 0) System.exit(1);
    }
}
