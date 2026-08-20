package net.fabricmc.loader.api;

/** Adaptateur Irium — parse de version (surface minimale). */
public interface Version extends Comparable<Version> {

    String getFriendlyString();
}
