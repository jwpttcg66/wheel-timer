package com.snowcattle.timer;

import java.util.concurrent.TimeUnit;

/**
 * Created by jiangwenping on 16/12/29.
 */
public class WheelTimerTest {

    public static void main(String[] args) throws  Exception{
        WheelTimer<Integer> wheelTimer = new WheelTimer<Integer>(20, TimeUnit.SECONDS, 60);
        wheelTimer.addExpirationListener(new TestExpirationListener<Integer>());

        wheelTimer.start();
//        Thread.sleep(10000L);
        System.out.println("start time " + System.currentTimeMillis());
        wheelTimer.add(20);

    }
}
