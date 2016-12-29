package com.snowcattle.timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by jiangwenping on 16/12/29.
 * 时间槽
 */
public class TimeSlot<E> {
    /**
     * 时间槽id
     */
    private int id;

    /**
     * 槽元素
     */
    private Map<E, E> elements = new ConcurrentHashMap<E, E>();

}
