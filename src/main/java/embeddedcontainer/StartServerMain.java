package embeddedcontainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author Thomas
 */
class StartServerMain {

    static final int DEFAULT_PORT = 8080;
    static final String EMBEDDED_CONTAINER_DIR = StartServerMain.class.getPackage().getName();
    // relative dir containing the container jars.
    static final String EMBEDDED_CONTAINER_LIB_DIR = EMBEDDED_CONTAINER_DIR + "/lib";
    // Deletes files and directories recursively.
    private static final FileVisitor<Path> DIR_TERMINATOR = new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    };

    public static void main(String[] args) throws Exception {
        Path warLocation = Paths.get(
                StartServerMain.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path warContainingDir = warLocation.getParent();

        // Working folder for the whole application.
        Path workDir = warContainingDir.resolve(System.getProperty("work.dir", "work"));
        // Do the cleanup on startup since shutdown-hooks are unreliable.
        workDir = prepareWorkDir(workDir);

        // Let libs use this same folder.
        String workDirString = workDir.toString();
        System.setProperty("java.io.tmpdir", workDirString);
        System.setProperty("javax.servlet.context.tempdir", workDirString);

        Path extractedWarDir = Files.createTempDirectory(workDir, "war-tmp-");

        extractWar(warLocation, extractedWarDir);

        File[] containerLibs = extractedWarDir.resolve(EMBEDDED_CONTAINER_LIB_DIR).toFile().listFiles();
        // Also load all libs of this webapp, so we can configure proper logging for the container.
        File[] webappLibs = extractedWarDir.resolve("WEB-INF/lib").toFile().listFiles();

        URL[] serverClasspath = new URL[1 + containerLibs.length + webappLibs.length];
        serverClasspath[0] = extractedWarDir.toUri().toURL();

        for (int i = 0; i < containerLibs.length; i++) {
            serverClasspath[1 + i] = containerLibs[i].toURI().toURL();
        }
        for (int i = 0; i < webappLibs.length; i++) {
            serverClasspath[1 + containerLibs.length + i] = webappLibs[i].toURI().toURL();
        }

        int port = determinePort();
        if (!isPortAvailable(port)) {
            throw new IllegalArgumentException("port is in use: " + port);
        }

        // This classloader will not have our package in its classpath.
        // We need this as the parent of our server-classloader, since URLClassLoader
        // will ask its parent (the classloader of this class) if it can load the
        // server-starting-class. Which it can. But our classloader does not contain
        // all the server-jars in the lib directory. So it would fail to load the server-classes.
        ClassLoader extensionClassLoader = ClassLoader.getSystemClassLoader().getParent();

        URLClassLoader serverLoader = new URLClassLoader(serverClasspath, extensionClassLoader);
        System.out.println("Starting server ...");
        startJettyHelper(extractedWarDir.toString(), serverLoader, port);
    }

    /**
     * Returns a path to an existing and empty working directory.
     *
     * @param workDir initial working directory
     * @return An empty working directory.
     * @throws IOException
     */
    private static Path prepareWorkDir(Path workDir) throws IOException {
        Files.createDirectories(workDir);

        System.out.println("Cleaning up working directory: " + workDir + " ...");
        try (DirectoryStream<Path> workDirStream = Files.newDirectoryStream(workDir)) {
            for (Path workFile : workDirStream) {
                try {
                    Files.walkFileTree(workFile, DIR_TERMINATOR);
                } catch (IOException ex) {
                    System.err.println("Unable to delete: " + workFile);
                    System.err.println("Reason:");
                    ex.printStackTrace(System.err);
                }
            }
        }
        // Always use a clean unique subfolder to remove any possibility of name clashes with remaining files.
        return Files.createTempDirectory(workDir, getWorkDirPrefix() + "--");
    }

    private static String getWorkDirPrefix() {
        // yyyy-mm-dd--hh-mm-ss
        return String.format("%1$tY-%1$tm-%1$td--%1$tH-%1$tM-%1$tS", Calendar.getInstance(Locale.getDefault()));
    }

    private static void extractWar(Path warLocation, Path destFolder) throws IOException {
        long start = System.currentTimeMillis();
        Files.createDirectories(destFolder);
        try (ZipFile warFile = new ZipFile(warLocation.toFile())) {
            Enumeration<? extends ZipEntry> entries = warFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path file = destFolder.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                } else {
                    Path parentDir = file.getParent();
                    Files.createDirectories(parentDir);

                    try(InputStream entryIn = warFile.getInputStream(entry)){
                        Files.copy(entryIn, file);
                    }
                }
            }
        }
        System.out.println("Extracted in: " + (System.currentTimeMillis() - start) + "ms");
    }

    private static int determinePort() {
        String portProp = System.getProperty("port");
        if (portProp == null) {
            return DEFAULT_PORT;
        }

        try {
            return Integer.parseInt(portProp);
        } catch (NumberFormatException ex) {
            // Don't use default port if port is not a number.
            // This seems wrong in most cases.
            throw new IllegalArgumentException("port malformed: " + portProp, ex);
        }
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void startJettyHelper(String warPath, ClassLoader serverLoader, int port) throws ReflectiveOperationException {
        // There is no static initializer or anything, so using .class is fine in the system classloader.
        Class<?> startJettyHelper = serverLoader.loadClass(StartJettyHelper.class.getName());
        Method startJetty = startJettyHelper.getDeclaredMethod("startJetty", String.class, Integer.TYPE);
        startJetty.setAccessible(true);

        // Isolate ALL the things.
        Thread.currentThread().setContextClassLoader(serverLoader);
        startJetty.invoke(null, warPath, port);
    }
}