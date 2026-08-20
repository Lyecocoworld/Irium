package net.fabricmc.loader.api.metadata;

import java.nio.file.Path;
import java.util.List;

/** Adaptateur Irium — origine d'un mod. */
public interface ModOrigin {

    Kind getKind();

    List<Path> getPaths();

    default String getParentModId() { return ""; }

    default String getParentSubLocation() { return ""; }

    enum Kind { PATH, NESTED, UNKNOWN }
}
