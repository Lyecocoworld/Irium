package dev.irium.agent.module;

/**
 * Lecteur minimal de class file : extrait le nom interne (this_class) sans
 * dépendance. Format : magic u4, minor u2, major u2, cp_count u2, constant
 * pool à tailles variables, puis access_flags u2, this_class u2.
 * this_class pointe sur CONSTANT_Class (tag 7) qui pointe sur un CONSTANT_Utf8.
 */
final class ClassFileName {

    private ClassFileName() {}

    /** @return le nom interne ("com/ex/Demo") ou null si non parsable. */
    static String parse(byte[] b) {
        try {
            int p = 0;
            if (b.length < 10) return null;
            int magic = u4(b, p); p += 4;
            if (magic != 0xCAFEBABE) return null;
            p += 4; // minor + major
            int cpCount = u2(b, p); p += 2;
            int[] offsets = new int[cpCount]; // offset de chaque entrée (0 = inutilisée)
            for (int i = 1; i < cpCount; i++) {
                offsets[i] = p;
                int tag = b[p] & 0xFF; p += 1;
                switch (tag) {
                    case 1 -> p += 2 + u2(b, p);            // Utf8: len + bytes
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> p += 4; // Int/Float/refs/NameAndType/Dynamic/InvokeDyn
                    case 5, 6 -> { p += 8; i++; }            // Long/Double: occupent 2 slots
                    case 15 -> p += 3;                        // MethodHandle: kind u1 + index u2
                    case 7, 8, 16, 19, 20 -> p += 2;         // Class/String/MethodType/Module/Package
                    default -> { return null; }              // tag inconnu -> abort
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
            return new String(b, utf8Entry + 3, len, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int u2(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
    private static int u4(byte[] b, int o) { return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF); }
}
