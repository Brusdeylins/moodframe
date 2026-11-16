import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/*  Maps a rectilinear square photo onto a circular fisheye image: the circle edge reaches the sides of the
    source, the angle grows linearly with the radius (equidistant), so the center bulges and the edges squeeze  */
public class Fisheye {
    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        int size = Integer.parseInt(args[2]);
        double maxAngle = Math.toRadians(Double.parseDouble(args[3]));
        int w = src.getWidth();
        int h = src.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double srcRadius = Math.min(cx, cy) - 1;
        double radius = size / 2.0 - 8;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x + 0.5 - size / 2.0;
                double dy = y + 0.5 - size / 2.0;
                double r = Math.hypot(dx, dy) / radius;
                if (r > 1) {
                    out.setRGB(x, y, 0);
                    continue;
                }
                double rs = srcRadius * Math.tan(r * maxAngle) / Math.tan(maxAngle);
                double scale = r == 0 ? 0 : rs / (r * radius);
                double sx = cx + dx * scale;
                double sy = cy + dy * scale;
                out.setRGB(x, y, sample(src, sx, sy));
            }
        }
        ImageIO.write(out, "png", new File(args[1]));
    }

    /*  bilinear sample, clamped to the image  */
    private static int sample(BufferedImage img, double x, double y) {
        int x0 = (int) Math.floor(x - 0.5);
        int y0 = (int) Math.floor(y - 0.5);
        double fx = x - 0.5 - x0;
        double fy = y - 0.5 - y0;
        int[] c = { 0, 0, 0 };
        double[] acc = { 0, 0, 0 };
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                int px = Math.max(0, Math.min(img.getWidth() - 1, x0 + i));
                int py = Math.max(0, Math.min(img.getHeight() - 1, y0 + j));
                int rgb = img.getRGB(px, py);
                double wgt = (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
                acc[0] += ((rgb >> 16) & 0xff) * wgt;
                acc[1] += ((rgb >> 8) & 0xff) * wgt;
                acc[2] += (rgb & 0xff) * wgt;
            }
        }
        for (int k = 0; k < 3; k++)
            c[k] = (int) Math.round(acc[k]);
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }
}
