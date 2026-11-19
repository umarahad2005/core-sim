# core-sim — OS Kernel Simulator

A Java Swing desktop app that simulates parts of an operating system kernel, built for the Operating Systems lab project (BSCS-5A). It currently covers the first three modules of the course manual (`OS_Project_Manual_BSCS-5A.pdf`): process management, CPU scheduling, and paged memory management.

## Tech stack

- Java (the IntelliJ project is set to JDK 23)
- Swing for the GUI
- No external libraries or build tool; compiled with `javac` or run from IntelliJ IDEA

## Features

The main window has one tab per module, opened from an intro screen.

**Module 1: PCB and process management**
- Process Control Block with pid, state, priority, parent/children, memory, CPU registers and I/O status
- States: New, Ready, Running, Waiting, Suspended, Terminated
- Operations: create, dispatch, destroy, suspend, resume, block, wakeup, change priority
- Live process table, ready/waiting queue summary and an event log

**Module 2: CPU scheduling**
- FCFS, SJF (non-preemptive), Priority (non-preemptive, lower value = higher priority) and Round Robin with a configurable time quantum
- Step-by-step simulation with a Gantt chart
- Average waiting time and average turnaround time

**Module 3: Memory management**
- Paging with 64 MB pages, a fixed number of physical frames, and a page table per process
- LRU page replacement, with hits and faults counted separately
- Three input modes: single page access, allocate N pages, or a reference string (e.g. `1, 2, 3, 1, 4` or `1:0, 2:0, 1:1`)
- Frame view, page-table view, and hit/fault/hit-rate stats
- Destroying a process frees its frames

## Project structure

```
.
├── OS_Project_Manual_BSCS-5A.pdf   # course project manual (manual.txt is a text copy)
└── core_sim/
    ├── report.md                   # detailed write-up of modules 1-3
    └── src/
        ├── Main.java               # entry point (intro screen, then control panel)
        ├── SmokeTest.java          # checks state transitions, scheduling and paging from code
        ├── model/                  # PCB, ProcessState
        ├── core/                   # ProcessManager, Scheduler, MemoryManager
        └── ui/                     # Swing panels, Gantt chart, theme
```

## Build and run

From the `core_sim/` directory:

```bash
javac -d out -sourcepath src src/Main.java src/model/*.java src/core/*.java src/ui/*.java
java -cp out Main
```

To run the smoke test:

```bash
javac -d out -sourcepath src src/SmokeTest.java
java -cp out SmokeTest
```

You can also open `core_sim/` in IntelliJ IDEA and run `Main.java`.

See [`core_sim/report.md`](core_sim/report.md) for how each module works and how it maps to the manual's requirements.
