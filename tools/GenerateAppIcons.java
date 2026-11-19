package tools;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateAppIcons {

    public static void main(String[] args) {
        try {
            File sourceFile = new File("C:/Users/utsho/.gemini/antigravity/brain/ed31aac0-817c-4848-b21d-70e26244225c/.user_uploaded/media_1794796237994_6096fc35.jpg");
            if (!sourceFile.exists()) {
                System.err.println("Source file does not exist: " + sourceFile.getAbsolutePath());
                System.exit(1);
            }

            BufferedImage source = ImageIO.read(sourceFile);
            System.out.println("Loaded source image: " + source.getWidth() + "x" + source.getHeight());

            // Android res directory
            File resDir = new File("TownCrierBD/app/src/main/res");
            if (!resDir.exists()) {
                resDir = new File("app/src/main/res");
            }
            System.out.println("Target res dir: " + resDir.getAbsolutePath());

            // Dimensions: {name, iconSize, adaptiveFgSize}
            Object[][] densities = {
                {"mipmap-mdpi", 48, 108},
                {"mipmap-hdpi", 72, 162},
                {"mipmap-xhdpi", 96, 216},
                {"mipmap-xxhdpi", 144, 324},
                {"mipmap-xxxhdpi", 192, 432}
            };

            for (Object[] d : densities) {
                String folderName = (String) d[0];
                int iconSize = (Integer) d[1];
                int fgSize = (Integer) d[2];

                File folder = new File(resDir, folderName);
                if (!folder.exists()) folder.mkdirs();

                // 1. Delete old webp files if present
                new File(folder, "ic_launcher.webp").delete();
                new File(folder, "ic_launcher_round.webp").delete();

                // 2. Standard ic_launcher.png
                BufferedImage standard = resize(source, iconSize, iconSize, false);
                ImageIO.write(standard, "png", new File(folder, "ic_launcher.png"));

                // 3. Round ic_launcher_round.png
                BufferedImage round = resize(source, iconSize, iconSize, true);
                ImageIO.write(round, "png", new File(folder, "ic_launcher_round.png"));

                // 4. Adaptive ic_launcher_foreground.png (centered with safe margins)
                BufferedImage fg = createAdaptiveForeground(source, fgSize, 0.78);
                ImageIO.write(fg, "png", new File(folder, "ic_launcher_foreground.png"));

                System.out.println("Generated icons for " + folderName + " (icon=" + iconSize + "px, fg=" + fgSize + "px)");
            }

            System.out.println("All launcher icons generated successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static BufferedImage resize(BufferedImage src, int width, int height, boolean round) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (round) {
            g.setClip(new Ellipse2D.Float(0, 0, width, height));
        }

        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static BufferedImage createAdaptiveForeground(BufferedImage src, int size, double scale) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int targetW = (int) Math.round(size * scale);
        int targetH = (int) Math.round(size * scale);
        int x = (size - targetW) / 2;
        int y = (size - targetH) / 2;

        g.drawImage(src, x, y, targetW, targetH, null);
        g.dispose();
        return out;
    }
}
