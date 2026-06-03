# OS Kernel Simulator — Modules 1, 2 & 3 Report

**Course:** Operating Systems (BSCS-5A)
**Modules:** 1 — PCB & Process Management • 2 — CPU Scheduling • 3 — Memory Management (Paging + LRU)
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

## 7. Notes

Module 1 is intentionally scoped to PCB management. The structures it
introduces — particularly the `readyQueue`, `waitingQueue`, and the
`Consumer<String>` event log — are reused as-is by Module 2.

---

# Module 2 — CPU Scheduling

## 1. What Was Implemented

Module 2 adds the scheduler component on top of Module 1. The existing
`ProcessManager`, `readyQueue`, and `PCB` model are reused without forking the
data layer — `PCB` simply gained scheduling fields (`burstTime`,
`remainingTime`, `startTime`, `completionTime`, `waitingTime`,
`turnaroundTime`).

Implemented features:

- **FCFS scheduling** — non-preemptive, drains the ready queue in arrival
  order.
- **SJF scheduling** — non-preemptive Shortest Job First; on every
  dispatch the scheduler scans the ready queue and picks the PCB with
  the smallest `burstTime` (FIFO tie-break).
- **Priority scheduling** — non-preemptive; picks the ready PCB with the
  lowest `priority` number (lower = higher priority, the convention used
  throughout the project; FIFO tie-break).
- **Round Robin scheduling** — preemptive, fixed time quantum (configurable
  in the GUI).
- **Animated step-by-step execution** — `javax.swing.Timer` ticks the
  scheduler one time unit at a time so the Gantt chart fills in live.
- **Step button** — advances the simulation by exactly one time unit on
  click, useful when demoing state transitions slowly.
- **Reset** — clears the Gantt chart and returns every non-terminated PCB to
  the ready queue with full remaining time.
- **Gantt chart** — custom-painted Swing panel rendering colored time-slice
  blocks with PID labels and a time axis.
- **Per-process metrics** — Burst, Remaining, Start, Completion, Waiting,
  Turnaround Time + averages.
- **Tabbed dashboard** — Module 1 and Module 2 each live in their own tab,
  with a `Modules` menu bar for navigation (plus a placeholder `Assigned
  Topic` menu reserved for Module 5).

## 2. New / Changed Files

```
src/
├── core/
│   └── Scheduler.java          # NEW — FCFS + Round Robin tick engine
├── model/
│   └── PCB.java                # CHANGED — added scheduling fields
├── ui/
│   ├── ControlPanel.java       # CHANGED — wrapped in JTabbedPane + menu bar
│   ├── SchedulingPanel.java    # NEW — Module 2 tab UI
│   └── GanttChartPanel.java    # NEW — custom Gantt visualization
└── SmokeTest.java              # CHANGED — added FCFS and RR test cases
```

## 3. How It Works

### 3.1 Scheduler

`Scheduler` is a pure state machine — no threads, no `Thread.sleep`. It
exposes a single `tick()` method that advances simulation by one time unit.
The UI drives ticks either with a `javax.swing.Timer` (animated Run) or by
calling `tick()` directly on the Step button. This keeps the scheduling
logic deterministic, testable, and independent of any GUI loop.

Each `tick()`:

1. If no process is running, pulls the head of the ready queue, marks it
   `RUNNING`, records its start time, and resets the quantum counter.
2. Decrements `remainingTime` by 1 and increments the clock.
3. Appends to the Gantt timeline (merges with the previous segment if the
   same PID is still running, so a process that holds the CPU for 4 units
   shows up as one block `[0..4]` rather than four `[0..1] [1..2] …`).
4. If `remainingTime == 0`, marks the process `TERMINATED`, sets
   `completionTime`, `turnaroundTime`, `waitingTime`, and clears the CPU.
5. If `algorithm == ROUND_ROBIN` and `currentQuantumUsed >= timeQuantum`,
   marks the process `READY` and re-enqueues it at the tail.

### 3.2 Round Robin Walkthrough (TQ = 2)

Bursts: `P1=5, P2=3, P3=2`.

```
SCHED: t=0  dispatch P1 (remaining=5)
SCHED: t=2  P1 preempted (remaining=3)
SCHED: t=2  dispatch P2 (remaining=3)
SCHED: t=4  P2 preempted (remaining=1)
SCHED: t=4  dispatch P3 (remaining=2)
SCHED: t=6  P3 terminated (WT=4, TAT=6)
SCHED: t=6  dispatch P1 (remaining=3)
SCHED: t=8  P1 preempted (remaining=1)
SCHED: t=8  dispatch P2 (remaining=1)
SCHED: t=9  P2 terminated (WT=6, TAT=9)
SCHED: t=9  dispatch P1 (remaining=1)
SCHED: t=10 P1 terminated (WT=5, TAT=10)

Gantt: P1[0-2] P2[2-4] P3[4-6] P1[6-8] P2[8-9] P1[9-10]
```

This matches the manual's sample sequence exactly.

### 3.3 SchedulingPanel

The Module 2 tab contains a toolbar (algorithm dropdown, time-quantum
spinner, tick-delay spinner, Run / Pause / Step / Reset buttons), a status
row (CPU, ready queue, clock, average WT/TAT), the Gantt chart in a
horizontal scroll pane, and a metrics table with one row per PCB. After
every tick the panel calls `refresh()`, which also triggers the Module 1
tab's process-table repaint so both views stay in sync.

### 3.4 GanttChartPanel

Custom-painted JPanel. Each PID maps to a stable color from an 8-color
palette via `Math.floorMod(pid - 1, palette.length)`. The component
reports a preferred width based on the last segment's `end` time × 38px
per unit, so a longer schedule simply scrolls horizontally.

## 4. Demo Script for Evaluation

1. Launch the simulator → main dashboard appears with two tabs.
2. **Module 1 tab**: click *Create Process* three times — burst times
   5, 3, 2 (priority/memory whatever the defaults). Show the Process
   Table populated with state = Ready and burst values visible.
3. Switch to **Module 2 tab**. Algorithm = *First Come First Serve*. Click
   *Run*. Watch the Gantt chart fill in `P1[0-4] P2[4-7] P3[7-9]` and the
   metrics table populate with waiting / turnaround per process.
4. Click *Reset*. Change algorithm to *Round Robin*, set time quantum = 2.
   Click *Run*. Watch the Gantt fill in the preemptive sequence
   `P1 → P2 → P3 → P1 → P2 → P1` exactly as the manual specifies.
5. Click *Reset*, then *Step*, *Step*, *Step* — demonstrates one-tick
   granular advance for the examiner.

## 5. Mapping to Manual Requirements (Module 2)

| Manual requirement                       | Where it lives                          |
| ---------------------------------------- | --------------------------------------- |
| FCFS algorithm                           | `Scheduler.pickNext()` -> `q.poll()`    |
| SJF algorithm                            | `Scheduler.pickNext()` -> shortest burst|
| Priority algorithm                       | `Scheduler.pickNext()` -> lowest priority|
| Round Robin algorithm                    | `Scheduler.tick()` (quantum branch)     |
| Ready queue visualization                | `SchedulingPanel.formatReady()`         |
| Running process display                  | `SchedulingPanel.runningLabel`          |
| Gantt chart                              | `ui/GanttChartPanel.java`               |
| Updated process table (Burst, Remaining) | `ControlPanel.ProcessTableModel`        |
| State transitions during scheduling      | `Scheduler.finishRunning()`, preempt    |
| Step execution (optional)                | `SchedulingPanel.onStep()`              |
| Waiting / Turnaround metrics (optional)  | `Scheduler.finishRunning()`, metrics tab|
| Animated execution (`Thread.sleep`)      | `javax.swing.Timer` in SchedulingPanel  |
| Two menus in dashboard                   | `ControlPanel.buildMenuBar()`           |
| Separation of scheduler logic and UI     | `core/Scheduler.java` vs `ui/...`       |

---

# Module 3 — Memory Management (Paging & LRU)

## 1. What Was Implemented

Module 3 extends the simulator with a paged-memory subsystem that the user
can drive interactively. The kernel keeps a fixed number of physical
**frames**, a **page table per process**, a logical clock for LRU bookkeeping,
and counters for accesses / hits / faults. Every reference the user makes
goes through the same `accessPage(pid, page)` path so the resulting
animation and statistics are always consistent.

Implemented features:

- **Paging** — each process owns a logical page space; pages are placed in
  the first free frame, or, if memory is full, in the LRU frame.
- **Per-process page tables** — `Map<Integer, Map<Integer, Integer>>` keyed
  by PID, then by logical page number, mapping to the physical frame index.
- **LRU replacement** — every access stamps the resident frame with the
  current logical clock; eviction picks the smallest stamp.
- **Page faults vs hits** — distinguished as
  `HIT` / `FAULT_LOAD` (loaded into a free frame) / `FAULT_REPLACE`
  (eviction required), each with their own log line.
- **Three drive modes** in the Module 3 GUI:
  1. **Single Access** — pick a PID + page and click *Access* to reference
     one page at a time (best for narrating each transition during a demo).
  2. **Allocate Pages** — request *N* contiguous pages for a PID; the
     kernel internally calls `accessPage(pid, 0..N-1)` and logs every step.
  3. **Reference String** — accepts the manual's exact `1, 2, 3, 1, 4`
     notation, or cross-process `pid:page` tokens such as `1:0, 2:0, 1:1`.
- **Visual memory blocks** with stable per-PID colours (the same palette
  used by the Gantt chart, so a process keeps its colour across all three
  modules). The most recently touched frame is outlined in green; the
  frame that was just evicted is outlined in red.
- **Page-table view** showing one row per `(pid, page)` mapping with
  `Frame`, `Last Used`, `Loaded At` columns.
- **Stat cards** showing Accesses, Hits, Faults, and live Hit Rate (%).
- **Memory event log** local to the Module 3 tab and fanned out to the
  central Module 1 event log, so events are visible no matter which tab
  the user is on.
- **Cross-module integration** — *Destroy Process* in Module 1 now also
  calls `memoryManager.freeProcess(pid)`, releasing every frame the PCB
  was holding and dropping its page table.

## 2. New / Changed Files

```
src/
├── core/
│   └── MemoryManager.java        # NEW — frames + per-process page tables + LRU
├── ui/
│   ├── MemoryPanel.java          # NEW — Module 3 tab UI (blocks + table + log)
│   ├── ControlPanel.java         # CHANGED — third tab, menu item, log fan-out,
│   │                             #          destroy now also frees memory
│   └── IntroScreen.java          # CHANGED — tagline updated
└── SmokeTest.java                # CHANGED — Module 3 tests + parser tests
```

## 3. How It Works

### 3.1 MemoryManager

`MemoryManager` is the kernel-side engine. It owns:

- `Frame[] frames` — physical memory. Each `Frame` carries `pid`, `page`,
  `lastUsed`, `loadedAt`, plus transient `justAccessed` / `justReplaced`
  flags the UI uses for highlight effects.
- `Map<Integer, Map<Integer, Integer>> pageTables` — the per-process
  page tables.
- An integer `clock` that increments on every access; it is the LRU stamp.
- Counters: `accesses`, `pageHits`, `pageFaults` (and derived hit/fault
  rates).
- A `Consumer<String> logger` so the GUI subscribes without the kernel
  knowing about Swing — same pattern as `ProcessManager` and `Scheduler`.

The hot path is `accessPage(int pid, int page)`:

1. Clear transient highlight flags from the previous access.
2. Increment the access counter and the LRU clock.
3. Look up `(pid, page)` in the page table. If the resident frame is still
   ours, refresh its `lastUsed`, mark a hit, return `HIT`.
4. Otherwise count a fault. Try `findFreeFrame()`; if a free frame exists,
   load there and return `FAULT_LOAD`.
5. If no free frame exists, pick the frame with the lowest `lastUsed`
   (`findLruFrame()`), drop the victim's page-table entry, then load the
   new page into that frame and return `FAULT_REPLACE`.

Every branch writes a single log line that includes the clock, the
reference, the outcome, and (for replacements) what was evicted.

### 3.2 Manual's LRU example, step-by-step

With `capacity = 3` and reference string `1, 2, 3, 1, 4` (single process):

```
t=1  Ref P1.page1 -> FAULT (loaded into free frame 0)
t=2  Ref P1.page2 -> FAULT (loaded into free frame 1)
t=3  Ref P1.page3 -> FAULT (loaded into free frame 2)
t=4  Ref P1.page1 -> HIT  (frame 0)                       ← clock stamps page 1
t=5  Ref P1.page4 -> FAULT (LRU: evicted P1.page2 from frame 1)
```

This is the exact trace the manual asks for: at step 5 page 2 has the
oldest stamp (t=2) because page 1 was refreshed at t=4 and page 3 at t=3,
so page 2 is evicted — matching the manual's
`[P1][P2][P3] → [P1][P4][P3]` diagram.

### 3.3 MemoryPanel

The Module 3 tab follows the same visual language as the Module 2 tab:

- **Toolbar** (rounded card titled *Memory Controls*) — frame-count
  spinner + Apply, access PID/page + Access, allocate count + Allocate,
  reference-string field with default-PID spinner + Run Refs, free PID +
  Free, and a Reset button.
- **Status row** — 4 stat cards (Accesses, Hits, Faults, Hit Rate).
- **Memory blocks panel** — custom-painted grid of rounded rectangles, one
  per frame. Free frames show "Free" in dim text; occupied frames show
  `P{pid}`, `page {n}`, the frame index, and the `lastUsed` value. The
  most recently touched frame is outlined green; the just-replaced frame
  is outlined red — both flags clear automatically on the next access.
- **Page Tables table** — flat view of every `(pid, page → frame)`
  mapping, sortable per default by insertion order so newest mappings
  appear last.
- **Memory Event Log** — same look as the Module 1 event log but local to
  this tab so the user can read the sequence without switching tabs.

When the user creates new PCBs in Module 1 and switches to Module 3,
`seedPidSpinnersFromKernel()` nudges the PID spinners to the first valid
PID so they don't have to type one in.

### 3.4 Cross-module wiring

`ControlPanel` constructs one `MemoryManager(4)` and routes its logger to
both `appendLog` (the central event log) and `memoryPanel.appendLog`
(the local log). The *Destroy Process* button on the Module 1 sidebar now
calls `memoryManager.freeProcess(pid)` before `manager.destroyProcess(pid)`,
so killing a PCB also reclaims its frames — visible immediately in the
Module 3 blocks panel.

## 4. Demo Script for Evaluation (Module 3)

1. Launch the simulator → **Module 3** tab.
2. Confirm the toolbar shows `Frames: 4`. Set it to `3` and click *Apply*
   (so the memory matches the manual's example exactly).
3. In the *Reference String* field type `1, 2, 3, 1, 4` (default PID = 1)
   and click *Run Refs*. Watch the blocks fill in: faults at t=1/2/3 fill
   frames 0/1/2, t=4 is a hit on frame 0 (green outline), and t=5 evicts
   frame 1 (red outline) and loads page 4 there.
4. Check the stat cards — **Accesses 5 · Hits 1 · Faults 4 · Hit Rate
   20.0%** — and the page-table view that now lists pages 1, 4, 3 in
   frames 0, 1, 2.
5. Switch to **Module 1**, click *Create Process* a couple of times, come
   back to Module 3, and demonstrate cross-process paging: set PID = 2,
   page = 0, click *Access* — a new fault evicts the LRU frame and the
   evicted process loses that mapping in the page-table view.
6. Click *Allocate*: with PID 1 and Allocate Pages = 5 you can watch how
   memory churns on each successive reference.
7. Back on Module 1, click *Destroy Process* on a PID that holds frames
   in Module 3 — the frames return to "Free" immediately.

## 5. Mapping to Manual Requirements (Module 3)

| Manual requirement                       | Where it lives                              |
| ---------------------------------------- | ------------------------------------------- |
| Paging (pages → frames)                  | `MemoryManager.accessPage()` (load branch)  |
| Per-process page table                   | `MemoryManager.pageTables`                  |
| Free-frame allocation                    | `MemoryManager.findFreeFrame()`             |
| Page fault detection                     | `MemoryManager.AccessResult.FAULT_*`        |
| LRU page replacement                     | `MemoryManager.findLruFrame()`              |
| Memory blocks panel                      | `ui/MemoryPanel.MemoryBlocksPanel`          |
| Page table display                       | `ui/MemoryPanel.PageTableModel`             |
| Logs panel (faults / replacements)       | `ui/MemoryPanel` log area + central log     |
| Reference string driver                  | `MemoryPanel.parseReferenceString()`        |
| Visual highlight of replaced frame       | `Frame.justReplaced` flag + red outline     |
| Separation of logic and UI               | `core/MemoryManager.java` vs `ui/...`       |
| Two menus in dashboard                   | unchanged from Module 2 (`Modules` updated) |

## 6. Cross-Module Sync

Module 3 is wired into the same data flow as Modules 1 and 2 — creating a
process in Module 1 makes it instantly visible in Module 2 *and* Module 3,
and running it in Module 2 animates Module 3 live.

| Trigger                              | What happens elsewhere                                        |
| ------------------------------------ | -------------------------------------------------------------- |
| **Module 1: Create Process**         | PCB added to `ProcessManager` (visible in M2 + M1 table); `memoryManager.allocatePages(pid, ceil(mem / PAGE_SIZE_MB))` is auto-called so its pages show up in the Module 3 frames grid and page-table view. |
| **Module 1: Destroy Process**        | PCB removed; `memoryManager.freeProcess(pid)` releases its frames immediately. |
| **Module 1: Dispatch / Block / …**   | Updates the same PCBs; Module 3 re-reads them on every refresh (running-process highlight in PCB cell). |
| **Module 2: Scheduler.tick()**       | Fires `onTick(pcb)` for the PCB that executed during the time unit; `ControlPanel` references one of that PCB's pages: `accessPage(pid, (clock − 1) mod pagesFor(pid))`. With memory over-subscribed this produces live page faults / LRU evictions in Module 3 while the Gantt chart fills. |
| **Module 2: Run / Step / Reset**     | Calls a sibling-refresh callback that re-renders Module 1 *and* Module 3 (`refreshSiblingsOfScheduler`). |
| **Module 3: Reset / Free / Apply**   | Memory state changes only — never touches PCBs in M1 / M2 so the kernel side stays authoritative. |

Conversion is controlled by a single constant on `MemoryManager`:

- `MemoryManager.PAGE_SIZE_MB = 64`
- `MemoryManager.pagesNeededFor(memMB) = max(1, ceil(memMB / 64))`

So a 128 MB process gets 2 pages; a 256 MB process gets 4 pages; the
default 4-frame memory therefore fits 2 default processes before
replacements start — perfect for demonstrating LRU live during scheduling.

## 7. Tests

`SmokeTest` now covers four Module 3 scenarios:

- **LRU manual example** — `capacity=3`, refs `1, 2, 3, 1, 4`. Asserts
  the result of every step, the final counters (`accesses=5`, `hits=1`,
  `faults=4`), and that page 2 (and only page 2) is gone.
- **Reference-string parser** — verifies bare numbers vs `pid:page`
  tokens, default-PID handling, and graceful skipping of garbage tokens.
- **Multi-process + freeProcess** — allocates pages for P1 and P2,
  verifies a hit on P1.page0, forces a replacement with a P3 reference,
  then asserts `freeProcess(2)` releases both P2 frames and drops the
  P2 page table.
- **Sync glue** — verifies `MemoryManager.pagesNeededFor` rounds up
  correctly with the floor-of-1 rule and that `Scheduler.setOnTick`
  fires exactly once per executed time unit with the PCB that ran
  (e.g. with burst 3 + burst 2 in FCFS, the hook fires 5 times, last
  PID = P2). This is the exact wiring that makes Module 3 animate live
  while Module 2 schedules.

All `OK:` lines printed, the trailing `ALL SMOKE TESTS PASSED` confirms
Modules 1, 2, and 3 still pass together.

