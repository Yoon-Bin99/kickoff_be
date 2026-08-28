import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 카카오톡 카드형 공유에 쓰는 브랜딩 이미지(share-card.png)를 만든다.
 *
 * <b>이 파일은 빌드에 들어가지 않는다.</b> src/main/java 밖에 있어서 Gradle 이 컴파일하지
 * 않고, 산출물인 PNG 만 static 으로 커밋된다. 그런데도 소스를 남기는 이유는, 배경색 하나
 * 바꾸려 할 때 "이 그림이 어떻게 만들어졌는지 아무도 모르는" 상태를 피하기 위해서다.
 *
 * 도형과 색은 FE 저장소의 로고 원본(kickoff_fe/assets/logo-drafts/01-ball-sunrise.svg)에서
 * 그대로 가져왔다 — 좌표와 색값이 그 SVG 와 일치해야 앱 아이콘과 같은 마크로 보인다.
 * 그 SVG 가 1024 정사각형이라, 800x400 카드에 맞게 공 크기와 지평선 높이만 다시 잡았다.
 *
 * 다시 만들려면:
 *   java tools/share-card/ShareCard.java src/main/resources/static/share-card.png
 */
public final class ShareCard {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 400;

    // 원본 SVG 의 색 그대로다. 바꾸면 앱 아이콘과 공유 카드의 색이 갈린다.
    private static final Color GREEN_TOP = new Color(0x14, 0x60, 0x3A);
    private static final Color GREEN_BOTTOM = new Color(0x1B, 0x7F, 0x4B);
    private static final Color GRASS_NEAR = new Color(0x14, 0x60, 0x3A);
    private static final Color GRASS_FAR = new Color(0x0F, 0x4C, 0x2E);

    /** 공의 반지름. 원본의 210/1024 비율을 카드 높이에 맞춰 줄인 값이다. */
    private static final double BALL_RADIUS = 96;
    private static final double BALL_CX = 196;
    private static final double BALL_CY = 178;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "share-card.png");

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        background(g);
        sun(g);
        grass(g);
        ball(g);
        wordmark(g);

        g.dispose();
        ImageIO.write(image, "png", out);
        System.out.println("wrote " + out.getAbsolutePath() + " (" + out.length() + " bytes)");
    }

    private static void background(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, GREEN_TOP, 0, HEIGHT, GREEN_BOTTOM));
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    /** 공 뒤로 떠오르는 해. 원본과 같이 흰색을 아주 옅게 두 겹 겹친다. */
    private static void sun(Graphics2D g) {
        g.setColor(new Color(255, 255, 255, 26));
        fillCircle(g, BALL_CX, BALL_CY + 62, 152);
        g.setColor(new Color(255, 255, 255, 31));
        fillCircle(g, BALL_CX, BALL_CY + 62, 110);
    }

    /** 잔디 지평선. 카드가 가로로 길어 원본보다 완만하게 눕혔다. */
    private static void grass(Graphics2D g) {
        g.setColor(GRASS_NEAR);
        g.fill(wave(300, 284, 316));
        g.setColor(GRASS_FAR);
        g.fill(wave(332, 316, 348));
    }

    private static Path2D wave(double start, double crest, double trough) {
        Path2D path = new Path2D.Double();
        path.moveTo(0, start);
        path.quadTo(WIDTH * 0.25, crest, WIDTH * 0.5, start);
        path.quadTo(WIDTH * 0.75, trough, WIDTH, start);
        path.lineTo(WIDTH, HEIGHT);
        path.lineTo(0, HEIGHT);
        path.closePath();
        return path;
    }

    /**
     * 공. 원본 SVG 의 좌표(반지름 210 기준)를 그대로 쓰고 배율만 곱한다 — 오각형 꼭짓점과
     * 이음선 길이의 비율이 마크의 인상을 만들기 때문에 눈대중으로 다시 그리지 않는다.
     */
    private static void ball(Graphics2D g) {
        double s = BALL_RADIUS / 210.0;

        g.setColor(Color.WHITE);
        fillCircle(g, BALL_CX, BALL_CY, BALL_RADIUS);

        double[][] pentagon = {{0, -96}, {91, -30}, {56, 78}, {-56, 78}, {-91, -30}};
        Path2D center = new Path2D.Double();
        center.moveTo(BALL_CX + pentagon[0][0] * s, BALL_CY + pentagon[0][1] * s);
        for (int i = 1; i < pentagon.length; i++) {
            center.lineTo(BALL_CX + pentagon[i][0] * s, BALL_CY + pentagon[i][1] * s);
        }
        center.closePath();
        g.setColor(GREEN_TOP);
        g.fill(center);

        double[][] seams = {{0, -96, 0, -190}, {91, -30, 181, -59}, {56, 78, 112, 155},
                {-56, 78, -112, 155}, {-91, -30, -181, -59}};
        g.setStroke(new BasicStroke((float) (20 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] seam : seams) {
            g.draw(new java.awt.geom.Line2D.Double(
                    BALL_CX + seam[0] * s, BALL_CY + seam[1] * s,
                    BALL_CX + seam[2] * s, BALL_CY + seam[3] * s));
        }
    }

    /**
     * 이름과 한 줄 설명. 카카오 카드는 제목·설명을 따로 싣지만, 이미지만 잘려 보이는
     * 자리(피드 미리보기)가 있어 이미지 안에도 이름이 있어야 무엇인지 알 수 있다.
     *
     * 한글이 들어가므로 맑은 고딕을 쓴다. 없는 환경이면 논리 폰트로 떨어져 자모가 깨지므로
     * 여기서 바로 실패시킨다 — 깨진 글씨로 조용히 만들어지는 쪽이 나쁘다.
     */
    private static void wordmark(Graphics2D g) throws Exception {
        Font bold = load("C:/Windows/Fonts/malgunbd.ttf");
        Font regular = load("C:/Windows/Fonts/malgun.ttf");

        g.setColor(Color.WHITE);
        g.setFont(bold.deriveFont(Font.PLAIN, 88f));
        g.drawString("Kickoff", 344, 196);

        g.setColor(new Color(255, 255, 255, 224));
        g.setFont(regular.deriveFont(Font.PLAIN, 31f));
        // "조기축구 팀 매칭" — 이스케이프로 적는 이유는 한 번 당했기 때문이다. 이 파일은
        // UTF-8 인데 java 단일 파일 실행은 소스를 플랫폼 기본 인코딩(한국어 윈도우에서는
        // CP949)으로 읽는다. 그대로 두면 예외 없이 글자만 깨진 PNG 가 만들어지고, 눈으로
        // 열어보기 전에는 아무도 모른다. \\u 이스케이프는 소스 인코딩과 무관하게 해석된다.
        g.drawString("\uc870\uae30\ucd95\uad6c \ud300 \ub9e4\uce6d", 348, 246);
    }

    private static Font load(String path) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            throw new IllegalStateException("폰트를 찾을 수 없습니다: " + path
                    + " — 한글이 깨진 이미지가 만들어지는 것을 막으려고 중단합니다");
        }
        return Font.createFont(Font.TRUETYPE_FONT, file);
    }

    private static void fillCircle(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }
}
