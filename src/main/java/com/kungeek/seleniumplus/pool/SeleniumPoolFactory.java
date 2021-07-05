package com.kungeek.seleniumplus.pool;

import com.kungeek.seleniumplus.pool.sync.SeleniumSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.InputStream;
import java.util.Properties;

public class SeleniumPoolFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumPoolFactory.class);

    private static String url;
    private static String syncUrl;
    private static int capacity;
    private static String browserType;
    private static String browserArgs;
    private static String proxyAddr;
    private static String nodeNamePrefix;
    private static boolean cluster;
    private static boolean lazy;
    private static long timeout;
    private static ThirdPartyStorage storage;
    private static boolean sync;
    private static long syncInterval;
    private static SeleniumSync seleniumSync;

    private static ApplicationContext context;

    private static void readProperty(String env)  {
        // 读取配置文件中的信息
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/conf/selenium/pool-" + env + ".properties");
        Properties properties = new Properties();
        try {
            properties.load(is);
            url = properties.getProperty("selenium.pool.url");
            capacity = Integer.parseInt(properties.getProperty("selenium.pool.capacity"));
            browserType = properties.getProperty("selenium.pool.browserType");
            browserArgs = properties.getProperty("selenium.pool.browserArgs");
            proxyAddr = properties.getProperty("selenium.pool.proxyAddr");
            nodeNamePrefix = properties.getProperty("selenium.pool.nodeNamePrefix");
            lazy = Boolean.parseBoolean(properties.getProperty("selenium.pool.lazy"));
            timeout = Long.parseLong(properties.getProperty("selenium.pool.timeout"));
            cluster = Boolean.parseBoolean(properties.getProperty("selenium.pool.cluster"));
            if (cluster) {
                storage = (ThirdPartyStorage)Class.forName(properties.getProperty("selenium.pool.storage")).newInstance();
            }
            sync = Boolean.parseBoolean(properties.getProperty("selenium.pool.sync"));
            if (sync) {
                syncUrl = properties.getProperty("selenium.pool.syncUrl");
                syncInterval = Long.parseLong(properties.getProperty("selenium.pool.syncInterval"));
                seleniumSync = (SeleniumSync)Class.forName(properties.getProperty("selenium.pool.seleniumSync")).newInstance();
            }
        } catch (Exception e) {
            LOGGER.error("SeleniumPool init error!", e);
        }
    }

    /**
     * 根据参数创建 SeleniumPool
     */
    public static SeleniumPool createSeleniumPool(String env) {
        try {
            readProperty(env);
            return new SeleniumPool(url, syncUrl, capacity, browserType, browserArgs, proxyAddr, nodeNamePrefix,
                    cluster, storage, lazy, timeout, sync, syncInterval, seleniumSync);
        } catch (Exception e) {
            LOGGER.error("create SeleniumPool error!", e);
        }
        return null;
    }
}
