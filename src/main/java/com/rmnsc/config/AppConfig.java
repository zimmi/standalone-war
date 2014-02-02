package com.rmnsc.config;

import java.util.Objects;
import java.util.Properties;

/**
 *
 * @author Thomas
 */
public final class AppConfig {

    public static final String DEVELOPMENT_PROFILE = "dev";
    public static final String PRODUCTION_PROFILE = "prod";

    private static final String JDBC_HOST_PROP = "rmnsc.jdbc.host";
    private static final String JDBC_PORT_PROP = "rmnsc.jdbc.port";
    private static final String JDBC_DBNAME_PROP = "rmnsc.jdbc.dbname";
    private static final String JDBC_USERNAME_PROP = "rmnsc.jdbc.username";
    private static final String JDBC_PASSWORD_PROP = "rmnsc.jdbc.password";

    private static final String ACTIVE_PROFILE_PROP = "rmnsc.profile";

    private static final String RESOURCES_VERSION_PROP = "rmnsc.resources.version";

    private final String jdbcHost;
    private final int jdbcPort;
    private final String jdbcDbName;
    private final String jdbcUsername;
    private final String jdbcPassword;
    private final String activeProfile;
    private final String internalResourceRoot;
    private final String resourceRoot;

    public AppConfig(Properties props) {
        this.jdbcHost = Objects.requireNonNull(props.getProperty(JDBC_HOST_PROP), JDBC_PORT_PROP);

        String jdbcPortString = Objects.requireNonNull(props.getProperty(JDBC_PORT_PROP), JDBC_PORT_PROP);
        int jdbcPortValue;
        try {
            jdbcPortValue = Integer.parseInt(jdbcPortString);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(JDBC_PORT_PROP + " malformed: " + jdbcPortString, ex);
        }
        if (!isPortValid(jdbcPortValue)) {
            throw new IllegalArgumentException(JDBC_PORT_PROP + " is no valid port: " + jdbcPortValue);
        }
        this.jdbcPort = jdbcPortValue;

        this.jdbcDbName = Objects.requireNonNull(props.getProperty(JDBC_DBNAME_PROP), JDBC_DBNAME_PROP);
        this.jdbcUsername = Objects.requireNonNull(props.getProperty(JDBC_USERNAME_PROP), JDBC_USERNAME_PROP);
        this.jdbcPassword = Objects.requireNonNull(props.getProperty(JDBC_PASSWORD_PROP), JDBC_PASSWORD_PROP);

        String profile = Objects.requireNonNull(props.getProperty(ACTIVE_PROFILE_PROP), ACTIVE_PROFILE_PROP);
        switch (profile) {
            case PRODUCTION_PROFILE:
                this.activeProfile = PRODUCTION_PROFILE;
                break;
            case DEVELOPMENT_PROFILE:
                this.activeProfile = DEVELOPMENT_PROFILE;
                break;
            default:
                throw new IllegalArgumentException(
                        "unknown " + ACTIVE_PROFILE_PROP + ": " + profile
                        + ", must be one of (" + PRODUCTION_PROFILE + ", " + DEVELOPMENT_PROFILE + ")");
        }

        this.internalResourceRoot = "/static/";

        String resourcesVersion = props.getProperty(RESOURCES_VERSION_PROP);
        if (resourcesVersion == null || resourcesVersion.isEmpty()) {
            this.resourceRoot = this.internalResourceRoot;
        } else {
            this.resourceRoot = "/static-" + resourcesVersion + "/";
        }
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public boolean isProduction() {
        return PRODUCTION_PROFILE.equals(getActiveProfile());
    }

    public boolean isDevelopment() {
        return DEVELOPMENT_PROFILE.equals(getActiveProfile());
    }

    public String getInternalResourceRoot() {
        return internalResourceRoot;
    }

    /**
     * The path to static resources as seen from the client. Version information
     * is embedded in this path to avoid client caching problems on version
     * upgrade.
     *
     * @return
     */
    public String getResourceRoot() {
        return resourceRoot;
    }

    public String getJdbcHost() {
        return jdbcHost;
    }

    public int getJdbcPort() {
        return jdbcPort;
    }

    public String getJdbcDbName() {
        return jdbcDbName;
    }

    public String getJdbcUsername() {
        return jdbcUsername;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    private static boolean isPortValid(int port) {
        return port >= 0 && port < (1 << 16);
    }

}
