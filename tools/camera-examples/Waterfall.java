import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/*  Puts the waterfall and river of the base photo (F2) into a stage image and renders the shutter physically:
    the outline stays, fast shutter thins the water into crisp jets, slow shutter smears it only along the fall  */
public class Waterfall {
    static final int FX0 = 560, FX1 = 790, FY0 = 850, FY1 = 1230;
    static final int RX0 = 430, RX1 = 720, RY0 = 1120, RY1 = 1400;
    static final int FEATHER = 40;

    public static void main(String[] args) throws Exception {
        BufferedImage base = ImageIO.read(new File(args[0]));
        BufferedImage stage = ImageIO.read(new File(args[1]));
        String mode = args[2];
        int blur = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        float[][][] src = rgb(base);
        float[][][] fall = src;
        float[][] alpha = null;
        if (mode.equals("frozen")) {

            /*  thin and sharpen only the water and a few px around it, so the rock keeps its texture  */
            fall = sharpen(thin(src));
            int hh = src.length, ww = src[0].length;
            alpha = new float[hh][ww];
            for (int y = FY0; y < FY1; y++)
                for (int x = FX0; x < FX1; x++) {
                    float best = 0;
                    for (int dy = -3; dy <= 3; dy++)
                        for (int dx = -3; dx <= 3; dx++) {
                            float[] p = src[Math.max(0, Math.min(hh - 1, y + dy))][Math.max(0, Math.min(ww - 1, x + dx))];
                            float lum = 0.3f * p[0] + 0.59f * p[1] + 0.11f * p[2];
                            best = Math.max(best, Math.max(0, Math.min(1, (lum - 120) / 60)));
                        }
                    alpha[y][x] = best;
                }
        }
        else if (mode.equals("blur")) {

            /*  only the bright water streaks, normalized so it keeps its color; the rock behind stays as it is  */
            int hh = src.length, ww = src[0].length;
            float[][] m = new float[hh][ww];
            float[][][] wm = new float[hh][ww][3];
            for (int y = FY0; y < FY1; y++)
                for (int x = FX0; x < FX1; x++) {
                    float lum = 0.3f * src[y][x][0] + 0.59f * src[y][x][1] + 0.11f * src[y][x][2];
                    m[y][x] = Math.max(0, Math.min(1, (lum - 120) / 60));
                    for (int k = 0; k < 3; k++)
                        wm[y][x][k] = src[y][x][k] * m[y][x];
                }
            float[][] bm = vblur1(m, blur);
            float[][][] bw = new float[hh][ww][3];
            for (int k = 0; k < 3; k++) {
                float[][] ch = new float[hh][ww];
                for (int y = 0; y < hh; y++)
                    for (int x = 0; x < ww; x++)
                        ch[y][x] = wm[y][x][k];
                float[][] bc = vblur1(ch, blur);
                for (int y = 0; y < hh; y++)
                    for (int x = 0; x < ww; x++)
                        bw[y][x][k] = bm[y][x] > 0.001f ? bc[y][x] / bm[y][x] : src[y][x][k];
            }
            alpha = new float[hh][ww];
            for (int x = FX0; x < FX1; x++) {
                int top = FY1;
                for (int y = FY0; y < FY1; y++)
                    if (m[y][x] > 0.5f) { top = y; break; }
                for (int y = top; y < FY1; y++)
                    alpha[y][x] = Math.min(1, Math.max(bm[y][x], m[y][x]));
            }
            fall = bw;
        }
        int w = stage.getWidth();
        int h = stage.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int s = stage.getRGB(x, y);
                double[] c = { (s >> 16) & 0xff, (s >> 8) & 0xff, s & 0xff };
                double mf = mask(x, y, FX0, FX1, FY0, FY1);
                double mr = mask(x, y, RX0, RX1, RY0, RY1);
                double a = alpha == null ? 1 : alpha[y][x];
                for (int k = 0; k < 3; k++) {
                    c[k] = c[k] * (1 - Math.max(mr, mf)) + src[y][x][k] * Math.max(mr, mf);
                    c[k] = c[k] * (1 - mf * a) + fall[y][x][k] * mf * a;
                }
                out.setRGB(x, y, (clamp(c[0]) << 16) | (clamp(c[1]) << 8) | clamp(c[2]));
            }
        ImageIO.write(out, "png", new File(args[3]));
    }

    static double mask(int x, int y, int x0, int x1, int y0, int y1) {
        double dx = Math.min(x - x0, x1 - x), dy = Math.min(y - y0, y1 - y);
        double d = Math.min(dx, dy);
        return d <= 0 ? 0 : Math.min(1, d / FEATHER);
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static float[][][] rgb(BufferedImage img) {
        float[][][] a = new float[img.getHeight()][img.getWidth()][3];
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                a[y][x][0] = (p >> 16) & 0xff;
                a[y][x][1] = (p >> 8) & 0xff;
                a[y][x][2] = p & 0xff;
            }
        return a;
    }

    /*  box blur along y of one channel inside the waterfall box  */
    static float[][] vblur1(float[][] a, int len) {
        int h = a.length, w = a[0].length;
        float[][] b = new float[h][w];
        for (int x = FX0; x < FX1; x++)
            for (int y = FY0; y < FY1; y++) {
                float sum = 0;
                int n = 0;
                for (int t = -len / 2; t <= len / 2; t++) {
                    int yy = y + t;
                    if (yy < FY0 || yy >= FY1)
                        continue;
                    sum += a[yy][x];
                    n++;
                }
                b[y][x] = sum / n;
            }
        return b;
    }

    /*  box blur along y only: water streaks along its fall, the outline keeps its width  */
    static float[][][] vertical(float[][][] a, int len) {
        int h = a.length, w = a[0].length;
        float[][][] b = new float[h][w][3];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                for (int k = 0; k < 3; k++) {
                    float sum = 0;
                    int n = 0;
                    for (int t = -len / 2; t <= len / 2; t++) {
                        int yy = y + t;
                        if (yy < FY0 || yy >= FY1)
                            continue;
                        sum += a[yy][x][k];
                        n++;
                    }
                    b[y][x][k] = n > 0 ? sum / n : a[y][x][k];
                }
        return b;
    }

    /*  local minimum over 3x1 px: bright water loses its soft fringe, dark gaps between jets open up  */
    static float[][][] thin(float[][][] a) {
        int h = a.length, w = a[0].length;
        float[][][] b = new float[h][w][3];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int k = 0; k < 3; k++) {
                    float m = a[y][x][k];
                    for (int t = -2; t <= 2; t++)
                        m = Math.min(m, a[y][Math.max(0, Math.min(w - 1, x + t))][k]);
                    b[y][x][k] = 0.65f * m + 0.35f * a[y][x][k];
                }
        return b;
    }

    /*  unsharp mask: crisper edges of the jets  */
    static float[][][] sharpen(float[][][] a) {
        int h = a.length, w = a[0].length;
        float[][][] b = new float[h][w][3];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int k = 0; k < 3; k++) {
                    float sum = 0;
                    int n = 0;
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dx = -2; dx <= 2; dx++) {
                            int yy = Math.max(0, Math.min(h - 1, y + dy)), xx = Math.max(0, Math.min(w - 1, x + dx));
                            sum += a[yy][xx][k];
                            n++;
                        }
                    b[y][x][k] = a[y][x][k] + 2.4f * (a[y][x][k] - sum / n);
                }
        return b;
    }
}
