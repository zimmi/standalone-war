package com.rmnsc.web;

/**
 *
 * @author Thomas
 */
public enum StandardRequestParameter {

    /**
     * Overrides locale set in session and default locale for this one request.
     */
    FORCED_LOCALE_TAG("flocale"), CHANGE_LOCALE_TAG(StandardSessionAttribute.LOCALE.getAttributeName());
    private final String parameterName;

    private StandardRequestParameter(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }
}