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

    public PCB(int pid, int priority, int memory, int parentId) {
        this.pid = pid;
        this.state = ProcessState.NEW;
        this.priority = priority;
        this.memory = memory;
        this.parentId = parentId;
        this.children = new ArrayList<>();
        this.cpuRegisters = new int[]{0, 0, 0, 0};
        this.ioStatus = "Idle";
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

    @Override
    public String toString() {
        return "P" + pid + "(" + state + ")";
    }
}
