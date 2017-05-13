import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;

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
import java.util.List;

/**
 * Created by saurav on 5/13/17.
 */
public class Runner {
    private final static int THREAD_COUNT_DEFAULT = 3;
    private final static String[] SOURCE_IMAGE_FORMATS = {"jpg", "png", "jpeg", "bmp"};

    private class Command {
        final String sourceDirectory;
        final String outputDirectory;
        final Integer threadCount;

        private Command(String sourceDirectory, String outputDirectory, Integer threadCount) {
            this.sourceDirectory = sourceDirectory;
            this.outputDirectory = outputDirectory;
            this.threadCount = threadCount;
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

    private String getImageExtensionPattern() {
        return "*.{" + String.join(",", SOURCE_IMAGE_FORMATS) + "}";
    }

    private Command parseCommandLineArguments(String[] arguments) {
        Options options = new Options();
        Option sourceDirectory = new Option("src", "source", true, "source directory (Required)");
        sourceDirectory.setRequired(true);
        options.addOption(sourceDirectory);

        Option outputDirectory = new Option("out", "output", true, "output directory (Required)");
        outputDirectory.setRequired(true);
        options.addOption(outputDirectory);

        Option threadCount = new Option("th", "thread", true, "Number of threads (Optional, default value is 2)");
        threadCount.setRequired(false);
        options.addOption(threadCount);

        CommandLineParser parser = new PosixParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, arguments);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Arguments......", options);
            System.exit(1);
            return null;
        }

        String sourceFilePath = cmd.getOptionValue("source");
        String outputFilePath = cmd.getOptionValue("output");
        Integer numberOfThread = Strings.isNullOrEmpty(cmd.getOptionValue("thread")) ? THREAD_COUNT_DEFAULT :
                Integer.parseInt(cmd.getOptionValue("thread"));

        return new Command(sourceFilePath, outputFilePath, numberOfThread);

    }

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

    public static void main(String[] args) throws IOException, InterruptedException {
        final ThumbNailMaker thumbNailMaker = new ThumbNailMaker();
        final Runner runner = new Runner();

        final Command command = runner.parseCommandLineArguments(args);
        assert command != null && command.sourceDirectory != null && command.outputDirectory != null;

        //check if source directory is empty
        if (runner.isDirectoryEmpty(command.sourceDirectory)) {
            System.exit(0);
        }

        //delete everything in output directory
        FileUtils.cleanDirectory(new File(command.outputDirectory));

        final Instant starts = Instant.now();
        final List<File> sourceImages = runner.traverseImages(command.sourceDirectory);
        System.out.println(String.format("Started to generate thumbnail for %d images", sourceImages.size()));
        final int partitionSize = IntMath.divide(sourceImages.size(), command.threadCount, RoundingMode.UP);
        final List<List<File>> partitions = Lists.partition(sourceImages, partitionSize);
        thumbNailMaker.parallelExecution(partitions, command.outputDirectory, command.threadCount);
        final Instant ends = Instant.now();

        System.out.println(String.format("Total time taken to generate thumbnails of %d images is %s seconds", sourceImages.size(),
                (double) Duration.between(starts, ends).toMillis() / 1000));
    }
 }
