package com.rmnsc.persistence;

import com.rmnsc.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

/**
 *
 * @author Thomas
 */
@Configuration
@EnableTransactionManagement
public class PersistenceConfig implements TransactionManagementConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceConfig.class);

    private static final class Slf4jInfoWriter extends Writer {

        private final ThreadLocal<StringBuilder> threadLocalBuilder;
        private final Logger logger;

        Slf4jInfoWriter(Logger logger) {
            this.logger = Objects.requireNonNull(logger);
            this.threadLocalBuilder = new ThreadLocal<>();
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            StringBuilder builder = threadLocalBuilder.get();
            if (builder == null) {
                builder = new StringBuilder();
                threadLocalBuilder.set(builder);
            }
            builder.append(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            StringBuilder builder = threadLocalBuilder.get();
            if (builder == null) {
                return;
            }
            // Remove trailing line separators.
            int end = builder.length();
            int lastLineSep = builder.lastIndexOf(System.lineSeparator());
            if (lastLineSep != -1 && lastLineSep + System.lineSeparator().length() == end) {
                end = lastLineSep;
            }
            logger.info(builder.substring(0, end));
            // Don't keep this junk around in every thread. Allocating a new one when needed is cheap.
            threadLocalBuilder.remove();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
    @Autowired
    private AppConfig appConfig;

    @Bean
    public BeanPostProcessor autowireSqlBeanPostProcessor() {
        return new AutowireSqlBeanPostProcessor();
    }

    @Bean
    public DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerName(appConfig.getJdbcHost());
        dataSource.setPortNumber(appConfig.getJdbcPort());
        dataSource.setDatabaseName(appConfig.getJdbcDbName());
        dataSource.setUser(appConfig.getJdbcUsername());
        dataSource.setPassword(appConfig.getJdbcPassword());
        try {
            dataSource.setLogWriter(new PrintWriter(
                    new Slf4jInfoWriter(LoggerFactory.getLogger(dataSource.getClass().getName())),
                    true));
        } catch (SQLException ex) {
            LOGGER.warn("failed to enable logging for raw postgres datasource", ex);
        }

        HikariConfig config = new HikariConfig();
        config.setDataSource(dataSource);
        config.setAutoCommit(false);
        config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        config.setMinimumPoolSize(5);
        config.setMaximumPoolSize(50);

        HikariDataSource connectionPool = new HikariDataSource(config);

        // Wrap the pool so that a Connection is only acquired if a statement
        // is created. This avoids potentially expensive Connection creation in methods
        // that are marked transactional but don't actually talk to the database.
        LazyConnectionDataSourceProxy lazyDataSource = new LazyConnectionDataSourceProxy();
        lazyDataSource.setTargetDataSource(connectionPool);

        // Tell Spring about the default settings so that it does not have to fetch a connection on startup to check them.
        lazyDataSource.setDefaultAutoCommit(config.isAutoCommit());
        lazyDataSource.setDefaultTransactionIsolation(config.getTransactionIsolation());

        return lazyDataSource;
    }

    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations() {
        return new NamedParameterJdbcTemplate(dataSource());
    }

    @Override
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return new DataSourceTransactionManager(dataSource());
    }
}