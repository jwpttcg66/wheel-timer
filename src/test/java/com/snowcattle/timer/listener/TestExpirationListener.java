package com.snowcattle.timer.listener;

import com.snowcattle.timer.ExpirationListener;

/**
 * Created by jiangwenping on 16/12/29.
 */
public class TestExpirationListener<E> implements ExpirationListener<E> {
    public void expired(E expireObject) {
        System.out.println(expireObject.toString() + " is expired at " + System.currentTimeMillis());
    }
}
