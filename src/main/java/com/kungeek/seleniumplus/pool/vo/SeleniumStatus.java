package com.kungeek.seleniumplus.pool.vo;

import java.util.List;

public class SeleniumStatus {
    public Integer browserTimeout;// 0,
    public String capabilityMatcher;// "org.openqa.grid.internal.utils.DefaultCapabilityMatcher",
    public Integer cleanUpCycle;// 5000,
    public Object custom;// {},
    public boolean debug;// true,
    public String host;// "192.168.199.184",
    public int maxSession;// 60,
    public int newSessionRequestCount;// 0,
    public int newSessionWaitTimeout;// -1,
    public List<Node> nodes;
    public String port;// 5555,
    public String registry;// "org.openqa.grid.internal.DefaultGridRegistry",
    public String role;// "hub",
    public List<String> servlets;
    public SlotCount slotCounts;
    public boolean success;// true,
    public boolean throwOnCapabilityNotPresent;// false,
    public int timeout;// 1,
    public int bossMaxSession;// 最大会话数

    public static final class Node {
        public List<Browser> browsers;
        public String id;// "http://192.168.199.184:4786"
        public SlotCount slotCounts;

        public static final class Browser {
            public String browser;// "chrome",
            public SlotCount slots;
        }
    }

    public static final class SlotCount {
        public int busy;// 0,
        public int free;// 20,
        public int total;// 20
    }

}
