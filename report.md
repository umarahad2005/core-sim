# OS Kernel Simulator — Module 1 Report

**Course:** Operating Systems (BSCS-5A)
**Module:** 1 — Process Control Block (PCB) & Process Management
**Language / Toolkit:** Java 23, Swing
**Project:** `core_sim`

---

## 1. What Was Implemented

This module establishes the foundation of the OS simulator: the Process Control
Block (PCB), the in-memory queues that the kernel uses to track processes, and a
GUI control panel for the user to drive the system. The eight required process
operations are all wired up end-to-end.

Implemented features:

- **PCB structure** — every process is represented by a `PCB` object holding the
  fields required by the manual: `pid`, `state`, `priority`, `parentId`,
  `children`, `memory`, `cpuRegisters`, and `ioStatus`.
- **Process states** — `New`, `Ready`, `Running`, `Waiting`, `Suspended`,
  `Terminated` (modelled as a `ProcessState` enum).
- **Eight operations** — Create, Dispatch (Ready → Running), Destroy, Suspend,
  Resume, Block, Wakeup, Change Priority.
- **Data structures** — an `ArrayList<PCB>` holding all processes, a
  `Queue<PCB>` ready queue, and a `Queue<PCB>` waiting queue.
- **Swing GUI** — a single `JFrame` with an operations panel on the left, a
  live process table in the centre, queue/CPU summary at the top, and an
  event log at the bottom.

## 2. Project Structure

```
core_sim/
├── src/
│   ├── Main.java                  # launches the GUI on the Swing EDT
│   ├── SmokeTest.java             # programmatic verification of state transitions
│   ├── model/
│   │   ├── PCB.java               # PCB data class
│   │   └── ProcessState.java      # enum of process states
│   ├── core/
│   │   └── ProcessManager.java    # all kernel operations + queues
│   └── ui/
│       └── ControlPanel.java      # Swing dashboard
└── report.md                      # this file
```

The split between `model`, `core`, and `ui` follows the manual's design tip
*"keep PCB class separate"* and *"separation of logic and UI"*. The
`ProcessManager` exposes a `Consumer<String>` logger so the GUI can subscribe
to kernel events without the kernel knowing about Swing.

## 3. How It Works

### 3.1 PCB

`PCB` is a plain Java data class. The constructor takes `pid`, `priority`,
`memory`, and `parentId`; `state` defaults to `NEW`, `cpuRegisters` is a
4-element array (placeholder for register snapshots), `ioStatus` defaults to
`Idle`, and a `children` list is initialised empty.

### 3.2 ProcessManager

`ProcessManager` is the kernel. It owns:

- `processes` — the master list of all live PCBs
- `readyQueue` — a `LinkedList` used as a FIFO ready queue
- `waitingQueue` — a `LinkedList` for blocked processes
- `runningProcess` — the single PCB currently on the CPU (or `null`)
- `nextPid` — a monotonically increasing PID counter

Each operation enforces a state-transition guard before mutating the PCB. For
example `blockProcess` rejects any PCB whose state is not `RUNNING`, and
`resumeProcess` rejects any PCB that is not `SUSPENDED`. Failed transitions
are logged rather than silently ignored, which makes the GUI log useful for
debugging during a demo.

### 3.3 GUI (ControlPanel)

The control panel uses a `BorderLayout`:

- **West** — vertical button column for the eight operations plus a "Clear
  Log" helper.
- **North (centre)** — three labels showing the running process, ready queue,
  and waiting queue.
- **Centre** — `JTable` backed by an `AbstractTableModel` that reads directly
  from `ProcessManager.getProcesses()`. Columns: PID, State, Priority,
  Memory, Parent, Children, I/O.
- **South (centre)** — `JTextArea` event log timestamped to the second.

When the user clicks a button that needs a target process, the panel pops a
`JOptionPane` populated with the current PIDs (preselected to the row the
user has highlighted in the table, if any). After every operation the panel
calls `refresh()`, which fires `tableModel.fireTableDataChanged()` and
re-renders the queue labels.

The application launches via `SwingUtilities.invokeLater` so the GUI is
constructed on the Event Dispatch Thread, as the Swing contract requires.

## 4. Sample Execution

Running the smoke test (`java -cp out SmokeTest`) produces:

```
LOG: Created P1 -> Ready (priority=1, mem=128MB)
LOG: Created P2 -> Ready (priority=2, mem=256MB)
LOG: Dispatched P1 -> Running
LOG: Blocked P1 -> Waiting
LOG: Woke up P1 -> Ready
LOG: Dispatched P2 -> Running
LOG: Suspended P2
LOG: Resumed P2 -> Ready
LOG: P1 priority 1 -> 9
LOG: Destroyed P1
ALL SMOKE TESTS PASSED
```

This exercises the full state-transition cycle the manual asks the demo to
cover (`New → Ready → Running → Waiting → Ready → Terminated`) plus the
suspend/resume and priority-change paths.

## 5. How to Build and Run

```bash
# from the core_sim/ directory
javac -d out -sourcepath src src/Main.java src/model/*.java src/core/*.java src/ui/*.java
java -cp out Main
```

In IntelliJ IDEA, simply right-click `Main.java` → **Run 'Main.main()'**.

## 6. Mapping to Manual Requirements

| Manual requirement                              | Where it lives                                |
| ----------------------------------------------- | --------------------------------------------- |
| PCB fields (PID, state, priority, parent, …)    | `model/PCB.java`                              |
| Process state enum                              | `model/ProcessState.java`                     |
| Create / Destroy / Suspend / Resume             | `core/ProcessManager.java`                    |
| Block / Wakeup / Change Priority                | `core/ProcessManager.java`                    |
| Ready queue + waiting queue                     | `ProcessManager.readyQueue`, `waitingQueue`   |
| Process list (`ArrayList<PCB>`)                 | `ProcessManager.processes`                    |
| Control-panel buttons                           | `ui/ControlPanel.buildUi()`                   |
| Process table display                           | `ui/ControlPanel.ProcessTableModel`           |
| State-transition guards                         | each `ProcessManager` operation               |
| Logic–UI separation                             | `core` package vs `ui` package                |

## 7. Notes & Next Steps

This module is intentionally scoped to PCB management. The structures it
introduces — particularly the `readyQueue`, `waitingQueue`, and the
`Consumer<String>` event log — are designed so Module 2 (FCFS / Round Robin
scheduling) can plug straight in without changing the data model.
