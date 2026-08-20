package net.fabricmc.loader.api.metadata;

import java.util.Map;
import java.util.Optional;

/** Adaptateur Irium — informations de contact. */
public interface ContactInformation {

    static final String EMAIL = "email";
    static final String IRC = "irc";
    static final String HOMEPAGE = "homepage";
    static final String ISSUES = "issues";
    static final String SOURCES = "sources";

    Optional<String> get(String key);

    default Map<String, String> asMap() { return Map.of(); }
}
