package net.fabricmc.loader.api.metadata;

import java.util.Map;
import java.util.Optional;

/** Adaptateur Irium — objet custom. */
public interface CvObject extends CustomValue {

    Optional<CustomValue> get(String key);

    Map<String, CustomValue> asMap();
}
