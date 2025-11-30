package io.slotify.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

class Config {

    private static final Config INSTANCE = new Config();

    private final Properties properties;

    private Config() {
        properties = new Properties();
        try (var input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    static Config get() {
        return INSTANCE;
    }

    String redisHost() {
        var value = getConfig("REDIS_HOST", "redis.host", "");
        return value.isBlank() ? null : value;
    }

    int redisPort() {
        return Integer.parseInt(getConfig("REDIS_PORT", "redis.port", "6379"));
    }

    Duration bufferBetweenMeetings() {
        var minutes = Integer.parseInt(getConfig("BUFFER_MINUTES", "buffer.minutes", "0"));
        return minutes > 0 ? Duration.ofMinutes(minutes) : null;
    }

    private String getConfig(String envVar, String property, String defaultValue) {
        var envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return properties.getProperty(property, defaultValue);
    }
}
