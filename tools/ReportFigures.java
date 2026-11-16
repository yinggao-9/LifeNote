import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Draws the two report figures that are not test results:
 *
 *   java tools/ReportFigures.java
 *
 * Writes docs/report-figures/architecture.png
 *        docs/report-figures/pipeline.png
 */
public class ReportFigures {

    static final Color BLUE = new Color(0x4E, 0x79, 0xA7);
    static final Color LIGHT = new Color(0xDC, 0xE6, 0xF1);
    static final Color GREEN = new Color(0x59, 0xA1, 0x4F);
    static final Color LGREEN = new Color(0xDD, 0xEE, 0xD9);
    static final Color ORANGE = new Color(0xF2, 0x8E, 0x2B);
    static final Color LORANGE = new Color(0xFC, 0xE8, 0xD2);
    static final Color GREY = new Color(0x76, 0x76, 0x76);
    static final Color LGREY = new Color(0xEE, 0xEE, 0xEE);
    static final Color DARK = new Color(0x2B, 0x2B, 0x2B);

    public static void main(String[] args) throws Exception {
        Path out = Path.of("docs", "report-figures");
        Files.createDirectories(out);
        architecture(out.resolve("architecture.png"));
        pipeline(out.resolve("pipeline.png"));
        System.out.println("Figures written to " + out.toAbsolutePath());
    }

    // ---------- shared helpers ----------

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        return g;
    }

    static void box(Graphics2D g, int x, int y, int w, int h, String title, String sub,
                    Color fill, Color border) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(border);
        g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        center(g, title, x + w / 2, y + (sub == null ? h / 2 + 9 : h / 2 - 8));
        if (sub != null) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            center(g, sub, x + w / 2, y + h / 2 + 24);
        }
    }

    static void center(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy);
    }

    static void arrow(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setColor(DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int len = 14;
        Polygon head = new Polygon();
        head.addPoint(x2, y2);
        head.addPoint((int) (x2 - len * Math.cos(angle - 0.45)), (int) (y2 - len * Math.sin(angle - 0.45)));
        head.addPoint((int) (x2 - len * Math.cos(angle + 0.45)), (int) (y2 - len * Math.sin(angle + 0.45)));
        g.fillPolygon(head);
    }

    static void label(Graphics2D g, String text, int x, int y) {
        g.setColor(GREY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 19));
        g.drawString(text, x, y);
    }

    static void diamond(Graphics2D g, int cx, int cy, int w, int h, String text) {
        Polygon p = new Polygon();
        p.addPoint(cx, cy - h / 2);
        p.addPoint(cx + w / 2, cy);
        p.addPoint(cx, cy + h / 2);
        p.addPoint(cx - w / 2, cy);
        g.setColor(LORANGE);
        g.fillPolygon(p);
        g.setColor(ORANGE);
        g.setStroke(new BasicStroke(2.5f));
        g.drawPolygon(p);
        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 21));
        center(g, text, cx, cy + 7);
    }

    // ---------- Figure A: architecture ----------

    static void architecture(Path file) throws Exception {
        BufferedImage img = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        center(g, "LifeNote architecture", 800, 56);

        box(g, 350, 90, 900, 110,
                "React single-page application (Vite)",
                "Home  ·  Calendar  ·  Monthly summary  ·  Settings", LIGHT, BLUE);
        arrow(g, 800, 200, 800, 262);
        label(g, "HTTPS + JSON   ·   JWT bearer token   ·   31 REST endpoints", 855, 240);

        // API tier
        g.setColor(BLUE);
        g.fillRoundRect(120, 270, 950, 420, 16, 16);
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(120, 270, 950, 420, 16, 16);
        g.setColor(DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        center(g, "Spring Boot 4 REST API  (Java 21)", 595, 312);

        box(g, 165, 340, 400, 80, "JwtTokenInterceptor", "authorisation on every call", LGREY, GREY);
        box(g, 600, 340, 425, 80, "Controllers (8) · 31 endpoints", "auth · tasks · documents · boards", LGREY, GREY);
        box(g, 165, 455, 400, 80, "Service layer", "Document · Task · Board · Reminder", LGREY, GREY);
        box(g, 600, 455, 425, 80, "Summary · User · Auth services", "settings and account deletion", LGREY, GREY);
        box(g, 165, 570, 860, 80, "QuickAddParser · AiExtraction · PdfText · IcsExport",
                "pure helpers kept separate so they are unit-testable", LGREY, GREY);

        // side systems
        box(g, 1140, 300, 400, 130, "LLM providers",
                "GPT and DeepSeek via Spring AI", LORANGE, ORANGE);
        label(g, "one LlmChatService, automatic fallback", 1170, 462);
        arrow(g, 1070, 420, 1140, 380);
        box(g, 1140, 500, 400, 130, "AWS S3", "uploaded originals per user prefix", LGREEN, GREEN);
        label(g, "10-minute presigned URLs on demand", 1170, 662);
        arrow(g, 1070, 520, 1140, 560);

        // data tier
        arrow(g, 595, 690, 595, 762);
        label(g, "Spring Data JPA", 615, 740);
        box(g, 200, 770, 790, 110,
                "PostgreSQL 16  ·  Flyway V1–V3  ·  10 tables",
                "users · user_settings · documents · ai_runs · task_candidates · tasks", LIGHT, BLUE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        center(g, "task_documents · postponements · summary_llm_cache · reminders", 595, 906);

        ImageIO.write(img, "png", file.toFile());
    }

    // ---------- Figure B: document pipeline ----------

    static void pipeline(Path file) throws Exception {
        BufferedImage img = new BufferedImage(1600, 1050, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        center(g, "Document pipeline: from raw material to a confirmed task", 800, 56);

        box(g, 60, 110, 300, 110, "User input", "pasted text · PDF · photo", LIGHT, BLUE);
        arrow(g, 360, 165, 460, 165);
        box(g, 460, 110, 400, 110, "DocumentService.submit",
                "original stored in S3, text extracted", LIGHT, BLUE);
        arrow(g, 860, 165, 960, 165);
        box(g, 960, 110, 400, 110, "AiExtractionService.extract",
                "prompt v1.0, provider from settings", LIGHT, BLUE);
        label(g, "audit row in ai_runs (model, raw output, duration)", 990, 246);

        arrow(g, 1160, 220, 1160, 300);
        diamond(g, 1160, 360, 320, 110, "Valid JSON array?");

        // no branch
        arrow(g, 1000, 360, 860, 360);
        label(g, "no", 940, 340);
        box(g, 420, 310, 430, 100, "Retry once on the other provider",
                "GPT fails, DeepSeek tried, or the reverse", LORANGE, ORANGE);
        arrow(g, 635, 410, 635, 470);
        diamond(g, 635, 530, 300, 100, "Retry worked?");
        arrow(g, 785, 530, 935, 527);
        label(g, "yes", 840, 512);
        arrow(g, 485, 530, 410, 548);
        label(g, "no", 430, 512);
        box(g, 60, 500, 340, 95, "Document status FAILED",
                "upload kept, reextract available", LORANGE, ORANGE);

        // yes branch
        arrow(g, 1160, 415, 1160, 475);
        label(g, "yes", 1180, 455);
        box(g, 940, 480, 440, 95, "task_candidates drafts",
                "status PENDING, nothing is a task yet", LGREEN, GREEN);
        arrow(g, 1160, 575, 1160, 635);
        diamond(g, 1160, 700, 330, 110, "User decision");

        arrow(g, 995, 700, 855, 700);
        label(g, "cancel", 900, 680);
        box(g, 420, 655, 430, 95, "Drafts deleted", "cancelling saves nothing", LGREY, GREY);

        arrow(g, 1160, 755, 1160, 815);
        label(g, "confirm", 1180, 795);
        box(g, 940, 820, 440, 95, "Task + task_documents link",
                "drafts marked CONFIRMED, source kept", LGREEN, GREEN);
        arrow(g, 1160, 915, 1160, 950);
        box(g, 850, 950, 620, 85, "Task views and feedback loop",
                "Board · Calendar · .ics · Reminders · Summary", LIGHT, BLUE);

        ImageIO.write(img, "png", file.toFile());
    }
}
