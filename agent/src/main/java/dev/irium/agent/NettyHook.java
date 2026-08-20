package dev.irium.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * M3 : transforme io.netty.channel.DefaultChannelPipeline pour notifier
 * IriumHooks à chaque addLast/addBefore nommé. Netty n'est jamais obfusqué
 * dans les builds Mojang : c'est le point d'accroche stable multi-versions.
 *
 * Méthodes ciblées (String = nom du handler ajouté) :
 *   addLast(String, ChannelHandler)                      -> var 1
 *   addLast(EventExecutorGroup, String, ChannelHandler)   -> var 2
 *   addBefore(String base, String name, ChannelHandler)   -> var 2
 *
 * Injection : avant chaque ARETURN, appel statique
 *   IriumHooks.onHandlerAdded(ChannelPipeline this, String name)
 * Empile 2 puis dépile -> stack inchangée, aucune branche ajoutée :
 * les frames d'origine restent valides (ClassWriter(reader, 0)).
 */
public final class NettyHook implements ClassFileTransformer {

    private static final String PIPELINE_CLASS = "io/netty/channel/DefaultChannelPipeline";
    private static final String HOOKS_OWNER = "dev/irium/agent/IriumHooks";
    private static final String HOOKS_DESC = "(Lio/netty/channel/ChannelPipeline;Ljava/lang/String;)V";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> being,
                            ProtectionDomain pd, byte[] classfileBuffer) {
        if (!PIPELINE_CLASS.equals(className)) return null;
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, 0);
            cr.accept(new Visitor(Opcodes.ASM9, cw), 0);
            dev.irium.agent.SafeLog.v("[netty-hook]", "DefaultChannelPipeline instrumenté (loader " + safeLoader(loader) + ")");
            return cw.toByteArray();
        } catch (Throwable t) {
            dev.irium.agent.SafeLog.v("[netty-hook]", "échec transformation, host intact : " + t);
            return null; // ne JAMAIS casser le host
        }
    }

    private static String safeLoader(ClassLoader l) {
        return l == null ? "bootstrap" : l.getClass().getName();
    }

    private static final class Visitor extends ClassVisitor {
        Visitor(int api, ClassWriter cw) { super(api, cw); }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            int nameVar = switch (descriptor) {
                case "(Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;" -> 1;
                case "(Lio/netty/util/concurrent/EventExecutorGroup;Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;" -> 2;
                case "(Ljava/lang/String;Ljava/lang/String;Lio/netty/channel/ChannelHandler;)Lio/netty/channel/ChannelPipeline;" -> 2;
                default -> -1;
            };
            if (nameVar < 0) return mv;
            boolean addLast = name.equals("addLast");
            boolean addBefore = name.equals("addBefore");
            if (!addLast && !addBefore) return mv;
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ARETURN) {
                        mv.visitVarInsn(Opcodes.ALOAD, 0);            // this (DefaultChannelPipeline est une ChannelPipeline)
                        mv.visitVarInsn(Opcodes.ALOAD, nameVar);      // nom du handler ajouté
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS_OWNER, "onHandlerAdded", HOOKS_DESC, false);
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }
}
