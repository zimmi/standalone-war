package com.rmnsc.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Possible profiles used by this application. Provides type safety.
 *
 * @author thomas
 */
public enum Profile {


    DEV("dev"), PROD("prod");
    private static final Map<String, Profile> propertyNameToProfile;

    static {
        Profile[] profiles = values();
        propertyNameToProfile = new HashMap<>(profiles.length);
        for (Profile profile : profiles) {
            propertyNameToProfile.put(profile.getPropertyName(), profile);
        }
    }

    /**
     * Translates the property name for a given Profile into a typesafe enum.
     *
     * @param propertyName
     * @return The matching Profile, or null if not found.
     */
    public static Profile valueOfPropertyName(String propertyName) {
        return propertyNameToProfile.get(propertyName);
    }

    public static Set<String> getProfilePropertyNames() {
        return new HashSet<>(propertyNameToProfile.keySet());
    }
    private final String propertyName;

    private Profile(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public String toString() {
        return propertyName;
    }
}