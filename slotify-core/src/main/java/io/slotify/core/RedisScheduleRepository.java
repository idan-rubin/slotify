package io.slotify.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slotify.contract.SchedulerException;
import redis.clients.jedis.JedisPool;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisScheduleRepository implements ScheduleRepository {

    private static final String KEY_PREFIX = "schedule:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisScheduleRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(Schedule schedule) {
        try (var jedis = jedisPool.getResource()) {
            var key = KEY_PREFIX + schedule.participantName();
            var json = objectMapper.writeValueAsString(schedule);
            jedis.set(key, json);
        } catch (JsonProcessingException e) {
            throw new SchedulerException(SchedulerException.ErrorType.REPOSITORY_ERROR,
                    "Failed to serialize schedule for " + schedule.participantName(), e);
        }
    }

    @Override
    public Optional<Schedule> findByParticipant(String name) {
        try (var jedis = jedisPool.getResource()) {
            var json = jedis.get(KEY_PREFIX + name);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Schedule.class));
        } catch (JsonProcessingException e) {
            throw new SchedulerException(SchedulerException.ErrorType.REPOSITORY_ERROR,
                    "Failed to deserialize schedule for " + name, e);
        }
    }

    @Override
    public Set<String> getAllParticipantNames() {
        try (var jedis = jedisPool.getResource()) {
            return jedis.keys(KEY_PREFIX + "*").stream()
                    .map(key -> key.substring(KEY_PREFIX.length()))
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public void clear() {
        try (var jedis = jedisPool.getResource()) {
            var keys = jedis.keys(KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        }
    }
}
