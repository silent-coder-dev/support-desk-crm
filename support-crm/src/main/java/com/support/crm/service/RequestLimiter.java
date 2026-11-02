package com.support.crm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Stops people flooding the public forms. Kept in memory, so it counts per server and resets on restart.
@Component
public class RequestLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    @Value("${app.limits.ip-per-minute:3}") private int ipPerMinute;
    @Value("${app.limits.ip-per-hour:10}") private int ipPerHour;
    @Value("${app.limits.email-per-hour:5}") private int emailPerHour;
    @Value("${app.limits.text-per-hour:3}") private int textPerHour;
    @Value("${app.limits.all-per-minute:30}") private int allPerMinute;
    @Value("${app.limits.track-per-minute:10}") private int trackPerMinute;

    // Each rule catches a different trick:
    // ip   = one person changing every field
    // email = many requests to one address, even one that belongs to a stranger
    // text = the same complaint sent under different names and emails
    // all  = a backstop if someone fakes many IP addresses
    public boolean allowNewRequest(String ip, String email, String subject, String description) {
        String text = (subject + "|" + description).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return allow("all", allPerMinute, MINUTE)
                && allow("ip-minute:" + ip, ipPerMinute, MINUTE)
                && allow("ip-hour:" + ip, ipPerHour, HOUR)
                && allow("email:" + mailboxOf(email), emailPerHour, HOUR)
                && allow("text:" + text.hashCode(), textPerHour, HOUR);
    }

    public boolean allowTrackAttempt(String ip) {
        return allow("track:" + ip, trackPerMinute, MINUTE);
    }

    // Gmail ignores dots and anything after +, so a.b+1@gmail.com and ab@gmail.com are the same inbox
    private String mailboxOf(String email) {
        String[] parts = email.trim().toLowerCase(Locale.ROOT).split("@", 2);
        if (parts.length < 2) return parts[0];
        String name = parts[0].split("\\+", 2)[0];
        return (parts[1].equals("gmail.com") ? name.replace(".", "") : name) + "@" + parts[1];
    }

    private boolean allow(String key, int max, Duration window) {
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.clear();
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > window.toMillis()) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
