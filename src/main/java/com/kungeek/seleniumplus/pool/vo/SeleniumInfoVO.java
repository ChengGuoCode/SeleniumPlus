package com.kungeek.seleniumplus.pool.vo;

public class SeleniumInfoVO {

    private String url;
    private String syncUrl;
    private boolean cluster;
    private int currentInstanceCount;
    private int currentInstanceNum;
    private String proxyAddr;
    private String storageInfo;
    private long timeout;


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSyncUrl() {
        return syncUrl;
    }

    public void setSyncUrl(String syncUrl) {
        this.syncUrl = syncUrl;
    }

    public boolean isCluster() {
        return cluster;
    }

    public void setCluster(boolean cluster) {
        this.cluster = cluster;
    }

    public int getCurrentInstanceCount() {
        return currentInstanceCount;
    }

    public void setCurrentInstanceCount(int currentInstanceCount) {
        this.currentInstanceCount = currentInstanceCount;
    }

    public int getCurrentInstanceNum() {
        return currentInstanceNum;
    }

    public void setCurrentInstanceNum(int currentInstanceNum) {
        this.currentInstanceNum = currentInstanceNum;
    }

    public String getProxyAddr() {
        return proxyAddr;
    }

    public void setProxyAddr(String proxyAddr) {
        this.proxyAddr = proxyAddr;
    }

    public String getStorageInfo() {
        return storageInfo;
    }

    public void setStorageInfo(String storageInfo) {
        this.storageInfo = storageInfo;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
