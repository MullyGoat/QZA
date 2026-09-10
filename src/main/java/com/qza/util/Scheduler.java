package com.qza.util;

import com.qza.QZA;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tiny client-tick scheduler. 20 ticks == 1 second.
 *
 * Everything QZA sends to the server goes through a delay so nothing ever
 * reacts to a chat line on the same tick it arrived -- instant reactions are
 * exactly the pattern anti-cheat looks for.
 */
public final class Scheduler {

    public static final int TICKS_PER_SECOND = 20;

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tickCount;

    private Scheduler() {
    }

    private static final class Task {
        final long dueTick;
        final Runnable action;

        Task(long dueTick, Runnable action) {
            this.dueTick = dueTick;
            this.action = action;
        }
    }

    public static void schedule(long delayTicks, Runnable action) {
        synchronized (Scheduler.class) {
            TASKS.add(new Task(tickCount + Math.max(1, delayTicks), action));
        }
    }

    /** Call once per client tick. Runs whatever is due. */
    public static void tick() {
        List<Runnable> due = null;
        synchronized (Scheduler.class) {
            tickCount++;
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task task = it.next();
                if (task.dueTick <= tickCount) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(task.action);
                    it.remove();
                }
            }
        }
        // Run outside the lock so a task may schedule more work.
        if (due != null) {
            for (Runnable action : due) {
                try {
                    action.run();
                } catch (Throwable t) {
                    QZA.LOGGER.error("Scheduled task failed", t);
                }
            }
        }
    }

    public static void clear() {
        synchronized (Scheduler.class) {
            TASKS.clear();
        }
    }
}
