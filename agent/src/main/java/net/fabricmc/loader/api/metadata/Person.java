package net.fabricmc.loader.api.metadata;

/** Adaptateur Irium — personne (auteur/contributeur). */
public interface Person {

    String getName();

    default java.util.Map<String, String> getContact() { return java.util.Map.of(); }
}
