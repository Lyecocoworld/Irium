package net.fabricmc.loader.api.metadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.Version;

/**
 * Adaptateur Irium — surface officielle ModMetadata (abstraites = utilisées
 * par le mod : id/name/version ; le reste en defaults).
 */
public interface ModMetadata {

    String getType();

    String getId();

    default Collection<String> getProvides() { return List.of(); }

    Version getVersion();

    default ModEnvironment getEnvironment() { return ModEnvironment.UNIVERSAL; }

    default Collection<ModDependency> getDependencies() { return List.of(); }

    default Collection<ModDependency> getDepends() { return List.of(); }

    default Collection<ModDependency> getRecommends() { return List.of(); }

    default Collection<ModDependency> getSuggests() { return List.of(); }

    default Collection<ModDependency> getConflicts() { return List.of(); }

    default Collection<ModDependency> getBreaks() { return List.of(); }

    String getName();

    default String getDescription() { return ""; }

    default Collection<Person> getAuthors() { return List.of(); }

    default Collection<Person> getContributors() { return List.of(); }

    /**
     * M7-B11c : Mod Menu appelle getContact() pour TOUT mod listé (lien
     * "Source" de la description, DescriptionListWidget.rebuildUI). Une
     * UnsupportedOperationException ici tue mouseClicked (FATAL Render thread).
     * Retourner des contacts vides plutôt que jeter.
     */
    default ContactInformation getContact() {
        return new ContactInformation() {
            @Override public Optional<String> get(String key) { return Optional.empty(); }
        };
    }

    default Collection<String> getLicense() { return List.of(); }

    default Optional<String> getIconPath(int size) { return Optional.empty(); }

    default boolean containsCustomValue(String key) { return false; }

    default CustomValue getCustomValue(String key) { return null; }

    default Map<String, CustomValue> getCustomValues() { return Map.of(); }

    default boolean containsCustomElement(String key) { return false; }
}
