package dev.irium.agent.module;

import dev.irium.agent.IriumAgent;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * M7-X21/X22 "Irium Gateway" : choix AVANT le boot de Minecraft (premain).
 * X22 : rendu aux VRAIES textures Minecraft 26.2 (extraites du jar client
 * officiel, embarquées dans /mc/) : boutons nine-slice 200x20 border 3,
 * fond dirt tuilé assombri, police bitmap officielle ascii+accented rendue
 * glyphe par glyphe avec ombre 25%.
 * Fermer la fenêtre = instance classique (ne jamais bloquer le boot).
 */
public final class BootChooser {

    public static final int MODE_CLASSIC = 0;
    public static final int MODE_FULL = 1;

    // Teintes de texte (les sprites sont déjà couleur vanilla)
    private static final Color GOLD = new Color(0xFFD84D);
    private static final Color GRAY = new Color(0xA0A0A0);
    private static final Color WHITE = new Color(0xFFFFFF);

    private static volatile int chosenMode = MODE_CLASSIC;
    private static volatile String chosenHost;

    private BootChooser() {
    }

    public static String server() {
        return chosenHost;
    }

    public static boolean wantsFullBoot() {
        return chosenMode == MODE_FULL && chosenHost != null;
    }

    /** M7-X21 : forcer le mode full pour un serveur (tests bot / CLI). */
    public static void forceFull(String hostPort) {
        chosenMode = MODE_FULL;
        chosenHost = hostPort;
    }

    public static void chooseBlocking() {
        try {
            List<ServerRegistry.ServerEntry> servers = ServerRegistry.list();
            if (servers.isEmpty()) {
                IriumAgent.log("[gateway] aucun serveur Irium en cache -> boot classique");
                chosenMode = MODE_CLASSIC;
                return;
            }
            if (GraphicsEnvironment.isHeadless()) {
                chosenMode = MODE_CLASSIC;
                return;
            }
            chosenMode = showDialog(servers);
            IriumAgent.log("[gateway] choix: " + (chosenMode == MODE_FULL
                    ? "EXPÉRIENCE COMPLÈTE (" + chosenHost + ")" : "instance classique"));
        } catch (Throwable t) {
            IriumAgent.log("[gateway] chooser échoué -> boot classique: " + t);
            chosenMode = MODE_CLASSIC;
        }
    }

    // ------------------------------------------------------------------
    //  Métriques (GUI scale 2 : 200x20 -> 400x40)
    // ------------------------------------------------------------------

    private static final int W = 480;
    private static final int BTN_W = 400;
    private static final int BTN_X = (W - BTN_W) / 2;

    private static int showDialog(List<ServerRegistry.ServerEntry> servers) throws Exception {
        McAssets.init();
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        final int[] result = { MODE_CLASSIC };

        JDialog dialog = new JDialog((Frame) null, "Irium", true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                result[0] = MODE_CLASSIC;
                dialog.dispose();
            }
        });
        dialog.setSize(W, 300);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

        DirtPanel root = new DirtPanel();
        dialog.setContentPane(root);

        buildStep1(dialog, root, servers, result);

        dialog.setVisible(true); // bloque jusqu'à dispose
        return result[0];
    }

    /** Étape 1 : Classique vs Expérience complète. */
    private static void buildStep1(JDialog dialog, JPanel root,
                                   List<ServerRegistry.ServerEntry> servers, int[] result) {
        root.removeAll();

        McText title = new McText("IRIUM", GOLD, 3);
        title.setBounds(0, 20, W, 30);
        root.add(title);

        McText subtitle = new McText("Choisis ton expérience", WHITE, 2);
        subtitle.setBounds(0, 56, W, 18);
        root.add(subtitle);

        McButton classic = new McButton("Jouer en Classique");
        classic.setBounds(BTN_X, 92, BTN_W, 40);
        classic.addActionListener(e -> {
            result[0] = MODE_CLASSIC;
            dialog.dispose();
        });
        root.add(classic);

        McButton full = new McButton("Expérience complète");
        full.setBounds(BTN_X, 142, BTN_W, 40);
        full.addActionListener(e -> buildStep2(dialog, root, servers, result));
        root.add(full);

        McText footer = new McText("Le serveur porte les mods", GRAY, 2);
        footer.setBounds(0, 252, W, 18);
        root.add(footer);

        root.revalidate();
        root.repaint();
    }

    /** Étape 2 : liste des serveurs. */
    private static void buildStep2(JDialog dialog, JPanel root,
                                   List<ServerRegistry.ServerEntry> servers, int[] result) {
        root.removeAll();

        McText title = new McText("IRIUM", GOLD, 2);
        title.setBounds(0, 14, W, 20);
        root.add(title);

        McText hint = new McText("Choisis ton serveur :", WHITE, 2);
        hint.setBounds(0, 42, W, 18);
        root.add(hint);

        int y = 72;
        for (ServerRegistry.ServerEntry s : servers) {
            if (y + 40 > 360) break; // garde-fou hauteur
            McButton b = new McButton(s.displayName());
            final String host = s.hostKey();
            b.setBounds(BTN_X, y, BTN_W, 40);
            b.addActionListener(e -> {
                chosenHost = host;
                result[0] = MODE_FULL;
                dialog.dispose();
            });
            root.add(b);
            y += 48;
        }

        McButton back = new McButton("Retour");
        back.setBounds(BTN_X, y + 6, BTN_W, 40);
        back.addActionListener(e -> buildStep1(dialog, root, servers, result));
        root.add(back);

        int needed = y + 6 + 40 + 16;
        if (needed > dialog.getHeight()) {
            dialog.setSize(W, needed);
            dialog.setLocationRelativeTo(null);
        }

        root.revalidate();
        root.repaint();
    }

    // ==================================================================
    //  Composants aux textures officielles
    // ==================================================================

    /** Bouton au sprite officiel nine-slice + texte police MC ombré. */
    static final class McButton extends JButton {
        McButton(String label) {
            super(label);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(WHITE);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight();
            boolean enabled = getModel().isEnabled();
            boolean hover = enabled && getModel().isRollover();
            McAssets.drawButton(g, 0, 0, w, h, hover, enabled);
            Color c = enabled ? (hover ? GOLD : WHITE) : GRAY;
            McAssets.drawTextCentered(g, getText(), 0, w,
                    (h - 8 * 2) / 2, c, 2, true);
            g.dispose();
        }
    }

    /** Texte police MC ombré. */
    static final class McText extends JComponent {
        private final String text;
        private final Color color;
        private final int scale;

        McText(String text, Color color, int scale) {
            this.text = text;
            this.color = color;
            this.scale = scale;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            McAssets.drawTextCentered(g, text, 0, getWidth(), 0, color, scale, true);
            g.dispose();
        }
    }

    /** Fond dirt officiel tuilé + voile sombre. */
    static final class DirtPanel extends JPanel {
        DirtPanel() {
            super(null);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            McAssets.drawDirt((Graphics2D) g, getWidth(), getHeight(), 0.45f);
        }
    }
}
