package com.rmnsc.persistence;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;

/**
 * Injects SQL into fields annotated with {@link AutowireSql}. Uses the name of
 * the field to resolve the file containing the SQL.
 *
 * Spring does not provide a mechanism to easily externalize SQL. So we use
 * this.
 *
 * This is actually not really better than hardcoding the name of the SQL-file.
 * But this app is just a proof of concept, so what.
 *
 * @author thomas
 */
public class AutowireSqlBeanPostProcessor implements BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutowireSqlBeanPostProcessor.class);
    private static final String SQL_DIR = "WEB-INF/sql/";
    private static final String SQL_FILE_EXTENSION = ".sql";
    private static final char DIR_SEPARATOR = '_';

    private static String buildSqlResourcePath(String fieldName, Object bean, String beanName) {
        int dirEndIdx = fieldName.indexOf(DIR_SEPARATOR);
        if (dirEndIdx == -1 || dirEndIdx == 0 || dirEndIdx == fieldName.length() - 1) {
            // a) File is in root. Not allowed right now.
            // b) DIR_SEPARATOR is last char -> dirname missing.
            // c) DIR_SEPARATOR is last char -> filename missing.
            throw new IllegalArgumentException("Field with annotation @"
                    + AutowireSql.class.getSimpleName()
                    + " does not conform to naming convention: <directory>"
                    + DIR_SEPARATOR + "<filename>. bean name: " + beanName
                    + ", class: " + bean.getClass());
        }
        StringBuilder sqlResourcePath = new StringBuilder(SQL_DIR.length() + fieldName.length() + SQL_FILE_EXTENSION.length());
        sqlResourcePath.append(fieldName);
        sqlResourcePath.setCharAt(dirEndIdx, '/');
        sqlResourcePath.insert(0, SQL_DIR);
        sqlResourcePath.append(SQL_FILE_EXTENSION);
        return sqlResourcePath.toString();
    }
    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), new FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                AutowireSql annotation = field.getAnnotation(AutowireSql.class);
                if (annotation == null) {
                    return;
                }
                ReflectionUtils.makeAccessible(field);

                String sqlResourcePath = buildSqlResourcePath(field.getName(), bean, beanName);
                Resource sqlResource = resourceLoader.getResource(sqlResourcePath);

                String sqlString;
                try {
                    sqlString = new String(FileCopyUtils.copyToByteArray(sqlResource.getInputStream()), StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Unable to read resource at path: " + sqlResourcePath, ex);
                }

                ReflectionUtils.setField(field, bean, sqlString);
                LOGGER.debug("Inserted sql into field: {} of beanName: {} of class: {} the sql: {}", field.getName(), beanName, bean.getClass(), sqlString);
            }
        });
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}