package embeddedcontainer;

import ch.qos.logback.access.PatternLayout;
import ch.qos.logback.access.PatternLayoutEncoder;
import ch.qos.logback.access.jetty.RequestLogImpl;
import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Slf4jLog;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.slf4j.LoggerFactory;

/**
 * Starts the Jetty server. Assumes that all needed classes are available to the
 * {@link ClassLoader} loading this class.
 *
 * This is a separate class, so that references to jetty classes can be made
 * statically instead of through reflection.
 *
 * @author Thomas
 */
class StartJettyHelper {

    public static void startJetty(String warPath, int port) throws Exception {
        // Temporarily configure logback. Will be overwritten by webapp later.
        // Buffer all messages and forward them to the webapp, so they can be logged with the real configuration.

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Clear any previous configuration, e.g. default configuration.
        loggerContext.reset();

        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.setContext(loggerContext);
        listAppender.setName("STARTUP_BUFFER");
        listAppender.start();

        rootLogger.addAppender(listAppender);
        rootLogger.setLevel(Level.ALL);

        org.eclipse.jetty.util.log.Logger jettyLog = new Slf4jLog(Slf4jLog.class.getPackage().getName());

        Log.setLog(jettyLog);

        HandlerCollection handlers = new HandlerCollection();

        WebAppContext webapp = new WebAppContext();
        handlers.addHandler(webapp);

        // Force everything to the logging classes in this classloader.
        webapp.addSystemClass("org.slf4j.");
        webapp.addSystemClass("ch.qos.logback.");

        webapp.setLogger(jettyLog);
        webapp.setLogUrlOnStart(true);

        configureHttpAccessLogging(handlers);

        webapp.setCopyWebDir(false);
        webapp.setCopyWebInf(false);
        webapp.setTempDirectory(createJettyTempDirectory(warPath));

        webapp.setWar(warPath);

        WebAppClassLoader webAppClassLoader = new WebAppClassLoader(StartJettyHelper.class.getClassLoader(), webapp);

        webapp.setClassLoader(webAppClassLoader);

        webapp.setConfigurations(new Configuration[]{
            new WebInfConfiguration(),
            new WebXmlConfiguration(),
            new MetaInfConfiguration(),
            new FragmentConfiguration()
        });

        final Server server = new Server(port);

        server.setHandler(handlers);
        server.setStopAtShutdown(true);
        server.setStopTimeout(5000);

        // Give Jetty time to shutdown cleanly (CTRL + C in the console).
        Runtime.getRuntime()
                .addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        // Shutdown jetty.
                        try {
                            server.stop();
                        } catch (Exception ex) {
                            // The server could not be stopped.
                            // Well, it will be soon, together with the whole JVM.
                        }
                    }
                });

        Thread.currentThread().setContextClassLoader(webAppClassLoader);

        server.start();
        server.join();
    }

    private static File createJettyTempDirectory(String warPath) throws IOException {
        // Jettys temporary directory must be empty at startup.
        // The war directory already contains our extracted war, so make a new one next to it.
        Path workDir = Paths.get(warPath).getParent();
        return Files.createTempDirectory(workDir, "jetty-tmp-").toFile();
    }

    private static void configureHttpAccessLogging(HandlerCollection handlers) {
        // Configure logback-access
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        RequestLogImpl requestLogImpl = new RequestLogImpl();

        RollingFileAppender<IAccessEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setContext(requestLogImpl);

        TimeBasedRollingPolicy<IAccessEvent> timeBasedRollingPolicy = new TimeBasedRollingPolicy<>();
        timeBasedRollingPolicy.setContext(requestLogImpl);
        // rolling policies need to know their parent
        // it's one of the rare cases, where a sub-component knows about its parent
        timeBasedRollingPolicy.setParent(rollingFileAppender);

        // daily rollover, zip at midnight
        timeBasedRollingPolicy.setFileNamePattern("log/%d.server.log.zip");
        // keep 30 days' worth of history
        timeBasedRollingPolicy.setMaxHistory(30);
        timeBasedRollingPolicy.start();

        rollingFileAppender.setRollingPolicy(timeBasedRollingPolicy);

        PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setContext(requestLogImpl);
        patternLayoutEncoder.setPattern(PatternLayout.COMBINED_PATTERN + " %elapsedTime");

        patternLayoutEncoder.start();

        rollingFileAppender.setEncoder(patternLayoutEncoder);
        rollingFileAppender.start();

        // we just configured everything in code, ignore missing logback-access.xml file
        requestLogImpl.setQuiet(true);
        requestLogImpl.addAppender(rollingFileAppender);
        requestLogHandler.setRequestLog(requestLogImpl);

        handlers.addHandler(requestLogHandler);
    }
}
