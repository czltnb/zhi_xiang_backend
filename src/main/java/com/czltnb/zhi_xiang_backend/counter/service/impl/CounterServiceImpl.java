package com.czltnb.zhi_xiang_backend.counter.service.impl;

import com.czltnb.zhi_xiang_backend.counter.event.CounterEvent;
import com.czltnb.zhi_xiang_backend.counter.event.CounterEventProducer;
import com.czltnb.zhi_xiang_backend.counter.schema.BitmapShard;
import com.czltnb.zhi_xiang_backend.counter.schema.CounterKeys;
import com.czltnb.zhi_xiang_backend.counter.schema.CounterSchema;
import com.czltnb.zhi_xiang_backend.counter.service.CounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CounterServiceImpl implements CounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> toggleScript;
    private final CounterEventProducer eventProducer;
    private final ApplicationEventPublisher eventPublisher; //用于给Spring容器内的@EventListener方法推送事件

    private static final String TOGGLE_LUA = """
            local bmKey = KEYS[1]
            local offset = tonumber(ARGV[1])
            local op = ARGV[2]
            local prev = redis.call('GETBIT',bmKey,offset)
            if op == 'add' then 
                if prev == 1 then return 0 end
                redis.call('SETBIT',bmKey,offset,1)
                return 1
            elseif op == 'remove' then
                if prev == 0 then return 0 end
                redis.call('SETBIT',bmKey,offset,0)
                return 1
            end
            return -1
            """;

    public CounterServiceImpl(StringRedisTemplate redis,CounterEventProducer eventProducer,
                              ApplicationEventPublisher eventPublisher) {
        this.redis = redis;
        this.eventProducer = eventProducer;
        this.eventPublisher = eventPublisher;
        this.toggleScript = new DefaultRedisScript<>();
        this.toggleScript.setResultType(Long.class);   // 告诉 Redis 客户端：这个脚本执行完返回的是 Long 类型
        this.toggleScript.setScriptText(TOGGLE_LUA);    // 把 Lua 脚本内容塞进去
    }

    @Override
    public boolean like(String entityType,String entityId,long userId) {
        return toggle(entityType,entityId,userId,"like", CounterSchema.IDX_LIKE,true);
    }

    private boolean toggle(String etype,String eid,long uid,String metric,int idx,boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric,etype,eid,chunk);
        Long changed = redis.execute(toggleScript, List.of(bmKey),
                String.valueOf(bit),add ? "add" : "remove");
        boolean ok = changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            eventProducer.publish(CounterEvent.of(etype,eid,metric,idx,uid,delta));
            eventPublisher.publishEvent(CounterEvent.of(etype,eid,metric,idx,uid,delta));
        }
        return ok;
    }
}
