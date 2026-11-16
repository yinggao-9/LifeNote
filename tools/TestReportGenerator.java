import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the test-suite results and the JaCoCo coverage CSV into PNG charts
 * for the interim report. Zero dependencies beyond the JDK (java.desktop +
 * java.xml), so it runs straight from the source file:
 *
 *   java tools/TestReportGenerator.java
 *
 * Reads  target/surefire-reports/TEST-*.xml   (per-class results)
 *        target/site/jacoco/jacoco.csv        (line coverage per class)
 * Writes docs/test-report/test-distribution.png
 *        docs/test-report/tests-per-class.png
 *        docs/test-report/pass-rate.png
 *        docs/test-report/coverage-by-package.png
 */
public class TestReportGenerator {

    // ---------- palette ----------
    static final Color BLUE   = new Color(0x4E, 0x79, 0xA7);   // unit
    static final Color GREEN  = new Color(0x59, 0xA1, 0x4F);   // integration
    static final Color ORANGE = new Color(0xF2, 0x8E, 0x2B);   // security
    static final Color RED    = new Color(0xE1, 0x57, 0x59);   // malicious intent
    static final Color GREY   = new Color(0xB0, 0xB0, 0xB0);
    static final Color DARK   = new Color(0x33, 0x33, 0x33);
    static final Color BG     = Color.WHITE;

    // ---------- data ----------
    record ClassResult(String name, int tests, int failures, int errors, int skipped,
                       List<String> caseNames) {
        int passed() { return tests - failures - errors - skipped; }
    }

    public static void main(String[] args) throws Exception {
        Path surefire = Path.of("target", "surefire-reports");
        Path jacocoCsv = Path.of("target", "site", "jacoco", "jacoco.csv");
        Path outDir = Path.of("docs", "test-report");
        Files.createDirectories(outDir);

        List<ClassResult> results = readSurefire(surefire);
        if (results.isEmpty()) {
            System.err.println("No TEST-*.xml found under " + surefire.toAbsolutePath());
            return;
        }

        int total = results.stream().mapToInt(ClassResult::tests).sum();
        int failed = results.stream().mapToInt(r -> r.failures + r.errors).sum();
        int skipped = results.stream().mapToInt(ClassResult::skipped).sum();
        System.out.printf("Classes: %d  tests: %d  failed: %d  skipped: %d%n",
                results.size(), total, failed, skipped);

        Map<String, Integer> byCategory = categorize(results);
        byCategory.forEach((k, v) -> System.out.printf("  %-22s %d%n", k, v));
        writePie(outDir.resolve("test-distribution.png"),
                "Test cases by category (n = " + total + ")", byCategory);
        writeBarPerClass(outDir.resolve("tests-per-class.png"),
                "Test cases per class", results);
        writeDonut(outDir.resolve("pass-rate.png"),
                "Overall result", total, total - failed - skipped, failed, skipped);

        if (Files.exists(jacocoCsv)) {
            Map<String, double[]> cov = readCoverage(jacocoCsv);   // pkg -> [missed, covered]
            writeCoverageBars(outDir.resolve("coverage-by-package.png"),
                    "Line coverage by package (JaCoCo)", cov);
            int miss = 0, cover = 0;
            for (double[] v : cov.values()) { miss += v[0]; cover += v[1]; }
            System.out.printf("Overall line coverage: %.1f%% (%d of %d lines)%n",
                    100.0 * cover / (miss + cover), (int) cover, (int) (miss + cover));
        } else {
            System.err.println("jacoco.csv not found - skipping coverage chart");
        }
        System.out.println("Charts written to " + outDir.toAbsolutePath());
    }

    // ---------- input ----------

    static List<ClassResult> readSurefire(Path dir) throws Exception {
        List<ClassResult> out = new ArrayList<>();
        File[] files = dir.toFile().listFiles((d, n) -> n.startsWith("TEST-") && n.endsWith(".xml"));
        if (files == null) return out;
        for (File f : files) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
            Element suite = doc.getDocumentElement();
            String name = suite.getAttribute("name");
            String simple = name.substring(name.lastIndexOf('.') + 1);
            List<String> cases = new ArrayList<>();
            NodeList nodes = suite.getElementsByTagName("testcase");
            for (int i = 0; i < nodes.getLength(); i++) {
                cases.add(((Element) nodes.item(i)).getAttribute("name"));
            }
            out.add(new ClassResult(simple,
                    Integer.parseInt(suite.getAttribute("tests")),
                    Integer.parseInt(suite.getAttribute("failures")),
                    Integer.parseInt(suite.getAttribute("errors")),
                    Integer.parseInt(suite.getAttribute("skipped")),
                    cases));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    static Map<String, double[]> readCoverage(Path csv) throws IOException {
        Map<String, double[]> byPackage = new LinkedHashMap<>();
        for (String line : Files.readAllLines(csv)) {
            if (line.startsWith("GROUP,")) continue;
            String[] cols = line.split(",");
            if (cols.length < 8) continue;
            String pkg = cols[1].replace("come.ying.lifenote", "lifenote");
            double missed = Double.parseDouble(cols[7]);   // LINE_MISSED
            double covered = Double.parseDouble(cols[8]);  // LINE_COVERED
            double[] slot = byPackage.computeIfAbsent(pkg, k -> new double[2]);
            slot[0] += missed;
            slot[1] += covered;
        }
        return byPackage;
    }

    // ---------- categorisation ----------
    // Unit test classes run without Spring; the rest boot the whole system
    // against a real PostgreSQL container.
    static final List<String> UNIT_CLASSES = List.of(
            "JwtUtilsTest", "QuickAddParserTest", "AiExtractionServiceTest",
            "LlmChatServiceTest", "ReminderServiceTest", "SummaryServiceTest",
            "IcsExportServiceTest", "PdfTextExtractorTest");

    static Map<String, Integer> categorize(List<ClassResult> results) {
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        byCategory.put("Unit", 0);
        byCategory.put("Integration / system", 0);
        byCategory.put("Security", 0);
        byCategory.put("Malicious intent", 0);
        for (ClassResult r : results) {
            for (String c : r.caseNames()) {
                byCategory.merge(categoryOf(r.name(), c), 1, Integer::sum);
            }
        }
        return byCategory;
    }

    static String categoryOf(String cls, String test) {
        if (UNIT_CLASSES.contains(cls)) return "Unit";
        String n = test.toLowerCase(Locale.ROOT);
        if (n.contains("hostile") || n.contains("injection") || n.contains("oversiz")
                || n.contains("unsupported") || n.contains("garbage") || n.contains("tamper")
                || n.contains("forg") || n.contains("expired") || n.contains("sneaky")
                || n.contains("hijack") || n.contains("foreign") || n.contains("leak")
                || n.contains("anotherusercannot") || n.contains("rejectsbad")
                || n.contains("rejectsweak") || n.contains("byhand")) {
            return "Malicious intent";
        }
        if (cls.contains("Security") || n.contains("token") || n.contains("password")
                || n.contains("login") || n.contains("register") || n.contains("account")
                || n.contains("delet") || n.contains("owner") || n.contains("profile")) {
            return "Security";
        }
        return "Integration / system";
    }

    // ---------- drawing helpers ----------

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        return g;
    }

    static void title(Graphics2D g, String text, int w) {
        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        int tw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (w - tw) / 2, 52);
    }

    static void writePie(Path out, String caption, Map<String, Integer> values) throws IOException {
        BufferedImage img = new BufferedImage(1200, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        title(g, caption, 1200);

        int total = values.values().stream().mapToInt(Integer::intValue).sum();
        double angle = 90.0;                       // start at twelve o'clock
        int cx = 380, cy = 440, r = 280;
        Color[] colors = {BLUE, GREEN, ORANGE, RED, GREY};
        int i = 0;
        List<String> legend = new ArrayList<>();
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            double sweep = 360.0 * e.getValue() / Math.max(1, total);
            g.setColor(colors[i % colors.length]);
            g.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, angle, -sweep, Arc2D.PIE));
            g.setColor(BG);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, angle, -sweep, Arc2D.PIE));
            legend.add(String.format("%s  %d  (%.0f%%)", e.getKey(), e.getValue(),
                    100.0 * e.getValue() / Math.max(1, total)));
            angle -= sweep;
            i++;
        }
        // legend
        int y = 300;
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        for (int j = 0; j < legend.size(); j++) {
            g.setColor(colors[j % colors.length]);
            g.fill(new Rectangle2D.Double(760, y - 22, 30, 30));
            g.setColor(DARK);
            g.drawString(legend.get(j), 810, y);
            y += 56;
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    static void writeDonut(Path out, String caption, int total, int passed, int failed, int skipped)
            throws IOException {
        BufferedImage img = new BufferedImage(1200, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        title(g, caption, 1200);

        int cx = 600, cy = 440, r = 260, inner = 150;
        double angle = 90.0;
        int[][] segs = {{passed, 0}, {failed, 1}, {skipped, 2}};
        Color[] colors = {GREEN, RED, GREY};
        String[] names = {"passed", "failed", "skipped"};
        for (int[] seg : segs) {
            if (seg[0] == 0) continue;
            double sweep = 360.0 * seg[0] / Math.max(1, total);
            g.setColor(colors[seg[1]]);
            g.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, angle, -sweep, Arc2D.PIE));
            angle -= sweep;
        }
        g.setColor(BG);
        g.fillOval(cx - inner, cy - inner, 2 * inner, 2 * inner);

        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 64));
        String big = passed + " / " + total;
        g.drawString(big, cx - g.getFontMetrics().stringWidth(big) / 2, cy + 10);
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        String sub = "100% pass rate";
        if (failed > 0) sub = String.format("%.0f%% pass rate", 100.0 * passed / Math.max(1, total));
        g.drawString(sub, cx - g.getFontMetrics().stringWidth(sub) / 2, cy + 55);

        int y = 760;
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        int x = 330;
        for (int i = 0; i < names.length; i++) {
            int count = i == 0 ? passed : i == 1 ? failed : skipped;
            g.setColor(colors[i]);
            g.fill(new Rectangle2D.Double(x, y - 22, 28, 28));
            g.setColor(DARK);
            g.drawString(names[i] + " " + count, x + 42, y);
            x += 240;
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    static void writeBarPerClass(Path out, String caption, List<ClassResult> results) throws IOException {
        int rows = results.size();
        int h = 120 + rows * 52;
        BufferedImage img = new BufferedImage(1200, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        title(g, caption, 1200);

        int max = results.stream().mapToInt(ClassResult::tests).max().orElse(1);
        int labelW = 330, chartW = 700, x0 = labelW + 40, y0 = 110;
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        for (int i = 0; i < rows; i++) {
            ClassResult r = results.get(i);
            int y = y0 + i * 52;
            g.setColor(DARK);
            g.drawString(r.name(), 20, y + 30);
            int w = (int) (chartW * r.tests() / (double) max);
            g.setColor(BLUE);
            g.fill(new Rectangle2D.Double(x0, y + 8, w, 32));
            g.setColor(DARK);
            g.drawString(String.valueOf(r.tests()), x0 + w + 12, y + 32);
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    static void writeCoverageBars(Path out, String caption, Map<String, double[]> cov) throws IOException {
        List<Map.Entry<String, double[]>> rows = new ArrayList<>(cov.entrySet());
        rows.sort((a, b) -> Double.compare(pct(b.getValue()), pct(a.getValue())));
        int h = 140 + rows.size() * 52;
        BufferedImage img = new BufferedImage(1200, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        title(g, caption, 1200);

        int labelW = 330, chartW = 620, x0 = labelW + 40, y0 = 110;
        // 80% target line
        int xTarget = x0 + (int) (chartW * 0.8);
        g.setColor(GREY);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{8f, 8f}, 0f));
        g.drawLine(xTarget, y0 - 10, xTarget, y0 + rows.size() * 52);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString("80%", xTarget - 12, y0 - 18);

        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<String, double[]> row = rows.get(i);
            int y = y0 + i * 52;
            double p = pct(row.getValue());
            g.setColor(DARK);
            g.drawString(row.getKey(), 20, y + 30);
            int w = (int) (chartW * p / 100.0);
            g.setColor(p >= 80 ? GREEN : p >= 50 ? ORANGE : RED);
            g.fill(new Rectangle2D.Double(x0, y + 8, w, 32));
            g.setColor(DARK);
            g.drawString(String.format(Locale.ROOT, "%.0f%%", p), x0 + w + 12, y + 32);
        }
        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    static double pct(double[] missedCovered) {
        double total = missedCovered[0] + missedCovered[1];
        return total == 0 ? 100.0 : 100.0 * missedCovered[1] / total;
    }
}
