package net.fabricmc.loader.api.metadata;

import java.util.Collection;

import net.fabricmc.loader.api.Version;

/** Adaptateur Irium — dépendance de mod. */
public interface ModDependency {

    Kind getKind();

    String getModId();

    String getVersionRange();

    boolean matches(Version version);

    boolean matchesAny(Collection<Version> versions);

    enum Kind { DEPENDS, RECOMMENDS, SUGGESTS, CONFLICTS, BREAKS }
}
