package dev.irium.agent.module;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;

/**
 * M7-X22 : vraies textures Minecraft 26.2 embarquées (/mc/*.png extraites
 * du jar client officiel). Boutons nine-slice 200x20 border 3, dirt tuilé,
 * police bitmap officielle (ascii + accented + nonlatin_european) rendue
 * glyphe par glyphe, proportionnelle, avec ombre 25% comme en jeu.
 */
final class McAssets {

    private McAssets() {
    }

    static final Color WHITE = new Color(0xFFFFFF);
    static final Color GOLD = new Color(0xFFD84D);
    static final Color GRAY = new Color(0xA0A0A0);

    private static BufferedImage button, buttonHl, buttonDis, dirt;
    /** PAGES[page] ; TINTED[tint][page] = page teintée ; SHADOW[tint][page] = ombre. */
    private static BufferedImage[] pages;
    private static BufferedImage[][] tinted, shadowed;
    private static final HashMap<Integer, int[]> GLYPH = new HashMap<>();
    private static final HashMap<Long, Integer> ADVANCE = new HashMap<>();
    private static boolean loaded;

    static boolean loaded() {
        return loaded;
    }

    static synchronized void init() {
        if (loaded) return;
        try {
            button = ImageIO.read(res("/mc/button.png"));
            buttonHl = ImageIO.read(res("/mc/button_highlighted.png"));
            buttonDis = ImageIO.read(res("/mc/button_disabled.png"));
            dirt = ImageIO.read(res("/mc/dirt.png"));
            pages = new BufferedImage[McFontMap.PAGES];
            for (int i = 0; i < McFontMap.PAGES; i++) {
                pages[i] = ImageIO.read(res(McFontMap.RES[i]));
            }
            buildGlyphMap();
            Color[] tints = { WHITE, GOLD, GRAY };
            tinted = new BufferedImage[3][];
            shadowed = new BufferedImage[3][];
            for (int t = 0; t < 3; t++) {
                tinted[t] = new BufferedImage[pages.length];
                shadowed[t] = new BufferedImage[pages.length];
                for (int p = 0; p < pages.length; p++) {
                    tinted[t][p] = tint(pages[p], tints[t]);
                    shadowed[t][p] = tint(pages[p], mulQuarter(tints[t]));
                }
            }
            loaded = true;
        } catch (Throwable t) {
            System.err.println("[irium] McAssets: fallback (assets non chargés): " + t);
        }
    }

    private static InputStream res(String path) {
        InputStream in = McAssets.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("asset manquant: " + path);
        return in;
    }

    private static Color mulQuarter(Color c) {
        return new Color((c.getRed() * 64 / 255) << 16 | (c.getGreen() * 64 / 255) << 8 | (c.getBlue() * 64 / 255));
    }

    private static BufferedImage tint(BufferedImage src, Color c) {
        int cr = c.getRed(), cg = c.getGreen(), cb = c.getBlue();
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int a = (rgb >>> 24) & 0xFF;
                out.setRGB(x, y, (a << 24) | (cr << 16) | (cg << 8) | cb);
            }
        }
        return out;
    }

    private static void buildGlyphMap() {
        String h = McFontMap.MAP_HEX;
        for (int i = 0; i + 10 <= h.length(); i += 10) {
            int cp = Integer.parseInt(h.substring(i, i + 4), 16);
            int pg = Integer.parseInt(h.substring(i + 4, i + 6), 16);
            int r = Integer.parseInt(h.substring(i + 6, i + 8), 16);
            int c = Integer.parseInt(h.substring(i + 8, i + 10), 16);
            GLYPH.put(cp, new int[]{ pg, r, c });
        }
    }

    // ==================================================================
    //  Police bitmap MC
    // ==================================================================

    static int textWidth(String s, int scale) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ' ') { w += 4 * scale; continue; }
            int[] e = GLYPH.get((int) ch);
            if (e == null) { w += 5 * scale; continue; }
            w += advance(e) * scale;
        }
        return w;
    }

    /** Largeur utile du glyphe (colonnes non vides) + 1px, comme MC. */
    private static int advance(int[] e) {
        long key = ((long) e[0] << 32) | (e[1] << 16) | e[2];
        Integer cached = ADVANCE.get(key);
        if (cached != null) return cached;
        BufferedImage img = pages[e[0]];
        int cw = img.getWidth() / McFontMap.COLS[e[0]];
        int ch = img.getHeight() / (img.getHeight() / McFontMap.CELLH[e[0]]);
        int sx = e[2] * cw, sy = e[1] * ch;
        int last = -1;
        outer:
        for (int x = cw - 1; x >= 0; x--) {
            for (int y = 0; y < ch;  y++) {
                if (((img.getRGB(sx + x, sy + y) >>> 24) & 0xFF) > 8) { last = x; break outer; }
            }
        }
        int adv = (last < 0 ? 0 : last + 1) + 1;
        ADVANCE.put(key, adv);
        return adv;
    }

    private static int rowsOf(int page) {
        return pages[page].getHeight() / McFontMap.CELLH[page];
    }

    private static BufferedImage pageImg(int p) {
        return pages[p];
    }

    private static int tintIndex(Color c) {
        if (c.equals(GOLD)) return 1;
        if (c.equals(GRAY)) return 2;
        return 0;
    }

    /**
     * Dessine du texte police MC. y = haut de la ligne virtuelle (8px).
     * Ombre = couleur / 4 décalée de 1px virtuel, comme en jeu.
     */
    static void drawText(Graphics2D g, String s, int x, int y, Color c, int scale, boolean shadow) {
        int t = tintIndex(c);
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ' ') { cx += 4 * scale; continue; }
            int[] e = GLYPH.get((int) ch);
            if (e == null) { cx += 5 * scale; continue; }
            BufferedImage img = pages[e[0]];
            int cw = img.getWidth() / McFontMap.COLS[e[0]];
            int chh = McFontMap.CELLH[e[0]];
            int sx = e[2] * cw, sy = e[1] * chh;
            // alignement vertical: ascii/nonlatin ascent 7 ; accented ascent 10
            int dy = y + (7 - McFontMap.ASCENT[e[0]]) * scale;
            if (shadow) {
                g.drawImage(shadowed[t][e[0]], cx + scale, dy + scale,
                        cx + scale + cw * scale, dy + scale + chh * scale,
                        sx, sy, sx + cw, sy + chh, null);
            }
            g.drawImage(tinted[t][e[0]], cx, dy,
                    cx + cw * scale, dy + chh * scale,
                    sx, sy, sx + cw, sy + chh, null);
            cx += advance(e) * scale;
        }
    }

    /** Texte centré sur [x0, x0+w]. */
    static void drawTextCentered(Graphics2D g, String s, int x0, int w, int y, Color c, int scale, boolean shadow) {
        drawText(g, s, x0 + (w - textWidth(s, scale)) / 2, y, c, scale, shadow);
    }

    // ==================================================================
    //  Bouton nine-slice officiel (200x20, border 3)
    // ==================================================================

    static void drawButton(Graphics2D g, int x, int y, int w, int h, boolean hover, boolean enabled) {
        BufferedImage src = enabled ? (hover ? buttonHl : button) : buttonDis;
        int B = 3 * 2; // border 3 virtuel, échelle 2
        int sw = src.getWidth(), sh = src.getHeight(), b = 3;
        // 4 coins
        g.drawImage(src, x, y, x + B, y + B, 0, 0, b, b, null);
        g.drawImage(src, x + w - B, y, x + w, y + B, sw - b, 0, sw, b, null);
        g.drawImage(src, x, y + h - B, x + B, y + h, 0, sh - b, b, sh, null);
        g.drawImage(src, x + w - B, y + h - B, x + w, y + h, sw - b, sh - b, sw, sh, null);
        // bords étirés
        g.drawImage(src, x + B, y, x + w - B, y + B, b, 0, sw - b, b, null);
        g.drawImage(src, x + B, y + h - B, x + w - B, y + h, b, sh - b, sw - b, sh, null);
        g.drawImage(src, x, y + B, x + B, y + h - B, 0, b, b, sh - b, null);
        g.drawImage(src, x + w - B, y + B, x + w, y + h - B, sw - b, b, sw, sh - b, null);
        // centre
        g.drawImage(src, x + B, y + B, x + w - B, y + h - B, b, b, sw - b, sh - b, null);
    }

    // ==================================================================
    //  Fond dirt tuilé assombri (fond de menu MC)
    // ==================================================================

    static void drawDirt(Graphics2D g, int w, int h, float brightness) {
        if (dirt == null) { g.setColor(Color.BLACK); g.fillRect(0, 0, w, h); return; }
        int s = 48; // 16px * échelle 3
        for (int y = 0; y < h; y += s) {
            for (int x = 0; x < w; x += s) {
                g.drawImage(dirt, x, y, s, s, null);
            }
        }
        g.setColor(new Color(0, 0, 0, (int) ((1 - brightness) * 255)));
        g.fillRect(0, 0, w, h);
    }
}
