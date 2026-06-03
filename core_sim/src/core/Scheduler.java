package core;

import model.PCB;
import model.ProcessState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Scheduler {

    public enum Algorithm {
        FCFS("First Come First Serve"),
        ROUND_ROBIN("Round Robin");
        private final String label;
        Algorithm(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static final class GanttSegment {
        public final int pid;
        public final int start;
        public final int end;
        public GanttSegment(int pid, int start, int end) {
            this.pid = pid; this.start = start; this.end = end;
        }
        public int length() { return end - start; }
    }

    private final ProcessManager manager;
    private Algorithm algorithm = Algorithm.FCFS;
    private int timeQuantum = 2;
    private int clock = 0;
    private int currentQuantumUsed = 0;
    private final List<GanttSegment> gantt = new ArrayList<>();
    private final List<PCB> completed = new ArrayList<>();
    private Consumer<String> logger = m -> {};
    /** Fired after each tick with the PCB that executed during that time unit. */
    private Consumer<PCB> onTick = p -> {};

    public Scheduler(ProcessManager manager) {
        this.manager = manager;
    }

    public void setLogger(Consumer<String> l) { this.logger = l != null ? l : m -> {}; }
    /** Subscribe to per-tick PCB execution (used to sync Module 3 page accesses). */
    public void setOnTick(Consumer<PCB> c) { this.onTick = c != null ? c : p -> {}; }
    public void setAlgorithm(Algorithm a) { this.algorithm = a; }
    public Algorithm getAlgorithm() { return algorithm; }
    public void setTimeQuantum(int q) { this.timeQuantum = Math.max(1, q); }
    public int getTimeQuantum() { return timeQuantum; }
    public int getClock() { return clock; }
    public List<GanttSegment> getGantt() { return gantt; }
    public List<PCB> getCompleted() { return completed; }

    public void reset() {
        clock = 0;
        currentQuantumUsed = 0;
        gantt.clear();
        completed.clear();
        manager.resetSchedulingState();
        logger.accept("Scheduler reset (clock=0, " + algorithm + ", TQ=" + timeQuantum + ")");
    }

    public boolean isComplete() {
        return manager.getRunningProcess() == null && manager.getReadyQueue().isEmpty();
    }

    /** Advance simulation by one time unit. Returns false if nothing more to schedule. */
    public boolean tick() {
        PCB running = manager.getRunningProcess();

        if (running == null) {
            running = manager.getReadyQueue().poll();
            if (running == null) return false;
            running.setState(ProcessState.RUNNING);
            if (running.getStartTime() < 0) running.setStartTime(clock);
            manager.setRunningProcess(running);
            currentQuantumUsed = 0;
            logger.accept("t=" + clock + " : dispatch P" + running.getPid()
                    + " (remaining=" + running.getRemainingTime() + ")");
        }

        if (running.getRemainingTime() <= 0) {
            finishRunning(running);
            return true;
        }

        int newRemaining = running.getRemainingTime() - 1;
        running.setRemainingTime(newRemaining);
        currentQuantumUsed++;
        clock++;
        appendGantt(running.getPid());

        if (newRemaining <= 0) {
            finishRunning(running);
        } else if (algorithm == Algorithm.ROUND_ROBIN && currentQuantumUsed >= timeQuantum) {
            running.setState(ProcessState.READY);
            manager.getReadyQueue().add(running);
            manager.setRunningProcess(null);
            currentQuantumUsed = 0;
            logger.accept("t=" + clock + " : P" + running.getPid()
                    + " preempted (quantum expired, remaining=" + newRemaining + ")");
        }
        // Notify subscribers (Module 3 listens to this to reference one of the
        // process's pages — produces hits while the working set is resident
        // and faults / LRU replacements when memory is over-subscribed).
        onTick.accept(running);
        return true;
    }

    private void finishRunning(PCB pcb) {
        pcb.setState(ProcessState.TERMINATED);
        pcb.setCompletionTime(clock);
        int turnaround = clock - pcb.getArrivalTime();
        pcb.setTurnaroundTime(turnaround);
        pcb.setWaitingTime(turnaround - pcb.getBurstTime());
        completed.add(pcb);
        manager.setRunningProcess(null);
        currentQuantumUsed = 0;
        logger.accept("t=" + clock + " : P" + pcb.getPid() + " terminated"
                + " (WT=" + pcb.getWaitingTime() + ", TAT=" + pcb.getTurnaroundTime() + ")");
    }

    private void appendGantt(int pid) {
        if (!gantt.isEmpty()) {
            GanttSegment last = gantt.get(gantt.size() - 1);
            if (last.pid == pid && last.end == clock - 1) {
                gantt.set(gantt.size() - 1, new GanttSegment(pid, last.start, clock));
                return;
            }
        }
        gantt.add(new GanttSegment(pid, clock - 1, clock));
    }

    public double getAverageWaitingTime() {
        if (completed.isEmpty()) return 0;
        return completed.stream().mapToInt(PCB::getWaitingTime).average().orElse(0);
    }

    public double getAverageTurnaroundTime() {
        if (completed.isEmpty()) return 0;
        return completed.stream().mapToInt(PCB::getTurnaroundTime).average().orElse(0);
    }
}
