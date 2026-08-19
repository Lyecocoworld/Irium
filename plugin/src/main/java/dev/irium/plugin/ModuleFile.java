package dev.irium.plugin;

/**
 * Parseur minimal de class file (miroir de l'agent) : extrait this_class pour
 * auto-décrire les modules .irm sans fichier de configuration.
 */
final class ModuleFile {

    private ModuleFile() {}

    /** @return nom FQCN ("harness.HelloModule") ou null. */
    static String parseClassName(byte[] b) {
        try {
            int p = 0;
            if (b.length < 10) return null;
            if (u4(b, p) != 0xCAFEBABE) return null;
            p += 8; // magic + minor + major
            int cpCount = u2(b, p); p += 2;
            int[] offsets = new int[cpCount];
            for (int i = 1; i < cpCount; i++) {
                offsets[i] = p;
                int tag = b[p] & 0xFF; p += 1;
                switch (tag) {
                    case 1 -> p += 2 + u2(b, p);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> p += 4;
                    case 5, 6 -> { p += 8; i++; }
                    case 15 -> p += 3;
                    case 7, 8, 16, 19, 20 -> p += 2;
                    default -> { return null; }
                }
            }
            p += 2; // access_flags
            int thisClass = u2(b, p);
            if (thisClass <= 0 || thisClass >= cpCount) return null;
            int classEntry = offsets[thisClass];
            if (classEntry <= 0 || (b[classEntry] & 0xFF) != 7) return null;
            int nameIndex = u2(b, classEntry + 1);
            int utf8Entry = nameIndex > 0 && nameIndex < cpCount ? offsets[nameIndex] : 0;
            if (utf8Entry <= 0 || (b[utf8Entry] & 0xFF) != 1) return null;
            int len = u2(b, utf8Entry + 1);
            return new String(b, utf8Entry + 3, len, java.nio.charset.StandardCharsets.UTF_8).replace('/', '.');
        } catch (Throwable t) {
            return null;
        }
    }

    private static int u2(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
    private static int u4(byte[] b, int o) { return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF); }
}
