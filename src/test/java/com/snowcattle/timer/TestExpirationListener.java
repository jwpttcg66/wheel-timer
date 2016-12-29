package com.snowcattle.timer;

/**
 * Created by jiangwenping on 16/12/29.
 */
public class TestExpirationListener<E> implements ExpirationListener<E>{
    public void expired(E expireObject) {
        System.out.println(expireObject.toString() + " is expired at " + System.currentTimeMillis());
    }
}
