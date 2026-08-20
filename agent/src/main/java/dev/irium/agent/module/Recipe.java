package dev.irium.agent.module;

/**
 * M5 : une recette de transformation — l'équivalent générique d'un mixin
 * Fabric @Inject(TAIL), mais poussé par le serveur et vérifié par ancre.
 *
 * @param target   nom interne de la classe hôte ("pkg/Class$Inner")
 * @param method   méthode à hooker
 * @param desc     descripteur de la méthode
 * @param anchor   sha256 des octets ORIGINAUX de la classe hôte (hex 64)
 * @param bridge   nom interne de la classe pont (".../HudBridge")
 */
record Recipe(String target, String method, String desc, byte[] anchor, String bridge) {

    static Recipe of(String target, String method, String desc, String anchorHex, String bridge) {
        if (target == null || method == null || desc == null || bridge == null
                || anchorHex == null || anchorHex.length() != 64) {
            return null;
        }
        byte[] a = new byte[32];
        try {
            for (int i = 0; i < 32; i++) {
                a[i] = (byte) Integer.parseInt(anchorHex.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new Recipe(target, method, desc, a, bridge);
    }
}
