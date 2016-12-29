package com.snowcattle.timer;

import java.util.ArrayList;
import java.util.Map;
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
 */
public class TimingWheel<E> {
    /**
     * 每次走的精度 tickDuration（一个tick的持续时间）
     */
    private long tickDuration;
    /**
     * 每次需要转多少圈 l（一轮的tick数
     */
    private int ticksPerWheel;
    private volatile  int currentTickIndex = 0;
    private CopyOnWriteArrayList<ExpirationListener<E>> expirationListeners = new CopyOnWriteArrayList<ExpirationListener<E>>();
    private ArrayList<TimeSlot<E>> wheel;

    /**
     * 指针
     */
    private Map<E, TimeSlot<E>> indicator = new ConcurrentHashMap<E, TimeSlot<E>>();
    private AtomicBoolean shutdown = new AtomicBoolean(false);
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Thread workThread;

    public TimingWheel(int tickDuration, int ticksPerWheel, TimeUnit timeUnit){
        if(timeUnit == null){
            throw new NullPointerException("time unit");
        }

        if(tickDuration <=0){
            throw new IllegalArgumentException("tickDuration must be greater than 0" + tickDuration);
        }

        this.wheel = new ArrayList<TimeSlot<E>>();
        this.tickDuration = TimeUnit.MICROSECONDS.convert(tickDuration, timeUnit);
        this.ticksPerWheel = ticksPerWheel;
        for(int i = 0; i < this.ticksPerWheel; i++){
            wheel.add(new TimeSlot<E>(i));
        }

        wheel.trimToSize();

        workThread = new Thread(new TickWorker(), "wheel-timer");
    }

    public void start(){
        if (shutdown.get()){
            throw new IllegalStateException("the thread is stoped");
        }

        if(!workThread.isAlive()){
            workThread.start();
        }
    }

    public boolean stop(){
        if(!shutdown.compareAndSet(false, true)){
            return false;
        }
        boolean interrupted = false;
        while (workThread.isAlive()){
            workThread.interrupt();
            try {
                workThread.join(1000);
            }catch (Exception e){
                interrupted = true;
            }
        }

        if(interrupted){
            Thread.currentThread().interrupt();
        }

        return true;
    }

    public void addExpirationListener(ExpirationListener<E> listener){
        expirationListeners.add(listener);
    }

    public void removeExpirationListner(ExpirationListener<E> listener){
        expirationListeners.remove(listener);
    }

    public void add(E e){
        synchronized (e){

        }
    }


    private class TickWorker implements Runnable{

        /**
         * 启动时间
         */
        private long startTime;

        /**
         * 运行次数
         */
        private long tick=1L;


        public void run() {
            startTime = System.currentTimeMillis();
            tick = 1;
            for ( int i = 0; !shutdown.get();i++){
                if(i == wheel.size()){
                    i = 0;
                }

                lock.writeLock().lock();
                try{
                    currentTickIndex = i;
                }catch (Exception e){

                }finally {
                    lock.writeLock().unlock();
                }

                waitForNexTick();
            }
        }

        private void waitForNexTick(){
            for (;;){
                long currentTime = System.currentTimeMillis();
                long sleepTime = tickDuration * tick - (currentTime - startTime);
                if(sleepTime <= 0){
                    break;
                }

                try {
                    Thread.sleep(sleepTime);
                }catch (Exception e){

                }finally {
                    break;
                }
            }
        }
    }
}
