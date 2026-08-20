package net.fabricmc.loader.api;

/** Adaptateur Irium — exception de parse de version. */
public class VersionParsingException extends Exception {

    public VersionParsingException(String message) {
        super(message);
    }
}
