package com.rmnsc.web;

/**
 *
 * @author Thomas
 */
public enum StandardSessionAttribute {

    LOCALE("locale");
    private final String attributeName;

    private StandardSessionAttribute(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }
}