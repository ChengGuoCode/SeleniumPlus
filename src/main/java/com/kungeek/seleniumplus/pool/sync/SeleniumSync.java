package com.kungeek.seleniumplus.pool.sync;

import com.kungeek.seleniumplus.pool.SeleniumPool;
import com.kungeek.seleniumplus.pool.ThirdPartyStorage;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class SeleniumSync{

    public SeleniumPool seleniumPool;
    public ThirdPartyStorage storage;

    public void start(SeleniumPool seleniumPool, ThirdPartyStorage storage) {
        this.seleniumPool = seleniumPool;
        this.storage = storage;
        // 设置守护线程
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1,
                new BasicThreadFactory.Builder().namingPattern("SeleniumSync").daemon(true).build());
        // 定时执行
        executorService.scheduleAtFixedRate(this::sync, 1, seleniumPool.getSyncInterval(), TimeUnit.MINUTES);
    }

    public abstract void sync();
}
