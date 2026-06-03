package model;

import java.util.ArrayList;
import java.util.List;

public class PCB {
    private final int pid;
    private ProcessState state;
    private int priority;
    private int parentId;
    private final List<Integer> children;
    private int memory;
    private final int[] cpuRegisters;
    private String ioStatus;

    private int burstTime;
    private int remainingTime;
    private int arrivalTime;
    private int startTime = -1;
    private int completionTime = -1;
    private int waitingTime;
    private int turnaroundTime;

    public PCB(int pid, int priority, int memory, int parentId) {
        this(pid, priority, memory, parentId, 0);
    }

    public PCB(int pid, int priority, int memory, int parentId, int burstTime) {
        this.pid = pid;
        this.state = ProcessState.NEW;
        this.priority = priority;
        this.memory = memory;
        this.parentId = parentId;
        this.children = new ArrayList<>();
        this.cpuRegisters = new int[]{0, 0, 0, 0};
        this.ioStatus = "Idle";
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
    }

    public int getPid() { return pid; }
    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { this.state = state; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getParentId() { return parentId; }
    public void setParentId(int parentId) { this.parentId = parentId; }
    public List<Integer> getChildren() { return children; }
    public int getMemory() { return memory; }
    public void setMemory(int memory) { this.memory = memory; }
    public int[] getCpuRegisters() { return cpuRegisters; }
    public String getIoStatus() { return ioStatus; }
    public void setIoStatus(String ioStatus) { this.ioStatus = ioStatus; }

    public int getBurstTime() { return burstTime; }
    public void setBurstTime(int burstTime) {
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
    }
    public int getRemainingTime() { return remainingTime; }
    public void setRemainingTime(int remainingTime) { this.remainingTime = remainingTime; }
    public int getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(int arrivalTime) { this.arrivalTime = arrivalTime; }
    public int getStartTime() { return startTime; }
    public void setStartTime(int startTime) { this.startTime = startTime; }
    public int getCompletionTime() { return completionTime; }
    public void setCompletionTime(int completionTime) { this.completionTime = completionTime; }
    public int getWaitingTime() { return waitingTime; }
    public void setWaitingTime(int waitingTime) { this.waitingTime = waitingTime; }
    public int getTurnaroundTime() { return turnaroundTime; }
    public void setTurnaroundTime(int turnaroundTime) { this.turnaroundTime = turnaroundTime; }

    public void resetSchedulingStats() {
        this.remainingTime = burstTime;
        this.startTime = -1;
        this.completionTime = -1;
        this.waitingTime = 0;
        this.turnaroundTime = 0;
    }

    @Override
    public String toString() {
        return "P" + pid + "(" + state + ")";
    }
}
