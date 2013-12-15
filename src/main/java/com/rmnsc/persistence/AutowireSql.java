package com.rmnsc.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author thomas
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface AutowireSql {

    // NOTE:
    // String value();
    // as override for file to inject is not provided right now.
    // The reason is enforcement of proper naming convention.

}