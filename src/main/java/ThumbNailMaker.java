import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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

    private final static int THREAD_COUNT_DEFAULT = 3;
    private final static String[] SOURCE_IMAGE_FORMATS = {"jpg", "png", "jpeg", "bmp"};
    private final static double OUTPUT_IMAGE_SCALE_RATIO = 0.08;
    private final static String OUTPUT_IMAGE_FORMAT = "jpg";

    private List<File> traverseImages(final String sourceDir) {
        final List<File> sourceImages = Lists.newArrayList();
        final Path source = Paths.get(sourceDir);
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(source, getImageExtensionPattern())) {
            files.forEach(image -> sourceImages.add(image.toFile()));
        } catch (final DirectoryIteratorException | IOException ex) {
            ex.printStackTrace();
        }
        return sourceImages;
    }

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

    private String getImageExtensionPattern() {
        return "*.{" + String.join(",", SOURCE_IMAGE_FORMATS) + "}";
    }

    private void parallelExecution(final List<List<File>> partitions, final String outputDir, final Integer threadCount) {
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

    private boolean isDirectoryEmpty(final String sourceDirectory) {
        final File sourceDir = new File(sourceDirectory);
        if (sourceDir.exists() && sourceDir.isDirectory()) {
            final String[] files = sourceDir.list();
            if (files != null && files.length > 0) {
                return false;
            }
        }
        return true;
    }

    private Command parseCommandLineArguments(final String[] arguments) {
        final Options options = new Options();

        final Option sourceDirectory = new Option("src", "source", true, "source directory (Required)");
        sourceDirectory.setRequired(true);
        options.addOption(sourceDirectory);

        final Option outputDirectory = new Option("out", "output", true, "output directory (Required)");
        outputDirectory.setRequired(true);
        options.addOption(outputDirectory);

        final Option threadCount = new Option("th", "thread", true, "Number of threads (Optional, default value is 2)");
        threadCount.setRequired(false);
        options.addOption(threadCount);

        final CommandLineParser parser = new PosixParser();
        final HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, arguments);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Arguments......", options);
            System.exit(1);
            return null;
        }

        final String sourceFilePath = cmd.getOptionValue("source");
        final String outputFilePath = cmd.getOptionValue("output");
        final Integer numberOfThread = Strings.isNullOrEmpty(cmd.getOptionValue("thread")) ? THREAD_COUNT_DEFAULT :
                Integer.parseInt(cmd.getOptionValue("thread"));

        return new Command(sourceFilePath, outputFilePath, numberOfThread);
    }

    private final class Command {
        final String sourceDirectory;
        final String outputDirectory;
        final Integer threadCount;

        private Command(final String sourceDirectory, final String outputDirectory, final Integer threadCount) {
            this.sourceDirectory = sourceDirectory;
            this.outputDirectory = outputDirectory;
            this.threadCount = threadCount;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final ThumbNailMaker thumbNailMaker = new ThumbNailMaker();

        final Command command = thumbNailMaker.parseCommandLineArguments(args);
        assert command != null && command.sourceDirectory != null && command.outputDirectory != null;

        //check if source directory is empty
        if (thumbNailMaker.isDirectoryEmpty(command.sourceDirectory)) {
            System.exit(0);
        }

        //delete everything in output directory
        FileUtils.cleanDirectory(new File(command.outputDirectory));

        final Instant starts = Instant.now();
        final List<File> sourceImages = thumbNailMaker.traverseImages(command.sourceDirectory);
        System.out.println(String.format("Started to generate thumbnail for %d images", sourceImages.size()));
        final int partitionSize = IntMath.divide(sourceImages.size(), command.threadCount, RoundingMode.UP);
        final List<List<File>> partitions = Lists.partition(sourceImages, partitionSize);
        thumbNailMaker.parallelExecution(partitions, command.outputDirectory, command.threadCount);
        final Instant ends = Instant.now();

        System.out.println(String.format("Total time taken to generate thumbnails of %d images is %s seconds", sourceImages.size(),
                (double) Duration.between(starts, ends).toMillis() / 1000));
    }

}