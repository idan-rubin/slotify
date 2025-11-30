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
        var value = properties.getProperty("redis.host", "");
        return value.isBlank() ? null : value;
    }

    int redisPort() {
        var value = properties.getProperty("redis.port", "6379");
        return Integer.parseInt(value);
    }

    Duration bufferBetweenMeetings() {
        var value = properties.getProperty("buffer.minutes", "0");
        var minutes = Integer.parseInt(value);
        return minutes > 0 ? Duration.ofMinutes(minutes) : null;
    }
}
