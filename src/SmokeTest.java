import core.ProcessManager;
import model.PCB;
import model.ProcessState;

public class SmokeTest {
    public static void main(String[] args) {
        ProcessManager m = new ProcessManager();
        m.setLogger(s -> System.out.println("LOG: " + s));

        PCB p1 = m.createProcess(1, 128);
        PCB p2 = m.createProcess(2, 256);
        check(p1.getState() == ProcessState.READY, "p1 should be READY after create");
        check(p2.getState() == ProcessState.READY, "p2 should be READY after create");

        m.dispatch();
        check(p1.getState() == ProcessState.RUNNING, "p1 should be RUNNING after dispatch");
        check(m.getRunningProcess() == p1, "running process should be p1");

        m.blockProcess(p1.getPid());
        check(p1.getState() == ProcessState.WAITING, "p1 should be WAITING after block");
        check(m.getRunningProcess() == null, "no running process after block");

        m.wakeupProcess(p1.getPid());
        check(p1.getState() == ProcessState.READY, "p1 should be READY after wakeup");

        m.dispatch();
        check(m.getRunningProcess() == p2, "p2 should run next (FIFO)");

        m.suspendProcess(p2.getPid());
        check(p2.getState() == ProcessState.SUSPENDED, "p2 should be SUSPENDED");

        m.resumeProcess(p2.getPid());
        check(p2.getState() == ProcessState.READY, "p2 should be READY after resume");

        m.changePriority(p1.getPid(), 9);
        check(p1.getPriority() == 9, "p1 priority should be 9");

        m.destroyProcess(p1.getPid());
        check(m.findProcess(p1.getPid()) == null, "p1 should be removed");

        System.out.println("ALL SMOKE TESTS PASSED");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL: " + msg);
            System.exit(1);
        }
        System.out.println("OK: " + msg);
    }
}
