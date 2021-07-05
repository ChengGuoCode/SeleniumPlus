package com.kungeek.seleniumplus.pool;

import com.kungeek.seleniumplus.pool.vo.SeleniumKeyConfig;

import java.util.List;

public interface ThirdPartyStorage<T> {

    SeleniumKeyConfig getConfig();

    SeleniumKeyConfig getConfigByKey(String key);

    List<SeleniumKeyConfig> getAllConfig();

    void writeConfig(SeleniumKeyConfig config);

    void writeConfigByKey(String key, SeleniumKeyConfig config);

    void delConfigBySessionId(String sessionId);

    void setConfigNumber(int number);

    Integer getConfigNumber();

    String getStorageInfo();

    void block(String key);

    void deblock(String key);

    T getExecutor();
}
