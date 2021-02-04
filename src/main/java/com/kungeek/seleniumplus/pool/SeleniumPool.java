package com.kungeek.seleniumplus.pool;

import com.kungeek.seleniumplus.pool.sync.SeleniumSync;
import com.kungeek.seleniumplus.pool.vo.SeleniumInfoVO;
import com.kungeek.seleniumplus.pool.vo.SeleniumKeyConfig;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SeleniumPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeleniumPool.class);

    private static Capabilities capabilities;

    private final String url;
    private final String syncUrl;
    private final int capacity;
    private final String browserType;
    private final String browserArgs;
    private final String proxyAddr;
    private final String nodeNamePrefix;
    private final boolean cluster;
    private final ThirdPartyStorage storage;
    private final boolean lazy;
    private final boolean sync;
    private final long syncInterval;
    private final SeleniumSync seleniumSync;
    /**
     * 超时时间：浏览器不操作的时间
     */
    private final long timeout;

    private final AtomicInteger count = new AtomicInteger(0);
    private final LinkedBlockingQueue<DepWebDriver> driverQueue;

    SeleniumPool(String url, String syncUrl, int capacity, String browserType, String browserArgs, String proxyAddr,
                 String nodeNamePrefix, boolean cluster, ThirdPartyStorage storage, boolean lazy, long timeout, boolean sync,
                 long syncInterval, SeleniumSync seleniumSync) throws MalformedURLException {
        this.url = url;
        this.syncUrl = syncUrl;
        this.capacity = capacity;
        this.browserType = browserType;
        this.browserArgs = browserArgs;
        this.proxyAddr = proxyAddr;
        this.nodeNamePrefix = nodeNamePrefix;
        this.cluster = cluster;
        this.storage = storage;
        this.lazy = lazy;
        this.timeout = timeout;
        this.sync = sync;
        this.syncInterval = syncInterval;
        this.seleniumSync = seleniumSync;
        driverQueue = new LinkedBlockingQueue<>(capacity);
        instantiate();
    }

    private void instantiate() throws MalformedURLException {
        if (!lazy) {
            createWebDriver(capacity);
        }
        if (this.sync) {
            seleniumSync.start(this, this.storage);
        }
        capabilities = getCapabilities(browserType, browserArgs, proxyAddr,
                StringUtils.isNotEmpty(nodeNamePrefix) ? nodeNamePrefix + count.get() : "");
    }


    /**
     * 创建指定 num 数量的 WebDriver
     * @param num 需要创建浏览器的数量
     */
    private void createWebDriver(int num) throws MalformedURLException {
        createWebDriver(num, null);
    }

    private void createWebDriver(int num, String key) throws MalformedURLException {
        for (int i = 0; i < num; i++) {
            if (count.get() >= capacity) {
                break;
            }
            count.incrementAndGet();
            DepWebDriver webDriver = new DepWebDriver(getCapabilities(browserType, browserArgs, proxyAddr,
                    StringUtils.isNotEmpty(nodeNamePrefix) ? nodeNamePrefix + count.get() : ""));
            boolean offer = driverQueue.offer(webDriver);
            if (offer && cluster) {
                SeleniumKeyConfig seleniumKeyConfig = webDriver.getSeleniumKeyConfig();
                if (key != null) {
                    seleniumKeyConfig.setKey(key);
                    storage.writeConfigByKey(key, seleniumKeyConfig);
                } else {
                    storage.writeConfig(seleniumKeyConfig);
                }
            } else if (!offer) {
                count.decrementAndGet();
            }
        }
    }


    /**
     * 获取 WebDriver
     * @return WebDriver
     */
    private WebDriver obtainWebDriver(String key) throws IOException {
        if (cluster) {
            storage.block(null);
            try {
                Integer configNumber = storage.getConfigNumber();
                if (configNumber < capacity && count.get() < capacity) {
                    createWebDriver(1, key);
                }
            } catch (Exception e) {
                LOGGER.error("judge config exception");
            } finally {
                storage.deblock(null);
            }
            SeleniumKeyConfig config = storage.getConfigByKey(key);
            if (config == null) {
                config = storage.getConfig();
            }
            DepWebDriver webDriver = driverQueue.poll();
            if (webDriver == null) {
                webDriver = new DepWebDriver();
            }
            webDriver.setSeleniumKeyConfig(config);
            if(checkTimeout(webDriver)) {
                // 退出浏览器
                webDriver.quit();
                // 创建一个 webDriver
                createWebDriver(1, key);

                return obtainWebDriver(key);
            }
            setCapabilities(webDriver);
            return webDriver;
        } else {
            return null;
        }
    }

    public WebDriver getWebDriver() throws IOException {
        return obtainWebDriver(null);
    }

    public WebDriver getWebDriver(String key) throws IOException {
        if (cluster) {
            SeleniumKeyConfig configByKey = storage.getConfigByKey(key);
            if (configByKey != null) {
                DepWebDriver webDriver = driverQueue.poll();
                if (webDriver == null) {
                    webDriver = new DepWebDriver();
                }
                webDriver.setSeleniumKeyConfig(configByKey);
                setCapabilities(webDriver);
                return webDriver;
            } else {
                return obtainWebDriver(key);
            }
        } else {
            throw new SeleniumException("condition query unsupport!");
        }
    }

    private Capabilities getCapabilities(String browserType, String browserArgs, String proxyAddr, String nodeName) {
        Capabilities cap;
        switch (browserType) {
            case BrowserType.CHROME:
                ChromeOptions chromeOptions = new ChromeOptions();
                if (StringUtils.isNotEmpty(browserArgs)) {
                    chromeOptions.addArguments(browserArgs);
                } else {
                    chromeOptions.addArguments("--force-device-scale-factor=1 /high-dpi-support=1", "--disable-infobars", "--silent", "–-incognito", "--allow-running-insecure-content", "--ignore-certificate-errors");
                    // 窗口最大化
                    chromeOptions.addArguments("start-maximized");
                }
                if (StringUtils.isNotEmpty(proxyAddr)) {
                    chromeOptions.addArguments("--proxy-server=" + proxyAddr);
                }
                chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
                DesiredCapabilities dc = DesiredCapabilities.chrome();
                // 接受SSL证书，即使是不安全的
                dc.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
                dc.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
                if (StringUtils.isNotEmpty(nodeName)) {
                    dc.setCapability(CapabilityType.APPLICATION_NAME, nodeName);
                }
                cap = dc;
                break;
            case BrowserType.FIREFOX:
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                if (StringUtils.isNotEmpty(proxyAddr)) {
                    String[] p = proxyAddr.split(":");
                    firefoxOptions.addPreference("network.proxy.type", 1);
                    firefoxOptions.addPreference("network.proxy.http", p[0]);
                    firefoxOptions.addPreference("network.proxy.http_port", Integer.parseInt(p[1]));
                    firefoxOptions.addPreference("network.proxy.ssl", p[0]);
                    firefoxOptions.addPreference("network.proxy.ssl_port", Integer.parseInt(p[1]));
                }
                firefoxOptions.addPreference("dom.webdriver.enabled", false);
                if (StringUtils.isNotEmpty(browserArgs)) {
                    firefoxOptions.addArguments(browserArgs);
                } else {
                    firefoxOptions.addArguments("-safe-mode");
                }
                firefoxOptions.setAcceptInsecureCerts(true);
                cap = firefoxOptions;
                break;
            default:
                throw new SeleniumException("unsupported browser type:[" + browserType + "]");
        }
        return cap;
    }

    /**
     * 必须设置 Capabilities，否则判断 JavaScript 会抛空指针异常。
     * @param webDriver DepWebDriver
     */
    private  void setCapabilities(DepWebDriver webDriver) {
        if (webDriver != null && webDriver.getCapabilities() == null) {
            Class<?> superclass = webDriver.getClass().getSuperclass();
            Field capabilities;
            try {
                capabilities = superclass.getDeclaredField("capabilities");
                capabilities.setAccessible(true);
                capabilities.set(webDriver, SeleniumPool.capabilities);
            } catch (Exception e) {
                LOGGER.error("设置【capabilities】值出现异常：", e);
            }
        }
    }

    /**
     * 判断当前浏览器不操作的时间是否大于 timeout，大于则要退出浏览器
     * @param webDriver DepWebDriver
     */
    private boolean checkTimeout(DepWebDriver webDriver){
        // 上次执行的时间到当前执行时间
        long time = (System.currentTimeMillis() - webDriver.getSeleniumKeyConfig().getUpdateDate().getTime()) / 1000;
        return time > timeout;
    }

    public SeleniumInfoVO getInfo() {
        SeleniumInfoVO seleniumInfoVO = new SeleniumInfoVO();
        seleniumInfoVO.setUrl(this.url);
        seleniumInfoVO.setCluster(this.cluster);
        seleniumInfoVO.setCurrentInstanceCount(this.count.get());
        seleniumInfoVO.setCurrentInstanceNum(this.driverQueue.size());
        seleniumInfoVO.setProxyAddr(this.proxyAddr);
        seleniumInfoVO.setStorageInfo(this.storage.getStorageInfo());
        seleniumInfoVO.setSyncUrl(this.syncUrl);
        seleniumInfoVO.setTimeout(this.timeout);
        return seleniumInfoVO;
    }

    public void setCount(int count) {
        this.count.set(count);
    }

    public long getSyncInterval() {
        return syncInterval;
    }

    private class DepWebDriver extends RemoteWebDriver {

        private SeleniumKeyConfig seleniumKeyConfig;

        DepWebDriver() {
        }

        DepWebDriver(Capabilities cap) throws MalformedURLException {
            super(new URL(url), cap);
            this.seleniumKeyConfig = new SeleniumKeyConfig(getSessionId().toString(), new Date());
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void startSession(Capabilities capabilities) {
            Map<String, ?> parameters = ImmutableMap.of("desiredCapabilities", capabilities);
            Response response = this.execute("newSession", parameters);
            Map rawCapabilities = (Map) response.getValue();
            MutableCapabilities returnedCapabilities = new MutableCapabilities();

            for (Object entry1 : rawCapabilities.entrySet()) {
                if (!"platform".equals(((Map.Entry) entry1).getKey()) && !"platformName".equals(((Map.Entry) entry1).getKey())) {
                    returnedCapabilities.setCapability((String) ((Map.Entry) entry1).getKey(), ((Map.Entry) entry1).getValue());
                }
            }

            String platformString = (String) rawCapabilities.getOrDefault("platform", rawCapabilities.get("platformName"));

            Platform platform;
            try {
                if (platformString != null && !"".equals(platformString)) {
                    platform = Platform.fromString(platformString);
                } else {
                    platform = Platform.ANY;
                }
            } catch (WebDriverException var9) {
                platform = Platform.extractFromSysProperty(platformString);
            }

            returnedCapabilities.setCapability("platform", platform);
            returnedCapabilities.setCapability("platformName", platform);
            if (rawCapabilities.containsKey("javascriptEnabled")) {
                Object raw = rawCapabilities.get("javascriptEnabled");
                if (raw instanceof String) {
                    returnedCapabilities.setCapability("javascriptEnabled", Boolean.parseBoolean((String) raw));
                } else if (raw instanceof Boolean) {
                    returnedCapabilities.setCapability("javascriptEnabled", (Boolean) raw);
                }
            } else {
                returnedCapabilities.setCapability("javascriptEnabled", true);
            }
            // 设置值
            if (SeleniumPool.capabilities == null) {
                SeleniumPool.capabilities = returnedCapabilities;
            }
            setSessionId(response.getSessionId());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DepWebDriver that = (DepWebDriver) o;
            return Objects.equals(this, that);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this);
        }

        @Override
        public void close() {
            // 获取有多少个标签页
            Set<String> windowHandles = this.getWindowHandles();
            int size = windowHandles.size();
            // 如果只有一个标签页，调用close方法并不会真正关闭浏览器,调用 quit 处理，归还资源
            if (size == 1) {
                quit();
                return;
            }
            super.close();
        }

        @Override
        public void quit() {
            // 判断当前浏览器是否超时，超时则退出
            if (checkTimeout(this)) {
                try {
                    if (cluster && this.seleniumKeyConfig.getSessionId() != null) {
                        storage.delConfigBySessionId(this.seleniumKeyConfig.getSessionId());
                    }
                    count.decrementAndGet();
                    super.quit();
                } catch (Exception ignored) {

                }
                return;
            }

            // 获取有多少个标签页
            Set<String> windowHandles = this.getWindowHandles();
            int size = windowHandles.size();
            // 如果有多个标签页，关闭其他的标签页
            if (size > 1) {
                for (int i = 1; i < size; i++) {
                    this.switchTo().window(new ArrayList<>(this.getWindowHandles()).get(1));
                    this.close();
                }
                // 切换到第一个标签页
                this.switchTo().window(new ArrayList<>(this.getWindowHandles()).get(0));
            }
            // 删掉浏览器记录
            this.manage().deleteAllCookies();
            // 更新执行时间
            this.seleniumKeyConfig.setUpdateDate(new Date());
            if (cluster) {
                // 将配置信息回写到 redis
                storage.writeConfigByKey(this.seleniumKeyConfig.getKey(), this.seleniumKeyConfig);
            }
            // 放回到队列中
            driverQueue.offer(this);
        }

        SeleniumKeyConfig getSeleniumKeyConfig() {
            return seleniumKeyConfig;
        }

        void setSeleniumKeyConfig(SeleniumKeyConfig seleniumKeyConfig) throws MalformedURLException {
            super.setSessionId(seleniumKeyConfig.getSessionId());
            HttpCommandExecutor httpCommandExecutor = new DepHttpCommandExecutor(new URL(url));
            this.setCommandExecutor(httpCommandExecutor);
            this.seleniumKeyConfig = seleniumKeyConfig;
        }

        @Override
        public String toString() {
            return "";
        }
    }

}
