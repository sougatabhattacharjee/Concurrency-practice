import com.google.common.collect.Lists;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by sougata on 08.05.17.
 */
public class ThumbNailMaker {

    private final static double OUTPUT_IMAGE_SCALE_RATIO = 0.08;
    private final static String OUTPUT_IMAGE_FORMAT = "jpg";

    // Another way to convert an image to its thumbnail
    private void convertImageToThumbnail1(final List<File> sourceImages, final String outputDir) throws IOException {
        sourceImages.forEach(
                image -> {
                    try {
                        Thumbnails.of(image)
                                .size(100, 80)
                                .outputFormat(OUTPUT_IMAGE_FORMAT)
                                .toFiles(new File(outputDir), Rename.PREFIX_DOT_THUMBNAIL);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    private void convertImageToThumbnail(final List<File> sourceImages, final String outputDir) throws IOException {
        sourceImages.forEach(image -> {
            BufferedImage readImage;
            try {
                readImage = ImageIO.read(image);
                ImageIO.write(scaleImage(readImage, OUTPUT_IMAGE_SCALE_RATIO), OUTPUT_IMAGE_FORMAT,
                        new File(outputDir + "/" + image.getName()));
            } catch (final IOException e) {
                e.printStackTrace();
            }
        });
    }

    private BufferedImage scaleImage(final BufferedImage source, final double ratio) {
        final int width = (int) (source.getWidth() * ratio);
        final int height = (int) (source.getHeight() * ratio);
        final BufferedImage bi = getCompatibleImage(width, height);
        final Graphics2D g2d = bi.createGraphics();
        final double xScale = (double) width / source.getWidth();
        final double yScale = (double) height / source.getHeight();
        final AffineTransform at = AffineTransform.getScaleInstance(xScale, yScale);
        g2d.drawRenderedImage(source, at);
        g2d.dispose();
        return bi;
    }

    private BufferedImage getCompatibleImage(final int width, final int height) {
        final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice gd = ge.getDefaultScreenDevice();
        final GraphicsConfiguration gc = gd.getDefaultConfiguration();
        return gc.createCompatibleImage(width, height);
    }

    public void parallelExecution(final List<List<File>> partitions, final String outputDir, final Integer threadCount) {
        final Collection<Callable<Void>> tasks = new ArrayList<>();
        partitions.forEach(list -> tasks.add(new Task(list, outputDir)));
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            final List<Future<Void>> results = executor.invokeAll(tasks);
            results.forEach(result -> System.out.println(result.isDone()));
        } catch (final InterruptedException ex) {
            System.out.println("Interrupted executing of Callable");
        } finally {
            executor.shutdown(); //always reclaim resources
        }
    }

    private final class Task implements Callable<Void> {
        List<File> images = Lists.newArrayList();
        String outputDir;

        Task(final List<File> images, final String outputDir) {
            this.images = images;
            this.outputDir = outputDir;
        }

        public Void call() throws Exception {
            final ThumbNailMaker thumbNailMaker = new ThumbNailMaker();
            thumbNailMaker.convertImageToThumbnail(images, outputDir);
            return null;
        }
    }
}