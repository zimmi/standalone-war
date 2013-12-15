package com.rmnsc.persistence;

import com.jolbox.bonecp.BoneCPDataSource;
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
import org.springframework.core.env.Environment;
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
            if(lastLineSep != -1 && lastLineSep + System.lineSeparator().length() == end){
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
    private Environment environment;

    @Bean
    public BeanPostProcessor autowireSqlBeanPostProcessor() {
        return new AutowireSqlBeanPostProcessor();
    }

    @Bean
    public DataSource dataSource() {
        PGSimpleDataSource rawDataSource = new PGSimpleDataSource();
        rawDataSource.setServerName(environment.getRequiredProperty("rmnsc.jdbc.host"));
        rawDataSource.setPortNumber(environment.getRequiredProperty("rmnsc.jdbc.port", int.class));
        rawDataSource.setDatabaseName(environment.getRequiredProperty("rmnsc.jdbc.dbname"));
        rawDataSource.setUser(environment.getRequiredProperty("rmnsc.jdbc.username"));
        rawDataSource.setPassword(environment.getRequiredProperty("rmnsc.jdbc.password"));
        try {
            rawDataSource.setLogWriter(
                    new PrintWriter(
                    new Slf4jInfoWriter(LoggerFactory.getLogger(rawDataSource.getClass())), true));
        } catch (SQLException ex) {
            LOGGER.warn("could not add logging to postgres datasource", ex);
        }

        BoneCPDataSource poolingDataSource = new BoneCPDataSource();
        poolingDataSource.setDatasourceBean(rawDataSource);

        poolingDataSource.setAcquireIncrement(1);
        poolingDataSource.setDetectUnclosedStatements(false);
        poolingDataSource.setDetectUnresolvedTransactions(false);
        poolingDataSource.setDisableConnectionTracking(true);
        poolingDataSource.setDisableJMX(true);
        poolingDataSource.setExternalAuth(false);
        poolingDataSource.setIdleConnectionTestPeriodInMinutes(0);
        poolingDataSource.setIdleMaxAgeInMinutes(0);
        poolingDataSource.setLazyInit(true);

        poolingDataSource.setLogStatementsEnabled(false); // jdbctemplate does this already
        poolingDataSource.setMaxConnectionAgeInSeconds(0);

        poolingDataSource.setMinConnectionsPerPartition(5);
        poolingDataSource.setMaxConnectionsPerPartition(10);
        poolingDataSource.setPartitionCount(3);

        poolingDataSource.setPoolAvailabilityThreshold(0);
        poolingDataSource.setQueryExecuteTimeLimitInMs(1000);
        poolingDataSource.setResetConnectionOnClose(false);
        poolingDataSource.setStatisticsEnabled(false);
        
        poolingDataSource.setTransactionRecoveryEnabled(true);
        poolingDataSource.setAcquireRetryDelayInMs(0);

        poolingDataSource.setDefaultAutoCommit(false);
        poolingDataSource.setDefaultTransactionIsolation("READ_COMMITTED");

        // Wrap the dataSource such that a Connection is only acquired if a statement
        // is created. This avoids potentially expensive Connection creation in methods
        // that are marked transactional but don't actually talk to the database.
        LazyConnectionDataSourceProxy lazyDataSource = new LazyConnectionDataSourceProxy();
        lazyDataSource.setTargetDataSource(poolingDataSource);

        // Tell Spring about the default settings so that it does not have to fetch a connection on startup to check them.
        lazyDataSource.setDefaultAutoCommit(poolingDataSource.getDefaultAutoCommit());
        lazyDataSource.setDefaultTransactionIsolationName("TRANSACTION_" + poolingDataSource.getDefaultTransactionIsolation());

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