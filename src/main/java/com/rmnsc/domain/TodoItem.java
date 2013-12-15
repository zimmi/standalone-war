package com.rmnsc.domain;

import java.util.Objects;

/**
 *
 * @author thomas
 */
public class TodoItem {

    private final String description;

    public TodoItem(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public String getDescription() {
        return description;
    }
}