package com.snowcattle.timer;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by jiangwenping on 16/12/29.
 * 定时轮是一种数据结构，其主体是一个循环列表（circular buffer），每个列表中包含一个称之为槽（slot）的结构（附图）。
 至于 slot 的具体结构依赖具体应用场景。
 以本文开头所述的管理大量连接 timeout 的场景为例，描述一下 timing wheel的具体实现细节。

 定时轮的工作原理可以类比于始终，如上图箭头（指针）按某一个方向按固定频率轮动，每一次跳动称为一个 tick。
 这样可以看出定时轮由个3个重要的属性参数，ticksPerWheel（一轮的tick数），tickDuration（一个tick的持续时间）
 以及 timeUnit（时间单位），例如 当ticksPerWheel=60，tickDuration=1，timeUnit=秒，这就和现实中的始终的秒针走动完全类似了。



 /**
 * A timing-wheel optimized for approximated I/O timeout scheduling.<br>
 * {@link TimingWheel} creates a new thread whenever it is instantiated and started, so don't create many instances.
 * <p>
 * <b>The classic usage as follows:</b><br>
 * <li>using timing-wheel manage any object timeout</li>
 * <pre>
 *    // Create a timing-wheel with 60 ticks, and every tick is 1 second.
 *    private static final TimingWheel<CometChannel> TIMING_WHEEL = new TimingWheel<CometChannel>(1, 60, TimeUnit.SECONDS);
 *
 *    // Add expiration listener and start the timing-wheel.
 *    static {
 *      TIMING_WHEEL.addExpirationListener(new YourExpirationListener());
 *      TIMING_WHEEL.start();
 *    }
 *
 *    // Add one element to be timeout approximated after 60 seconds
 *    TIMING_WHEEL.add(e);
 *
 *    // Anytime you can cancel count down timer for element e like this
 *    TIMING_WHEEL.remove(e);
 * </pre>
 *
 * After expiration occurs, the {@link ExpirationListener} interface will be invoked and the expired object will be
 * the argument for callback method {@link ExpirationListener#expired(Object)}
 * <p>
 * {@link TimingWheel} is based on <a href="http://cseweb.ucsd.edu/users/varghese/">George Varghese</a> and Tony Lauck's paper,
 * <a href="http://cseweb.ucsd.edu/users/varghese/PAPERS/twheel.ps.Z">'Hashed and Hierarchical Timing Wheels: data structures
 * to efficiently implement a timer facility'</a>.  More comprehensive slides are located <a href="http://www.cse.wustl.edu/~cdgill/courses/cs6874/TimingWheels.ppt">here</a>.
 *
*/
public class WheelTimer<E> {
    /**
     * 每次走的精度 tickDuration（一个tick的持续时间）
     */
    private long tickDuration;
    /**
     * 每次需要转多少圈 l（一轮的tick数
     */
    private int ticksPerWheel;
    private volatile int currentTickIndex = 0;
    private CopyOnWriteArrayList<ExpirationListener<E>> expirationListeners = new CopyOnWriteArrayList<ExpirationListener<E>>();
    private ArrayList<TimeSlot<E>> wheel;

    /**
     * 指针(全局仪表盘)
     */
    private Map<E, TimeSlot<E>> indicator = new ConcurrentHashMap<E, TimeSlot<E>>();
    private AtomicBoolean shutdown = new AtomicBoolean(false);
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Thread workThread;

    public WheelTimer(int tickDuration, int ticksPerWheel, TimeUnit timeUnit) {
        if (timeUnit == null) {
            throw new NullPointerException("time unit");
        }

        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be greater than 0" + tickDuration);
        }

        this.wheel = new ArrayList<TimeSlot<E>>();
        this.tickDuration = TimeUnit.MICROSECONDS.convert(tickDuration, timeUnit);
        this.ticksPerWheel = ticksPerWheel;
        for (int i = 0; i < this.ticksPerWheel; i++) {
            wheel.add(new TimeSlot<E>(i));
        }

        wheel.trimToSize();

        workThread = new Thread(new TickWorker(), "wheel-timer");
    }

    public void start() {
        if (shutdown.get()) {
            throw new IllegalStateException("the thread is stoped");
        }

        if (!workThread.isAlive()) {
            workThread.start();
        }
    }

    public boolean stop() {
        if (!shutdown.compareAndSet(false, true)) {
            return false;
        }
        boolean interrupted = false;
        while (workThread.isAlive()) {
            workThread.interrupt();
            try {
                workThread.join(1000);
            } catch (Exception e) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return true;
    }

    public void addExpirationListener(ExpirationListener<E> listener) {
        expirationListeners.add(listener);
    }

    public void removeExpirationListner(ExpirationListener<E> listener) {
        expirationListeners.remove(listener);
    }

    public long add(E e) {
        synchronized (e) {
            checkAdd(e);
            int previousTickIndex = getPreviousTickIndex();
            TimeSlot<E> timeSlotSet = wheel.get(previousTickIndex);
            timeSlotSet.add(e);
            indicator.put(e, timeSlotSet);
            return (ticksPerWheel - 1) * tickDuration;
        }
    }

    public boolean remove(E e) {
        synchronized (e) {
            TimeSlot<E> timeSlot = indicator.get(e);
            if (timeSlot == null) {
                return false;
            }

            indicator.remove(e);
            return timeSlot.remove(e);
        }
    }

    public void notifyExpired(int idx) {
        TimeSlot<E> timeSlot = wheel.get(idx);
        Set<E> elements = timeSlot.getElements();
        for (E e : elements) {
            timeSlot.remove(e);
            synchronized (e) {
                TimeSlot<E> latestSLot = indicator.get(e);
                if (latestSLot.equals(timeSlot)) {
                    indicator.remove(e);
                }
            }

            for (ExpirationListener<E> listener : expirationListeners) {
                listener.expired(e);
            }
        }
    }

    private int getPreviousTickIndex() {
        lock.readLock().lock();
        try {
            int cti = currentTickIndex;
            if (cti == 0) {
                return ticksPerWheel - 1;
            }
            return cti - 1;
        } catch (Exception e) {

        } finally {
            lock.readLock().unlock();
        }

        return currentTickIndex - 1;
    }

    private void checkAdd(E e) {
        TimeSlot<E> slot = indicator.get(e);
        if (slot != null) {
            slot.remove(e);
        }
    }


    private class TickWorker implements Runnable {

        /**
         * 启动时间
         */
        private long startTime;

        /**
         * 运行次数
         */
        private long tick = 1L;


        public void run() {
            startTime = System.currentTimeMillis();
            tick = 1;
            for (int i = 0; !shutdown.get(); i++) {
                if (i == wheel.size()) {
                    i = 0;
                }

                lock.writeLock().lock();
                try {
                    currentTickIndex = i;
                } catch (Exception e) {

                } finally {
                    lock.writeLock().unlock();
                }

                notifyExpired(currentTickIndex);
                waitForNexTick();
            }
        }

        private void waitForNexTick() {
            for (; ; ) {
                long currentTime = System.currentTimeMillis();
                long sleepTime = tickDuration * tick - (currentTime - startTime);
                if (sleepTime <= 0) {
                    break;
                }

                try {
                    Thread.sleep(sleepTime);
                } catch (Exception e) {

                } finally {
                    break;
                }
            }
        }
    }
}
