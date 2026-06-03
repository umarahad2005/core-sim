package core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Module 3 — Memory Management.
 *
 * Pure paging + LRU replacement simulator. The kernel reasons about logical
 * pages owned by processes; this manager maps each (pid, page) pair to one of
 * a fixed number of physical {@link Frame frames} and evicts the
 * least-recently-used frame when memory is full.
 *
 * The class is intentionally framework-free (no Swing imports) so the GUI can
 * drive it the same way {@link Scheduler} is driven from
 * {@link ui.SchedulingPanel}.
 */
public class MemoryManager {

    /**
     * Logical page size in megabytes. Used by {@link #pagesNeededFor(int)} so
     * the rest of the project can convert a PCB's {@code memory} field into
     * a page count without hard-coding the conversion in three places.
     */
    public static final int PAGE_SIZE_MB = 64;

    /** Number of pages a process with {@code memMB} memory needs (at least 1). */
    public static int pagesNeededFor(int memMB) {
        if (memMB <= 0) return 1;
        return Math.max(1, (int) Math.ceil(memMB / (double) PAGE_SIZE_MB));
    }

    /** Physical memory frame. Mutable so the GUI can read it without copying. */
    public static final class Frame {
        public int pid = -1;             // -1 => free
        public int page = -1;
        public int lastUsed = -1;        // monotonically increasing logical clock
        public int loadedAt = -1;
        public boolean justAccessed = false;
        public boolean justReplaced = false;

        public boolean isFree() { return pid < 0; }

        public void clear() {
            pid = -1; page = -1; lastUsed = -1; loadedAt = -1;
            justAccessed = false; justReplaced = false;
        }
    }

    /** Outcome of a single page reference, useful for tests and UI highlights. */
    public enum AccessResult { HIT, FAULT_LOAD, FAULT_REPLACE }

    private int frameCount;
    private Frame[] frames;
    /** pid -> (page -> frame index). LinkedHashMap keeps display order stable. */
    private final Map<Integer, Map<Integer, Integer>> pageTables = new LinkedHashMap<>();
    private int clock = 0;          // LRU logical clock
    private int accesses = 0;
    private int pageHits = 0;
    private int pageFaults = 0;
    private int lastTouchedFrame = -1;
    private int lastEvictedFrame = -1;
    private Consumer<String> logger = m -> {};

    public MemoryManager(int frameCount) {
        resize(frameCount);
    }

    public void setLogger(Consumer<String> l) {
        this.logger = l != null ? l : m -> {};
    }

    public int getFrameCount()   { return frameCount; }
    public Frame[] getFrames()   { return frames; }
    public Map<Integer, Map<Integer, Integer>> getPageTables() { return pageTables; }
    public int getClock()        { return clock; }
    public int getAccesses()     { return accesses; }
    public int getPageHits()     { return pageHits; }
    public int getPageFaults()   { return pageFaults; }
    public int getLastTouchedFrame() { return lastTouchedFrame; }
    public int getLastEvictedFrame() { return lastEvictedFrame; }

    public double getHitRate()   { return accesses == 0 ? 0 : 100.0 * pageHits   / accesses; }
    public double getFaultRate() { return accesses == 0 ? 0 : 100.0 * pageFaults / accesses; }

    /** Re-create the memory array at a new size. Wipes all state. */
    public void resize(int newFrameCount) {
        this.frameCount = Math.max(1, newFrameCount);
        this.frames = new Frame[this.frameCount];
        for (int i = 0; i < this.frameCount; i++) frames[i] = new Frame();
        pageTables.clear();
        clock = 0;
        accesses = 0;
        pageHits = 0;
        pageFaults = 0;
        lastTouchedFrame = -1;
        lastEvictedFrame = -1;
        logger.accept("Memory initialised with " + this.frameCount + " frame(s)");
    }

    /** Reset all state but keep the current frame count. */
    public void reset() {
        resize(this.frameCount);
    }

    /**
     * Reference a (pid, page). Returns {@link AccessResult#HIT} if the page
     * was already resident, {@link AccessResult#FAULT_LOAD} on a fault that
     * loaded into a free frame, or {@link AccessResult#FAULT_REPLACE} on a
     * fault that had to evict an LRU frame.
     */
    public AccessResult accessPage(int pid, int page) {
        clearTransientFlags();
        accesses++;
        clock++;

        Map<Integer, Integer> pt = pageTables.computeIfAbsent(pid, k -> new LinkedHashMap<>());
        Integer frameIdx = pt.get(page);
        if (frameIdx != null
                && frameIdx < frameCount
                && frames[frameIdx].pid == pid
                && frames[frameIdx].page == page) {
            pageHits++;
            frames[frameIdx].lastUsed = clock;
            frames[frameIdx].justAccessed = true;
            lastTouchedFrame = frameIdx;
            lastEvictedFrame = -1;
            logger.accept(String.format(
                    "t=%d  Ref P%d.page%d -> HIT  (frame %d)", clock, pid, page, frameIdx));
            return AccessResult.HIT;
        }

        pageFaults++;
        int freeIdx = findFreeFrame();
        if (freeIdx >= 0) {
            loadInto(freeIdx, pid, page);
            lastTouchedFrame = freeIdx;
            lastEvictedFrame = -1;
            logger.accept(String.format(
                    "t=%d  Ref P%d.page%d -> FAULT (loaded into free frame %d)",
                    clock, pid, page, freeIdx));
            return AccessResult.FAULT_LOAD;
        }

        int lruIdx = findLruFrame();
        Frame victim = frames[lruIdx];
        int victimPid = victim.pid;
        int victimPage = victim.page;
        Map<Integer, Integer> victimPt = pageTables.get(victimPid);
        if (victimPt != null) victimPt.remove(victimPage);

        loadInto(lruIdx, pid, page);
        frames[lruIdx].justReplaced = true;
        lastTouchedFrame = lruIdx;
        lastEvictedFrame = lruIdx;
        logger.accept(String.format(
                "t=%d  Ref P%d.page%d -> FAULT (LRU: evicted P%d.page%d from frame %d)",
                clock, pid, page, victimPid, victimPage, lruIdx));
        return AccessResult.FAULT_REPLACE;
    }

    /**
     * Bulk-allocate {@code pageCount} pages (0..pageCount-1) for {@code pid}.
     * Mirrors the manual's "process needs N pages" walkthrough.
     */
    public void allocatePages(int pid, int pageCount) {
        logger.accept("Allocate request: P" + pid + " needs " + pageCount + " page(s)");
        for (int p = 0; p < pageCount; p++) accessPage(pid, p);
    }

    /** Release every frame owned by {@code pid} and drop its page table. */
    public int freeProcess(int pid) {
        int freed = 0;
        for (Frame f : frames) {
            if (f.pid == pid) { f.clear(); freed++; }
        }
        pageTables.remove(pid);
        logger.accept("Freed P" + pid + " (" + freed + " frame(s) released)");
        return freed;
    }

    private int findFreeFrame() {
        for (int i = 0; i < frameCount; i++) if (frames[i].isFree()) return i;
        return -1;
    }

    private int findLruFrame() {
        int best = 0;
        int bestUsed = frames[0].lastUsed;
        for (int i = 1; i < frameCount; i++) {
            if (frames[i].lastUsed < bestUsed) {
                bestUsed = frames[i].lastUsed;
                best = i;
            }
        }
        return best;
    }

    private void loadInto(int frameIdx, int pid, int page) {
        Frame f = frames[frameIdx];
        f.pid = pid;
        f.page = page;
        f.loadedAt = clock;
        f.lastUsed = clock;
        f.justAccessed = true;
        pageTables.computeIfAbsent(pid, k -> new LinkedHashMap<>()).put(page, frameIdx);
    }

    private void clearTransientFlags() {
        for (Frame f : frames) { f.justAccessed = false; f.justReplaced = false; }
    }
}
