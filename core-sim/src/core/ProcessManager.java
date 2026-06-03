package core;

import model.PCB;
import model.ProcessState;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

public class ProcessManager {
    private final List<PCB> processes = new ArrayList<>();
    private final Queue<PCB> readyQueue = new LinkedList<>();
    private final Queue<PCB> waitingQueue = new LinkedList<>();
    private PCB runningProcess = null;
    private int nextPid = 1;
    private Consumer<String> logger = msg -> {};

    public void setLogger(Consumer<String> logger) {
        this.logger = logger != null ? logger : msg -> {};
    }

    public List<PCB> getProcesses() { return processes; }
    public Queue<PCB> getReadyQueue() { return readyQueue; }
    public Queue<PCB> getWaitingQueue() { return waitingQueue; }
    public PCB getRunningProcess() { return runningProcess; }

    public PCB createProcess(int priority, int memory) {
        PCB pcb = new PCB(nextPid++, priority, memory, 0);
        pcb.setState(ProcessState.READY);
        processes.add(pcb);
        readyQueue.add(pcb);
        log("Created P" + pcb.getPid() + " -> Ready (priority=" + priority + ", mem=" + memory + "MB)");
        return pcb;
    }

    public boolean destroyProcess(int pid) {
        PCB pcb = findProcess(pid);
        if (pcb == null) {
            log("Destroy failed: P" + pid + " not found");
            return false;
        }
        pcb.setState(ProcessState.TERMINATED);
        readyQueue.remove(pcb);
        waitingQueue.remove(pcb);
        if (runningProcess == pcb) runningProcess = null;
        processes.remove(pcb);
        log("Destroyed P" + pid);
        return true;
    }

    public boolean dispatch() {
        if (runningProcess != null) {
            log("Dispatch skipped: P" + runningProcess.getPid() + " already running");
            return false;
        }
        PCB next = readyQueue.poll();
        if (next == null) {
            log("Dispatch skipped: ready queue empty");
            return false;
        }
        next.setState(ProcessState.RUNNING);
        runningProcess = next;
        log("Dispatched P" + next.getPid() + " -> Running");
        return true;
    }

    public boolean suspendProcess(int pid) {
        PCB pcb = findProcess(pid);
        if (pcb == null || pcb.getState() == ProcessState.TERMINATED) {
            log("Suspend failed: P" + pid);
            return false;
        }
        readyQueue.remove(pcb);
        waitingQueue.remove(pcb);
        if (runningProcess == pcb) runningProcess = null;
        pcb.setState(ProcessState.SUSPENDED);
        log("Suspended P" + pid);
        return true;
    }

    public boolean resumeProcess(int pid) {
        PCB pcb = findProcess(pid);
        if (pcb == null || pcb.getState() != ProcessState.SUSPENDED) {
            log("Resume failed: P" + pid + " not suspended");
            return false;
        }
        pcb.setState(ProcessState.READY);
        readyQueue.add(pcb);
        log("Resumed P" + pid + " -> Ready");
        return true;
    }

    public boolean blockProcess(int pid) {
        PCB pcb = findProcess(pid);
        if (pcb == null || pcb.getState() != ProcessState.RUNNING) {
            log("Block failed: P" + pid + " not running");
            return false;
        }
        pcb.setState(ProcessState.WAITING);
        pcb.setIoStatus("Waiting on I/O");
        waitingQueue.add(pcb);
        if (runningProcess == pcb) runningProcess = null;
        log("Blocked P" + pid + " -> Waiting");
        return true;
    }

    public boolean wakeupProcess(int pid) {
        PCB pcb = findProcess(pid);
        if (pcb == null || pcb.getState() != ProcessState.WAITING) {
            log("Wakeup failed: P" + pid + " not waiting");
            return false;
        }
        waitingQueue.remove(pcb);
        pcb.setState(ProcessState.READY);
        pcb.setIoStatus("Idle");
        readyQueue.add(pcb);
        log("Woke up P" + pid + " -> Ready");
        return true;
    }

    public boolean changePriority(int pid, int newPriority) {
        PCB pcb = findProcess(pid);
        if (pcb == null) {
            log("Priority change failed: P" + pid + " not found");
            return false;
        }
        int old = pcb.getPriority();
        pcb.setPriority(newPriority);
        log("P" + pid + " priority " + old + " -> " + newPriority);
        return true;
    }

    public PCB findProcess(int pid) {
        for (PCB p : processes) if (p.getPid() == pid) return p;
        return null;
    }

    private void log(String msg) {
        logger.accept(msg);
    }
}
