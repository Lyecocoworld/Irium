package dev.irium.plugin.fabric;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * M7 : patch Folia pour Simple Voice Chat (générique : tout mod qui fait
 * MinecraftServer.execute(runnable)).
 *
 * Canvas/Folia : MinecraftServer.execute() lève UnsupportedOperationException
 * (pas de thread serveur global). Le runnable est déjà sur le thread régional
 * du joueur quand le bridge invoque le receiver — on remplace l'appel par
 * Runnable.run() direct.
 *
 * Appliqué À LA DÉFINITION de la classe (octets en mémoire), jar intact.
 */
public final class FoliaExecutePatch {

    private FoliaExecutePatch() {}

    public static byte[] patch(byte[] classfileBuffer) {
        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            private final String targetOwner = "net/minecraft/server/MinecraftServer";

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (mv == null) return null;
                // toute méthode qui appelle MinecraftServer.execute(Runnable)
                return new MethodVisitor(api, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mname,
                                                String desc, boolean isInterface) {
                        if ("execute".equals(mname)
                                && "(Ljava/lang/Runnable;)V".equals(desc)
                                && targetOwner.equals(owner)) {
                            // stack: [MinecraftServer, Runnable] -> [Runnable]
                            // pop le server, run direct
                            super.visitInsn(Opcodes.SWAP);
                            super.visitInsn(Opcodes.POP);
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable",
                                    "run", "()V", true);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, mname, desc, isInterface);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
