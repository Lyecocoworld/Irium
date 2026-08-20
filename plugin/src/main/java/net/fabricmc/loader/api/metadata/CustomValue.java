package net.fabricmc.loader.api.metadata;

/** Adaptateur Irium — valeurs custom de fabric.mod.json. */
public interface CustomValue {

    CvType getType();

    default String asString() { throw new UnsupportedOperationException(); }

    default Number asNumber() { throw new UnsupportedOperationException(); }

    default boolean asBoolean() { throw new UnsupportedOperationException(); }

    default CvObject asObject() { throw new UnsupportedOperationException(); }

    default CvArray asArray() { throw new UnsupportedOperationException(); }

    enum CvType { STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL }
}
