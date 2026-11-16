import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/*  Copies the lower river course from the base photo into a stage image: the valley left of the dress, bounded on
    the right by a slanted line just left of the dress hem, with a soft edge  */
public class RiverLeft {
    static final int Y0 = 1150, Y1 = 1800, FEATHER = 20;

    /*  the right border of the region at a row: from x 440 at the top to x 360 at the bottom  */
    static double border(int y) {
        return 440 - 80.0 * (y - Y0) / (Y1 - Y0);
    }

    public static void main(String[] args) throws Exception {
        BufferedImage base = ImageIO.read(new File(args[0]));
        BufferedImage stage = ImageIO.read(new File(args[1]));
        int w = stage.getWidth(), h = stage.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int s = stage.getRGB(x, y), p = base.getRGB(x, y);
                double d = Math.min(Math.min(y - Y0, Y1 - y), border(y) - x);
                double a = d <= 0 ? 0 : Math.min(1, d / FEATHER);
                int[] c = new int[3];
                for (int k = 0; k < 3; k++) {
                    int shift = 16 - 8 * k;
                    c[k] = (int) Math.round(((s >> shift) & 0xff) * (1 - a) + ((p >> shift) & 0xff) * a);
                }
                out.setRGB(x, y, (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        ImageIO.write(out, "png", new File(args[2]));
    }
}
