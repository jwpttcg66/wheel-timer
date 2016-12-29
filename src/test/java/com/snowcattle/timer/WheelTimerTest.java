package com.snowcattle.timer;

import java.util.concurrent.TimeUnit;

/**
 * Created by jiangwenping on 16/12/29.
 */
public class WheelTimerTest {

    public static void main(String[] args) {
        WheelTimer<Integer> wheelTimer = new WheelTimer<Integer>(60, 1, TimeUnit.MINUTES);
        wheelTimer.addExpirationListener(new TestExpirationListener<Integer>());

        wheelTimer.start();
        System.out.println("start time " + System.currentTimeMillis());
        wheelTimer.add(1);

    }
}
