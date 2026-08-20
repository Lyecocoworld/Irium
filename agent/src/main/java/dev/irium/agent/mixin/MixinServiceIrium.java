package dev.irium.agent.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.IMixinServiceBootstrap;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service Mixin pour l'agent Irium — jeu 26.2 non obfusqué, sans mapping.
 * (Adapté du MixinServiceMinecraft de Fabric, Apache-2.0, (c) FabricMC.)
 */
public final class MixinServiceIrium implements IMixinService {

    /** Entrée ServiceLoader (META-INF/services). */
    public static class Bootstrap implements IMixinServiceBootstrap {
        @Override public String getName() { return "Irium"; }
        @Override public String getServiceClassName() { return MixinServiceIrium.class.getName(); }
        @Override public void bootstrap() {}
    }

    private final IClassProvider classProvider = new ClassProvider();
    private final IClassBytecodeProvider bytecodeProvider = new BytecodeProvider();
    private final IClassTracker classTracker = new ClassTracker();
    private final ITransformerProvider transformerProvider = new TransformerProvider();
    private final ReEntranceLock lock = new ReEntranceLock(1);

    @Override public String getName() { return "Irium"; }
    @Override public boolean isValid() { return true; }
    @Override public void prepare() {}
    @Override public void offer(IMixinInternal mixin) {}
    @Override public void init() {}
    @Override public void beginPhase() {}
    @Override public void checkEnv(Object env) {}

    @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }

    @Override public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_21;
    }
    @Override public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override public ReEntranceLock getReEntranceLock() { return lock; }
    @Override public IClassProvider getClassProvider() { return classProvider; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return bytecodeProvider; }
    @Override public IClassTracker getClassTracker() { return classTracker; }
    @Override public ITransformerProvider getTransformerProvider() { return transformerProvider; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList(
                "org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(getName());
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        List<IContainerHandle> out = new ArrayList<>();
        synchronized (MixinGateway.modLoaders) {
            for (ClassLoader cl : MixinGateway.modLoaders) {
                out.add(new ContainerHandleVirtual("mod@" + Integer.toHexString(cl.hashCode())));
            }
        }
        return out;
    }

    @Override public InputStream getResourceAsStream(String name) {
        synchronized (MixinGateway.modLoaders) {
            for (ClassLoader cl : MixinGateway.modLoaders) {
                InputStream is = cl.getResourceAsStream(name);
                if (is != null) return is;
            }
        }
        return MixinServiceIrium.class.getClassLoader().getResourceAsStream(name);
    }

    @Override public String getSideName() { return "CLIENT"; }

    @Override public ILogger getLogger(String name) { return new MixinLogger(name); }

    /* ---------------- providers internes ---------------- */

    static final class ClassProvider implements IClassProvider {
        @Override public URL[] getClassPath() { return new URL[0]; }
        @Override public Class<?> findClass(String name) throws ClassNotFoundException {
            return Class.forName(name, false, ClassLoader.getSystemClassLoader());
        }
        @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, ClassLoader.getSystemClassLoader());
        }
        @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return Class.forName(name, initialize, MixinServiceIrium.class.getClassLoader());
        }
    }

    static final class BytecodeProvider implements IClassBytecodeProvider {
        @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
            return getClassNode(name, false, 0);
        }
        @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
            return getClassNode(name, runTransformers, 0);
        }
        @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags) throws ClassNotFoundException, IOException {
            InputStream is = null;
            synchronized (MixinGateway.modLoaders) {
                for (ClassLoader cl : MixinGateway.modLoaders) {
                    is = cl.getResourceAsStream(name.replace('.', '/') + ".class");
                    if (is != null) break;
                }
            }
            if (is == null) {
                is = ClassLoader.getSystemClassLoader().getResourceAsStream(name.replace('.', '/') + ".class");
            }
            if (is == null) throw new ClassNotFoundException(name);
            try (InputStream in = is) {
                ClassNode node = new ClassNode();
                new ClassReader(in.readAllBytes()).accept(node, 0);
                return node;
            }
        }
    }

    static final class ClassTracker implements IClassTracker {
        private final Set<String> invalid = new HashSet<>();
        @Override public void registerInvalidClass(String name) { synchronized (invalid) { invalid.add(name); } }
        @Override public boolean isClassLoaded(String name) {
            try { Class.forName(name, false, ClassLoader.getSystemClassLoader()); return true; }
            catch (ClassNotFoundException e) { return false; }
        }
        @Override public String getClassRestrictions(String name) { return ""; }
    }

    static final class TransformerProvider implements ITransformerProvider {
        @Override public Collection<ITransformer> getTransformers() { return Collections.emptyList(); }
        @Override public Collection<ITransformer> getDelegatedTransformers() { return Collections.emptyList(); }
        @Override public void addTransformerExclusion(String name) {}
    }
}
