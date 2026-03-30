package com.jmeternext.engine.service.shape;

import com.jmeternext.engine.service.VirtualUserExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bridges a {@link LoadShape} to the engine's {@link VirtualUserExecutor}.
 *
 * <p>Runs a background ticker thread (platform thread via {@link ScheduledExecutorService})
 * that calls {@link LoadShape#tick} every second. Based on the returned
 * {@link LoadShape.ShapeTick}, it spawns or stops virtual users to match the
 * target count.
 *
 * <p>Each spawned virtual user is given a shared {@link AtomicBoolean} cancellation
 * flag. When the shape requests fewer users than are running, excess VUs are
 * signalled to stop by setting their flag. The VU task created by the
 * {@code vuTaskFactory} must check this flag periodically (e.g., between iterations)
 * and exit when it becomes {@code true}.
 *
 * <p>Uses {@link ReentrantLock} to avoid virtual-thread pinning (see JEP-491).
 *
 * <p>Thread safety: all public methods are safe to call from any thread.
 */
public final class LoadShapeController implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LoadShapeController.class.getName());

    private final LoadShape shape;
    private final VirtualUserExecutor executor;
    private final Supplier<Runnable> vuTaskFactory;
    private final ScheduledExecutorService ticker;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Tracks a spawned virtual user: its cancellation flag and its future.
     */
    private record ManagedVU(AtomicBoolean cancelled, Future<?> future) {}

    private final ConcurrentLinkedQueue<ManagedVU> managedVUs = new ConcurrentLinkedQueue<>();
    private volatile Instant startTime;
    private volatile boolean running = false;

    /**
     * Constructs a controller that will drive the given shape against the executor.
     *
     * <p>The {@code vuTaskFactory} must produce {@link Runnable} instances that
     * accept an {@link AtomicBoolean} cancellation signal. The controller wraps
     * each task so the cancellation flag is checked; the task itself should also
     * check the flag between iterations for responsive shutdown.
     *
     * @param shape         the load shape to drive; must not be {@code null}
     * @param executor      the executor to spawn VUs on; must not be {@code null}
     * @param vuTaskFactory factory producing VU task runnables; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public LoadShapeController(LoadShape shape, VirtualUserExecutor executor,
                               Supplier<Runnable> vuTaskFactory) {
        this.shape = Objects.requireNonNull(shape, "shape must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.vuTaskFactory = Objects.requireNonNull(vuTaskFactory, "vuTaskFactory must not be null");
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "shape-controller-ticker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the shape controller. The ticker will begin calling
     * {@link LoadShape#tick} every second.
     *
     * @throws IllegalStateException if already running
     */
    public void start() {
        lock.lock();
        try {
            if (running) {
                throw new IllegalStateException("Controller is already running");
            }
            running = true;
            startTime = Instant.now();
            ticker.scheduleAtFixedRate(this::onTick, 0, 1, TimeUnit.SECONDS);
            LOG.info("LoadShapeController started");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops the controller, cancels the ticker, and signals all managed VUs to stop.
     *
     * <p>This method is idempotent: calling it multiple times has no additional effect.
     */
    public void stop() {
        lock.lock();
        try {
            if (!running) {
                return;
            }
            running = false;
            ticker.shutdownNow();
            cancelAllVUs();
            LOG.info("LoadShapeController stopped");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether the controller is currently running.
     *
     * @return {@code true} if started and not yet stopped
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Closes this controller by calling {@link #stop()}.
     *
     * <p>Allows use in try-with-resources blocks.
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Called every second by the scheduled ticker. Queries the shape, then
     * adjusts the VU count by spawning or cancelling as needed.
     */
    private void onTick() {
        lock.lock();
        try {
            if (!running) {
                return;
            }

            // Purge completed VUs from the tracking queue
            managedVUs.removeIf(vu -> vu.future().isDone());

            Duration elapsed = Duration.between(startTime, Instant.now());
            int currentUsers = managedVUs.size();

            LoadShape.ShapeTick tick;
            try {
                tick = shape.tick(elapsed, currentUsers);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "LoadShape.tick threw an exception; stopping controller", e);
                running = false;
                ticker.shutdownNow();
                cancelAllVUs();
                return;
            }

            if (tick == null) {
                LOG.info("LoadShape returned null; stopping all VUs");
                running = false;
                ticker.shutdownNow();
                cancelAllVUs();
                return;
            }

            int target = tick.targetUsers();
            int delta = target - currentUsers;

            if (delta > 0) {
                spawnVUs(delta);
            } else if (delta < 0) {
                cancelExcessVUs(-delta);
            }

            LOG.fine(() -> String.format(
                    "Tick: elapsed=%ds target=%d current=%d delta=%+d",
                    elapsed.toSeconds(), target, currentUsers, delta));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error in onTick", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Spawns {@code count} new virtual users via the executor.
     *
     * @param count number of VUs to spawn
     */
    private void spawnVUs(int count) {
        for (int i = 0; i < count; i++) {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            Runnable task = vuTaskFactory.get();
            Runnable wrappedTask = () -> {
                while (!cancelled.get() && !Thread.currentThread().isInterrupted()) {
                    task.run();
                }
            };
            try {
                Future<?> future = executor.submitVirtualUser(wrappedTask);
                managedVUs.add(new ManagedVU(cancelled, future));
            } catch (IllegalStateException e) {
                LOG.warning("Could not spawn VU: executor is shut down");
                break;
            }
        }
    }

    /**
     * Signals {@code count} virtual users to stop by setting their cancellation flag.
     *
     * <p>Removes from the queue in FIFO order (oldest VUs first).
     *
     * @param count number of excess VUs to cancel
     */
    private void cancelExcessVUs(int count) {
        for (int i = 0; i < count; i++) {
            ManagedVU vu = managedVUs.poll();
            if (vu == null) {
                break;
            }
            vu.cancelled().set(true);
        }
    }

    /**
     * Signals all managed VUs to stop.
     */
    private void cancelAllVUs() {
        ManagedVU vu;
        while ((vu = managedVUs.poll()) != null) {
            vu.cancelled().set(true);
        }
    }
}
