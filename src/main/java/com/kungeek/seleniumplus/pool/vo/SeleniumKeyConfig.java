package com.kungeek.seleniumplus.pool.vo;

import java.io.Serializable;
import java.util.Date;

public class SeleniumKeyConfig implements Serializable {

    private String key;
    private String sessionId;
    private Date createDate;
    private Date updateDate;

    public SeleniumKeyConfig() {
    }

    public SeleniumKeyConfig(String sessionId, Date createDate) {
        this.sessionId = sessionId;
        this.createDate = createDate;
        this.updateDate = createDate;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

}
