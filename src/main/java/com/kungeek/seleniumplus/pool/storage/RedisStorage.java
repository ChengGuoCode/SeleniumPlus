package com.kungeek.seleniumplus.pool.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kungeek.seleniumplus.pool.SeleniumException;
import com.kungeek.seleniumplus.pool.ThirdPartyStorage;
import com.kungeek.seleniumplus.pool.util.UUIDUtil;
import com.kungeek.seleniumplus.pool.vo.SeleniumKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class RedisStorage implements ThirdPartyStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStorage.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SELENIUM_CONFIG_QUEUE = "agent.selenium.config";

    private static final String SELENIUM_CONFIG_COUNT = "agent.selenium.count";

    private static final String RANDOM_KEY_PREFIX = "random.key.";

    //加锁失效时间，毫秒
    private static final long LOCK_EXPIRE = 1000L;

    private static final long LOCK_WAIT = 300L;

    private static final int LOCK_TIMES = 10;

    private static final long LOCK_WAIT_MID = 3000L;

    private static final long LOCK_WAIT_LONG = 60000L;

    private static final String READ_LOCK = "read_lock_";

    private static final String WRITE_LOCK = "write_lock_";

    private static final String LOCK = "lock_";

    private static final String LOCK_RESET = "lock_reset_";

    private static final String LOCK_DELAY = "lock_delay_";

    public abstract RedisTemplate<String, Object> getExecutor();

    @Override
    public SeleniumKeyConfig getConfig() {
        getLock(READ_LOCK);
        try {
            RedisTemplate<String, Object> redisTemplate = getExecutor();
            for (;;) {
                BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(SELENIUM_CONFIG_QUEUE);
                Set<Object> keys = hashOps.keys();
                if (keys != null && keys.size() > 0) {
                    for (Object key : keys) {
                        String keyStr = String.valueOf(key);
                        if (keyStr.startsWith(RANDOM_KEY_PREFIX)) {
                            Object obj = hashOps.get(key);
                            SeleniumKeyConfig seleniumKeyConfig = MAPPER.readValue(Objects.requireNonNull(obj).toString(), SeleniumKeyConfig.class);
                            hashOps.delete(key);
                            redisTemplate.opsForValue().set(LOCK_RESET + READ_LOCK, LOCK_RESET);
                            seleniumKeyConfig.setKey(keyStr);
                            return seleniumKeyConfig;
                        }
                    }
                    for (Object key : keys) {
                        Object obj = hashOps.get(key);
                        SeleniumKeyConfig seleniumKeyConfig = MAPPER.readValue(Objects.requireNonNull(obj).toString(), SeleniumKeyConfig.class);
                        seleniumKeyConfig.setKey(null);
                        hashOps.delete(key);
                        redisTemplate.opsForValue().set(LOCK_RESET + READ_LOCK, LOCK_RESET);
                        seleniumKeyConfig.setKey(String.valueOf(key));
                        return seleniumKeyConfig;
                    }
                }
                redisTemplate.opsForValue().set(LOCK_RESET + READ_LOCK, LOCK_DELAY);
                TimeUnit.MILLISECONDS.sleep(500);
            }
        } catch (Exception e) {
            throw new SeleniumException(e.getMessage());
        } finally {
            unlock(READ_LOCK);
        }
    }

    @Override
    public SeleniumKeyConfig getConfigByKey(String key) {
        if (key == null || "".equals(key)) {
            return getConfig();
        }
        getLock(READ_LOCK + key);
        try {
            BoundHashOperations<String, Object, Object> hashOps = getExecutor().boundHashOps(SELENIUM_CONFIG_QUEUE);
            Object obj = hashOps.get(key);
            if (obj == null) {
                return null;
            }
            hashOps.delete(key);
            SeleniumKeyConfig seleniumKeyConfig = MAPPER.readValue(Objects.requireNonNull(obj).toString(), SeleniumKeyConfig.class);
            seleniumKeyConfig.setKey(key);
            return seleniumKeyConfig;
        } catch (Exception e) {
            throw new SeleniumException(e.getMessage());
        } finally {
            unlock(READ_LOCK + key);
        }
    }

    @Override
    public List<SeleniumKeyConfig> getAllConfig() {
        List<SeleniumKeyConfig> list = new ArrayList<>();
        try {
            BoundHashOperations<String, Object, Object> hashOps = getExecutor().boundHashOps(SELENIUM_CONFIG_QUEUE);
            Set<Object> keys = hashOps.keys();
            if (keys != null && keys.size() > 0) {
                for (Object key : keys) {
                    Object obj = hashOps.get(key);
                    SeleniumKeyConfig seleniumKeyConfig = MAPPER.readValue(Objects.requireNonNull(obj).toString(), SeleniumKeyConfig.class);
                    list.add(seleniumKeyConfig);
                }
            }
        } catch (Exception e) {
            throw new SeleniumException(e.getMessage());
        }
        return list;
    }

    @Override
    public void writeConfig(SeleniumKeyConfig config) {
        getLock(WRITE_LOCK);
        try {
            RedisTemplate<String, Object> redisTemplate = getExecutor();
            BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(SELENIUM_CONFIG_QUEUE);
            hashOps.put(RANDOM_KEY_PREFIX + UUIDUtil.get32LowStr(), MAPPER.writeValueAsString(config));
            countIncrement();
        } catch (JsonProcessingException e) {
            throw new SeleniumException(e.getMessage());
        } finally {
            unlock(WRITE_LOCK);
        }
    }

    @Override
    public void writeConfigByKey(String key, SeleniumKeyConfig config) {
        if (key == null || "".equals(key)) {
            writeConfig(config);
            return;
        }
        getLock(WRITE_LOCK);
        try {
            RedisTemplate<String, Object> redisTemplate = getExecutor();
            BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(SELENIUM_CONFIG_QUEUE);
            hashOps.put(key, MAPPER.writeValueAsString(config));
            if (config.getUpdateDate() == null) {
                countIncrement();
            }
        } catch (JsonProcessingException e) {
            throw new SeleniumException(e.getMessage());
        } finally {
            unlock(WRITE_LOCK);
        }
    }

    private void countIncrement() {
        ValueOperations<String, Object> operations = getExecutor().opsForValue();
        Object obj = operations.get(SELENIUM_CONFIG_COUNT);
        AtomicInteger count = new AtomicInteger(0);
        if (obj instanceof Integer) {
            count.set((Integer) obj);
        }
        operations.set(SELENIUM_CONFIG_COUNT, count.incrementAndGet());
    }

    @Override
    public void delConfigBySessionId(String sessionId) {
        getLock(WRITE_LOCK);
        try {
            RedisTemplate<String, Object> redisTemplate = getExecutor();
            BoundHashOperations<String, Object, Object> hashOps = redisTemplate.boundHashOps(SELENIUM_CONFIG_QUEUE);
            Set<Object> keys = hashOps.keys();
            if (keys != null && keys.size() > 0) {
                for (Object key : keys) {
                    SeleniumKeyConfig seleniumKeyConfig = MAPPER.readValue(Objects.requireNonNull(hashOps.get(key)).toString(), SeleniumKeyConfig.class);
                    if (seleniumKeyConfig.getSessionId().equals(sessionId)) {
                        hashOps.delete(key);
                        ValueOperations<String, Object> operations = redisTemplate.opsForValue();
                        Object obj = operations.get(SELENIUM_CONFIG_COUNT);
                        AtomicInteger count = new AtomicInteger(0);
                        if (obj instanceof Integer) {
                            count.set((Integer) obj);
                        }
                        operations.set(SELENIUM_CONFIG_COUNT, count.decrementAndGet());
                    }
                }
            }
        } catch (Exception e) {
            throw new SeleniumException(e.getMessage());
        } finally {
            unlock(WRITE_LOCK);
        }
    }

    @Override
    public void setConfigNumber(int number) {
        ValueOperations<String, Object> operations = getExecutor().opsForValue();
        operations.set(SELENIUM_CONFIG_COUNT, number);
    }

    @Override
    public Integer getConfigNumber() {
        Object obj = getExecutor().opsForValue().get(SELENIUM_CONFIG_COUNT);
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        return 0;
    }

    @Override
    public String getStorageInfo() {
        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("configTotal", getConfigNumber());
        infoMap.put("configCurrent", getExecutor().boundHashOps(SELENIUM_CONFIG_QUEUE).size());
        try {
            return MAPPER.writeValueAsString(infoMap);
        } catch (JsonProcessingException e) {
            LOGGER.error("storage info transfer exception");
            return null;
        }
    }

    @Override
    public void block(String key) {
        if (key == null || "".equals(key)) {
            getLock(LOCK, LOCK_TIMES, LOCK_WAIT_MID);
        } else {
            getLock(key, LOCK_TIMES, LOCK_WAIT_MID);
        }
    }

    @Override
    public void deblock(String key) {
        if (key == null || "".equals(key)) {
            unlock(LOCK);
        } else {
            unlock(key);
        }
    }

    private void getLock(String lockKey) {
        getLock(lockKey, LOCK_TIMES, LOCK_WAIT);
    }

    private void getLock(String lockKey, int times, long wait) {
        boolean delay = false;
        RedisTemplate<String, Object> redisTemplate = getExecutor();
        Object obj = redisTemplate.opsForValue().get(LOCK_RESET + lockKey);
        if (LOCK_DELAY.equals(obj)) {
            times = LOCK_TIMES;
            wait = LOCK_WAIT_LONG;
            delay = true;
        }
        if (times < 1) {
            unlock(lockKey);
            throw new SeleniumException("get lock exception: [" + lockKey + "]");
        }
        if (!lock(lockKey)) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException e) {
                LOGGER.error("sleep exception", e);
            }
            if (delay) {
                getLockDelay(lockKey, --times, wait);
            } else {
                getLock(lockKey, --times, wait);
            }
        }
    }

    private void getLockDelay(String lockKey, int times, long wait) {
        boolean delay = true;
        RedisTemplate<String, Object> redisTemplate = getExecutor();
        Object obj = redisTemplate.opsForValue().get(LOCK_RESET + lockKey);
        if (LOCK_RESET.equals(obj)) {
            times = LOCK_TIMES;
            wait = LOCK_WAIT;
            delay = false;
        }
        if (times < 1) {
            unlock(lockKey);
            throw new SeleniumException("get lock exception");
        }
        if (!lock(lockKey)) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException e) {
                LOGGER.error("sleep exception", e);
            }
            if (delay) {
                getLockDelay(lockKey, --times, wait);
            } else {
                getLock(lockKey, --times, wait);
            }
        }
    }

    private boolean lock(String lockKey) {
        // 利用lambda表达式
        return (Boolean) getExecutor().execute((RedisCallback) connection -> {

            long expireAt = System.currentTimeMillis() + LOCK_EXPIRE + 1;
            Boolean acquire = connection.setNX(lockKey.getBytes(), String.valueOf(expireAt).getBytes());

            if (acquire) {
                return true;
            } else {
                byte[] value = connection.get(lockKey.getBytes());
                if (Objects.nonNull(value) && value.length > 0) {
                    long expireTime = Long.parseLong(new String(value));
                    // 如果锁已经过期
                    if (expireTime < System.currentTimeMillis()) {
                        // 重新加锁，防止死锁
                        byte[] oldValue = connection.getSet(lockKey.getBytes(), String.valueOf(System.currentTimeMillis() + LOCK_EXPIRE + 1).getBytes());
                        return Long.parseLong(new String(oldValue)) < System.currentTimeMillis();
                    }
                }
            }
            return false;
        });
    }

    private void unlock(String lockKey) {
        getExecutor().delete(lockKey);
    }


}
