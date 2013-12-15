package com.rmnsc.startup;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.StatusPrinter;
import com.rmnsc.config.Profile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionTrackingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.ServletContextPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.util.HttpSessionMutexListener;

/**
 *
 * @author Thomas
 */
public class StartupServletContextListener implements ServletContextListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupServletContextListener.class);
    private static final String ACTIVE_PROFILE_PROPERTY_NAME = "rmnsc.profile";

    private static PatternLayoutEncoder createLoggingEncoder(LoggerContext loggerContext) {
        PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
        patternLayoutEncoder.setContext(loggerContext);
        patternLayoutEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        patternLayoutEncoder.start();

        return patternLayoutEncoder;
    }

    private static Appender<ILoggingEvent> createDevAppender(LoggerContext loggerContext) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(loggerContext);

        consoleAppender.setEncoder(createLoggingEncoder(loggerContext));

        consoleAppender.start();

        return consoleAppender;
    }

    private static Appender<ILoggingEvent> createProdAppender(LoggerContext loggerContext) {
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setContext(loggerContext);

        TimeBasedRollingPolicy<ILoggingEvent> timeBasedRollingPolicy = new TimeBasedRollingPolicy<>();
        timeBasedRollingPolicy.setContext(loggerContext);
        // rolling policies need to know their parent
        // it's one of the rare cases, where a sub-component knows about its parent
        timeBasedRollingPolicy.setParent(rollingFileAppender);

        // daily rollover, zip at midnight
        timeBasedRollingPolicy.setFileNamePattern("log/%d.app.log.zip");
        // keep 30 days' worth of history
        timeBasedRollingPolicy.setMaxHistory(30);

        timeBasedRollingPolicy.start();

        rollingFileAppender.setRollingPolicy(timeBasedRollingPolicy);
        rollingFileAppender.setEncoder(createLoggingEncoder(loggerContext));

        rollingFileAppender.start();

        return rollingFileAppender;
    }

    private static void initializeLogback(Profile activeProfile) {
        // Inlitialize logback in code to avoid dependency on logback-config-dependency Janino
        // since there's really no need for it. Also more IDE support on logging changes.

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        Appender<ILoggingEvent> appender;
        Level level;
        // We COULD associate appender and level directly with Profile and do away with this brittle-to-change if-else-cascade.
        // Meh.
        if (activeProfile == Profile.DEV) {
            appender = createDevAppender(loggerContext);
            level = Level.DEBUG;
        } else if (activeProfile == Profile.PROD) {
            appender = createProdAppender(loggerContext);
            level = Level.INFO;
        } else {
            throw new IllegalStateException("forgot to handle profile: " + activeProfile.name() + " with profile property: " + activeProfile.getPropertyName());
        }

        // If there was a ListAppender buffering startup events, replay them.
        // TODO: Way around hardcoded appender-name? share name in init-params?
        // and init param is named...? maybe share name of init param in init param. huh. needs more indirection.
        ListAppender<ILoggingEvent> startupBuffer = (ListAppender<ILoggingEvent>) rootLogger.getAppender("STARTUP_BUFFER");
        List<ILoggingEvent> startupEvents = Collections.emptyList();
        if (startupBuffer != null) {
            startupEvents = startupBuffer.list;
        }

        // Clear any previous configuration, e.g. default configuration.
        // Note: This has to be done directly before adding the new appenders.
        // The webapp-classloader may log class loading activity, and this may lead
        // to a "no appender found" warning, because we removed them all by resetting the context.
        loggerContext.reset();

        rootLogger.addAppender(appender);
        rootLogger.setLevel(level);

        // Replay all startup events, if any.
        for (ILoggingEvent startupEvent : startupEvents) {
            if (rootLogger.isEnabledFor(startupEvent.getLevel())) {
                rootLogger.callAppenders(startupEvent);
            }
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
    }

    private static ConfigurableEnvironment initializeEnvironment(final ServletContext servletContext, Profile activeProfile, final Properties defaultProperties) {
        ConfigurableEnvironment environment = new AbstractEnvironment() {
            @Override
            @SuppressWarnings("unchecked")
            protected void customizePropertySources(MutablePropertySources propertySources) {
                propertySources.addLast(new ServletContextPropertySource(StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME, servletContext));
                // premature optimization: removing synchronization of properties
                propertySources.addLast(new MapPropertySource("defaultProperties", new HashMap(defaultProperties)));

                // Set some additional internal properties.
                Map<String, Object> internalProperties = new HashMap<>();

                internalProperties.put("resourceroot.internal", "/static/");

                // The path to static resources as seen from the client.
                // Version information is embedded in this path to avoid
                // client caching problems on version upgrade.
                // TODO: Maybe maven filtering to have this automated? generate random/timestamp on -SNAPSHOT?
                internalProperties.put("resourceroot", this.resolveRequiredPlaceholders("/static-${rmnsc.resources.version}/"));
                propertySources.addLast(new MapPropertySource("internalProperties", internalProperties));
            }
        };
        environment.setActiveProfiles(activeProfile.getPropertyName());

        return environment;
    }

    private static Profile determineActiveProfile(ServletContext servletContext, Properties defaultProperties) {
        String activeProfileProp = servletContext.getInitParameter(ACTIVE_PROFILE_PROPERTY_NAME);
        if (activeProfileProp == null) {
            activeProfileProp = defaultProperties.getProperty(ACTIVE_PROFILE_PROPERTY_NAME);
        }
        if (activeProfileProp == null) {
            throw new IllegalStateException(ACTIVE_PROFILE_PROPERTY_NAME + " property not set as servlet-context init-param or default property.");
        }

        Profile activeProfile = Profile.valueOfPropertyName(activeProfileProp);
        if (activeProfile == null) {
            throw new IllegalStateException("unrecognized profile property: " + activeProfileProp);
        }

        return activeProfile;
    }

    private static Properties readDefaultProperties() {
        Properties defaultProperties = new Properties();
        try (InputStream in = StartupServletContextListener.class.getResourceAsStream("/default.properties")) {
            defaultProperties.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read default.properties", ex);
        }
        return defaultProperties;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext servletContext = sce.getServletContext();

        Properties defaultProperties = readDefaultProperties();
        // There is only one active Profile supported.
        Profile activeProfile = determineActiveProfile(servletContext, defaultProperties);

        // Initialize logging as early as possible, so no logging events are lost.
        initializeLogback(activeProfile);

        ConfigurableEnvironment environment = initializeEnvironment(servletContext, activeProfile, defaultProperties);

        // log only AFTER configuring the logger
        LOGGER.info("webapp context initialized");

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setAllowCircularReferences(false);
        context.setAllowBeanDefinitionOverriding(false);
        context.setServletContext(servletContext);
        context.setEnvironment(environment);

        // Everything else is configured transitively.
        context.register(com.rmnsc.web.Controllers.class);

        DispatcherServlet dispatcherServlet = new DispatcherServlet(context);
        ServletRegistration.Dynamic dispatcherReg =
                servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);

        // Changing the character encoding MUST be the first filter.
        // At least prior to reading request parameters.
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding(StandardCharsets.UTF_8.name());
        encodingFilter.setForceEncoding(true);

        FilterRegistration.Dynamic encodingFilterReg = servletContext.addFilter("encodingFilter", encodingFilter);
        // UTF-8 ALL the things
        encodingFilterReg.addMappingForUrlPatterns(null, false, "/*");

        // OWASP says url-rewriting is bad.
        servletContext.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));

        // Add a mutex named WebUtils.SESSION_MUTEX_ATTRIBUTE to the session.
        // Will call handler methods synchronized on this session mutex. Makes session interactions threadsafe.
        HttpSessionMutexListener mutexListener = new HttpSessionMutexListener();
        servletContext.addListener(mutexListener);

        // If browser supports it, hide the session tracking cookie from client scripts.
        servletContext.getSessionCookieConfig().setHttpOnly(true);
        servletContext.getSessionCookieConfig().setMaxAge(-1); // never expire
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("webapp context destroyed");
    }
}