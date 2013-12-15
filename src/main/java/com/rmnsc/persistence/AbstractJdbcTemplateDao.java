package com.rmnsc.persistence;

import java.util.Objects;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

abstract class AbstractJdbcTemplateDao {

    protected final NamedParameterJdbcOperations jdbcOperations;

    public AbstractJdbcTemplateDao(NamedParameterJdbcOperations jdbcOperations) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
    }

}