import com.sun.tools.attach.VirtualMachine;

/** Attache l'agent Irium à chaud sur un JVM vivant (PID en argument). */
public class Attach {
    public static void main(String[] args) throws Exception {
        String pid = args[0];
        String agentJar = args[1].replace('\\', '/');
        VirtualMachine vm = VirtualMachine.attach(pid);
        vm.loadAgent(agentJar, "force");
        vm.detach();
        System.out.println("[attach] irium-agent chargé dans PID " + pid);
    }
}
