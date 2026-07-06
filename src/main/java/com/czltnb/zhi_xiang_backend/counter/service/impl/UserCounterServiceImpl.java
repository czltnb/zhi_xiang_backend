package com.czltnb.zhi_xiang_backend.counter.service.impl;

import com.czltnb.zhi_xiang_backend.counter.schema.UserCounterKeys;
import com.czltnb.zhi_xiang_backend.counter.service.CounterService;
import com.czltnb.zhi_xiang_backend.counter.service.UserCounterService;
import com.czltnb.zhi_xiang_backend.knowpost.mapper.KnowPostMapper;
import com.czltnb.zhi_xiang_backend.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserCounterServiceImpl implements UserCounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final RelationMapper relationMapper;

    private static final String INCR_FIELD_LUA = """
            local cntKey = KEYS[1]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            
            local function read32be(s,off)
              local b = {string.byte(s,off+1,off+4)}
              local n = 0;
              for i=1,4 do n = n*256 + b[i] end
              return n
            end
            
            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end
              return string.char(unpack(t))
            end
            
            local cnt = redis.call('GET',cntKey)
            if not cnt then cnt = string.rep(string.char(0),schemaLen * fieldSize) end
            
            local off = (idx - 1) * fieldSize
            local v = read32be(cnt,off) + delta
            if v < 0 then v = 0 end
            local seg = write32be(v)
            
            cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)
            redis.call('SET', cntKey, cnt)
            return 1
            """;

    public UserCounterServiceImpl(StringRedisTemplate redis, KnowPostMapper knowPostMapper,
                                  CounterService counterService, RelationMapper relationMapper) {
        this.redis          = redis;
        this.knowPostMapper = knowPostMapper;
        this.counterService = counterService;
        this.relationMapper = relationMapper;
        this.incrScript     = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
    }

    @Override public void incrementFollowings(long userId, int delta) {
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)), "5","4","1", String.valueOf(delta));
    }
    @Override public void incrementFollowers(long userId, int delta) {
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)), "5","4","2", String.valueOf(delta));
    }
    @Override public void incrementPosts(long userId, int delta) {
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)), "5","4","3", String.valueOf(delta));
    }
    @Override public void incrementLikesReceived(long userId, int delta) {
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)), "5","4","4", String.valueOf(delta));
    }
    @Override public void incrementFavsReceived(long userId, int delta) {
        redis.execute(incrScript, List.of(UserCounterKeys.sdsKey(userId)), "5","4","5", String.valueOf(delta));
    }

    @Override
    public void rebuildAllCounters(long userId) {
        String key = UserCounterKeys.sdsKey(userId);
        byte[] raw = redis.execute((RedisCallback<byte[]>) c ->
                c.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
        int len = 5 * 4;
        byte[] buf = new byte[len];
        if (raw != null && raw.length == len) System.arraycopy(raw, 0, buf, 0, len);

        long followings = relationMapper.countFollowingActive(userId);
        long followers  = relationMapper.countFollowerActive(userId);

        List<Long> ids = knowPostMapper.listMyPublishedIds(userId);
        List<String> idStr = ids.stream().map(String::valueOf).collect(Collectors.toList());

        if (!idStr.isEmpty()) {
            Map<String, Map<String, Long>> counts =
                    counterService.getCountsBatch("knowpost", idStr, List.of("like", "fav"));
            long likeSum = 0L, favSum = 0L;
            for (String id : idStr) {
                Map<String, Long> v = counts.get(id);
                likeSum += v.getOrDefault("like", 0L);
                favSum  += v.getOrDefault("fav",  0L);
            }
            write32be(buf, 2 * 4, idStr.size());
            write32be(buf, 3 * 4, likeSum);
            write32be(buf, 4 * 4, favSum);
        } else {
            write32be(buf, 2 * 4, 0L);
            write32be(buf, 3 * 4, 0L);
            write32be(buf, 4 * 4, 0L);
        }
        write32be(buf, 0, followings);
        write32be(buf, 4, followers);

        redis.execute((RedisCallback<Void>) c -> {
            c.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), buf);
            return null;
        });
    }

    private static void write32be(byte[] buf, int off, long val) {
        long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL));
        buf[off]   = (byte)((n >>> 24) & 0xFF);
        buf[off+1] = (byte)((n >>> 16) & 0xFF);
        buf[off+2] = (byte)((n >>>  8) & 0xFF);
        buf[off+3] = (byte)(n & 0xFF);
    }
}

