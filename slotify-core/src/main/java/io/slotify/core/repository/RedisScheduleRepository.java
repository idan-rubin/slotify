package io.slotify.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slotify.core.exception.SchedulerException;
import io.slotify.core.model.Schedule;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class RedisScheduleRepository implements ScheduleRepository {

    private static final String KEY_PREFIX = "schedule:";
    private static final int MAX_SCAN_ITERATIONS = 10000;

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
            var names = new HashSet<String>();
            scanKeys(jedis, keys -> {
                for (String key : keys) {
                    names.add(key.substring(KEY_PREFIX.length()));
                }
            });
            return names;
        }
    }

    @Override
    public void clear() {
        try (var jedis = jedisPool.getResource()) {
            scanKeys(jedis, keys -> {
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
            });
        }
    }

    private void scanKeys(Jedis jedis, Consumer<List<String>> keyProcessor) {
        var scanParams = new ScanParams().match(KEY_PREFIX + "*").count(100);
        String cursor = "0";
        int iterations = 0;
        do {
            if (++iterations > MAX_SCAN_ITERATIONS) {
                throw new SchedulerException(SchedulerException.ErrorType.REPOSITORY_ERROR,
                        "Redis scan exceeded maximum iterations");
            }
            var result = jedis.scan(cursor, scanParams);
            keyProcessor.accept(result.getResult());
            cursor = result.getCursor();
        } while (!cursor.equals("0"));
    }
}
