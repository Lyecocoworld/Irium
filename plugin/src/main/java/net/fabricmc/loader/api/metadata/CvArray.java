package net.fabricmc.loader.api.metadata;

import java.util.List;

/** Adaptateur Irium — tableau custom. */
public interface CvArray extends CustomValue {

    List<CustomValue> asList();

    int size();
}
