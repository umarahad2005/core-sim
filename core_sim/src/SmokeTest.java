import core.MemoryManager;
import core.MemoryManager.AccessResult;
import core.ProcessManager;
import core.Scheduler;
import core.Scheduler.Algorithm;
import core.Scheduler.GanttSegment;
import model.PCB;
import model.ProcessState;
import ui.MemoryPanel;

import java.util.List;

public class SmokeTest {
    public static void main(String[] args) {
        module1Tests();
        module2FcfsTests();
        module2RoundRobinTests();
        module3LruTests();
        module3ReferenceStringParserTests();
        module3MultiProcessTests();
        module3SchedulerSyncTests();
        System.out.println("ALL SMOKE TESTS PASSED");
    }

    private static void module1Tests() {
        System.out.println("=== Module 1: PCB & Process Management ===");
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
    }

    private static void module2FcfsTests() {
        System.out.println();
        System.out.println("=== Module 2: FCFS ===");
        ProcessManager m = new ProcessManager();
        m.setLogger(s -> System.out.println("LOG: " + s));
        Scheduler s = new Scheduler(m);
        s.setLogger(msg -> System.out.println("SCHED: " + msg));
        s.setAlgorithm(Algorithm.FCFS);

        PCB p1 = m.createProcess(1, 64, 4);
        PCB p2 = m.createProcess(1, 64, 3);
        PCB p3 = m.createProcess(1, 64, 2);

        int safety = 0;
        while (s.tick() && safety++ < 100) { /* drain */ }

        check(s.getClock() == 9, "FCFS: total time should be 4+3+2 = 9, got " + s.getClock());
        check(p1.getCompletionTime() == 4, "P1 completion=4");
        check(p2.getCompletionTime() == 7, "P2 completion=7");
        check(p3.getCompletionTime() == 9, "P3 completion=9");
        check(p1.getWaitingTime() == 0, "P1 waiting=0");
        check(p2.getWaitingTime() == 4, "P2 waiting=4");
        check(p3.getWaitingTime() == 7, "P3 waiting=7");
        check(s.getGantt().size() == 3, "FCFS: 3 gantt segments");
        check(s.getGantt().get(0).pid == 1 && s.getGantt().get(0).start == 0 && s.getGantt().get(0).end == 4, "seg0 = P1 [0..4]");
        check(s.getGantt().get(1).pid == 2 && s.getGantt().get(1).start == 4 && s.getGantt().get(1).end == 7, "seg1 = P2 [4..7]");
        check(s.getGantt().get(2).pid == 3 && s.getGantt().get(2).start == 7 && s.getGantt().get(2).end == 9, "seg2 = P3 [7..9]");
    }

    private static void module2RoundRobinTests() {
        System.out.println();
        System.out.println("=== Module 2: Round Robin (TQ=2) ===");
        ProcessManager m = new ProcessManager();
        m.setLogger(s -> System.out.println("LOG: " + s));
        Scheduler s = new Scheduler(m);
        s.setLogger(msg -> System.out.println("SCHED: " + msg));
        s.setAlgorithm(Algorithm.ROUND_ROBIN);
        s.setTimeQuantum(2);

        PCB p1 = m.createProcess(1, 64, 5);
        PCB p2 = m.createProcess(1, 64, 3);
        PCB p3 = m.createProcess(1, 64, 2);

        int safety = 0;
        while (s.tick() && safety++ < 100) { /* drain */ }

        check(s.getClock() == 10, "RR: total time = 5+3+2 = 10, got " + s.getClock());
        check(p1.getState() == ProcessState.TERMINATED, "P1 terminated");
        check(p2.getState() == ProcessState.TERMINATED, "P2 terminated");
        check(p3.getState() == ProcessState.TERMINATED, "P3 terminated");
        check(p1.getCompletionTime() == 10, "P1 completion=10 (got " + p1.getCompletionTime() + ")");
        check(p2.getCompletionTime() == 9,  "P2 completion=9 (got " + p2.getCompletionTime() + ")");
        check(p3.getCompletionTime() == 6,  "P3 completion=6 (got " + p3.getCompletionTime() + ")");
        check(p1.getWaitingTime() == 5, "P1 WT=5 (got " + p1.getWaitingTime() + ")");
        check(p2.getWaitingTime() == 6, "P2 WT=6 (got " + p2.getWaitingTime() + ")");
        check(p3.getWaitingTime() == 4, "P3 WT=4 (got " + p3.getWaitingTime() + ")");

        StringBuilder seq = new StringBuilder();
        for (GanttSegment g : s.getGantt()) {
            seq.append("P").append(g.pid).append("[").append(g.start).append("-").append(g.end).append("] ");
        }
        System.out.println("RR Gantt: " + seq.toString().trim());
    }

    private static void module3LruTests() {
        System.out.println();
        System.out.println("=== Module 3: Paging + LRU (manual example, capacity=3) ===");
        MemoryManager mm = new MemoryManager(3);
        mm.setLogger(s -> System.out.println("MEM: " + s));

        // Manual example: reference string 1, 2, 3, 1, 4 on a single process
        // Expected: fault, fault, fault, hit, fault (evict page 2 as LRU)
        int pid = 1;
        AccessResult r1 = mm.accessPage(pid, 1);
        AccessResult r2 = mm.accessPage(pid, 2);
        AccessResult r3 = mm.accessPage(pid, 3);
        AccessResult r4 = mm.accessPage(pid, 1);
        AccessResult r5 = mm.accessPage(pid, 4);

        check(r1 == AccessResult.FAULT_LOAD,    "ref 1 -> fault/load");
        check(r2 == AccessResult.FAULT_LOAD,    "ref 2 -> fault/load");
        check(r3 == AccessResult.FAULT_LOAD,    "ref 3 -> fault/load");
        check(r4 == AccessResult.HIT,           "ref 1 -> hit");
        check(r5 == AccessResult.FAULT_REPLACE, "ref 4 -> fault/replace");

        check(mm.getAccesses() == 5,   "5 accesses");
        check(mm.getPageHits() == 1,   "1 hit");
        check(mm.getPageFaults() == 4, "4 faults");

        // page 2 (least recently used) must be gone; pages 1, 3, 4 must all be resident
        boolean has1 = false, has2 = false, has3 = false, has4 = false;
        for (MemoryManager.Frame f : mm.getFrames()) {
            if (f.pid == pid && f.page == 1) has1 = true;
            if (f.pid == pid && f.page == 2) has2 = true;
            if (f.pid == pid && f.page == 3) has3 = true;
            if (f.pid == pid && f.page == 4) has4 = true;
        }
        check(has1, "page 1 still resident after LRU step");
        check(!has2, "page 2 evicted by LRU (was least recently used)");
        check(has3, "page 3 still resident");
        check(has4, "page 4 newly loaded");
    }

    private static void module3ReferenceStringParserTests() {
        System.out.println();
        System.out.println("=== Module 3: Reference String parser ===");
        List<int[]> simple = MemoryPanel.parseReferenceString("1, 2, 3, 1, 4", 7);
        check(simple.size() == 5, "5 refs parsed from \"1, 2, 3, 1, 4\"");
        check(simple.get(0)[0] == 7 && simple.get(0)[1] == 1, "ref0 = pid 7, page 1");
        check(simple.get(4)[0] == 7 && simple.get(4)[1] == 4, "ref4 = pid 7, page 4");

        List<int[]> mixed = MemoryPanel.parseReferenceString("1:0, 2, 3:5", 9);
        check(mixed.size() == 3, "3 refs parsed from mixed string");
        check(mixed.get(0)[0] == 1 && mixed.get(0)[1] == 0, "ref0 = pid 1, page 0");
        check(mixed.get(1)[0] == 9 && mixed.get(1)[1] == 2, "ref1 uses default pid 9");
        check(mixed.get(2)[0] == 3 && mixed.get(2)[1] == 5, "ref2 = pid 3, page 5");

        List<int[]> garbage = MemoryPanel.parseReferenceString("abc, , 1, xx:2", 1);
        check(garbage.size() == 1, "garbage tokens are skipped, only \"1\" parsed");
    }

    private static void module3MultiProcessTests() {
        System.out.println();
        System.out.println("=== Module 3: Multi-process paging + freeProcess ===");
        MemoryManager mm = new MemoryManager(4);
        mm.setLogger(s -> System.out.println("MEM: " + s));

        mm.allocatePages(1, 2);                 // P1 takes 2 frames
        mm.allocatePages(2, 2);                 // P2 takes 2 frames -> memory full

        check(mm.getPageFaults() == 4, "4 faults from initial allocations");
        check(mm.getPageHits() == 0,   "no hits yet");

        // Touching P1 page 0 must be a hit
        check(mm.accessPage(1, 0) == AccessResult.HIT, "P1.page0 hit");

        // New page from P3 forces eviction. P2.page0 should now be LRU (P1.page0 was just touched,
        // P2.page1 was last during allocate, so the LRU is P1.page1).
        AccessResult r = mm.accessPage(3, 0);
        check(r == AccessResult.FAULT_REPLACE, "P3.page0 triggers replacement");

        // Free P2 and confirm its frames go free + page table dropped
        int freed = mm.freeProcess(2);
        check(freed == 2, "freed 2 frames belonging to P2");
        check(!mm.getPageTables().containsKey(2), "P2 page table dropped");
    }

    private static void module3SchedulerSyncTests() {
        System.out.println();
        System.out.println("=== Sync: pagesNeededFor + Scheduler.onTick hook ===");

        // pagesNeededFor: ceil(mem / PAGE_SIZE_MB) with a floor of 1.
        check(MemoryManager.pagesNeededFor(0)   == 1, "pages(0 MB) = 1 (floor)");
        check(MemoryManager.pagesNeededFor(64)  == 1, "pages(64 MB) = 1");
        check(MemoryManager.pagesNeededFor(128) == 2, "pages(128 MB) = 2");
        check(MemoryManager.pagesNeededFor(200) == 4, "pages(200 MB) = 4 (ceil)");

        // Scheduler.onTick should fire once per tick with the PCB that
        // executed during that time unit. We just count callbacks and
        // remember the last PID seen.
        ProcessManager m = new ProcessManager();
        Scheduler s = new Scheduler(m);
        s.setAlgorithm(Algorithm.FCFS);

        int[] tickCount = {0};
        int[] lastPid = {-1};
        s.setOnTick(pcb -> {
            if (pcb == null) return;
            tickCount[0]++;
            lastPid[0] = pcb.getPid();
        });

        PCB p1 = m.createProcess(1, 64, 3);  // 3 burst units
        PCB p2 = m.createProcess(1, 64, 2);  // 2 burst units

        int safety = 0;
        while (s.tick() && safety++ < 20) { /* drain */ }

        // 3 ticks ran P1, 2 ticks ran P2 -> 5 callbacks total, last PID = P2.
        check(tickCount[0] == 5, "onTick fired 5 times (3 for P1 + 2 for P2), got " + tickCount[0]);
        check(lastPid[0] == p2.getPid(), "last onTick PID was P2");
        check(p1.getState() == ProcessState.TERMINATED, "P1 terminated");
        check(p2.getState() == ProcessState.TERMINATED, "P2 terminated");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL: " + msg);
            System.exit(1);
        }
        System.out.println("OK: " + msg);
    }
}
