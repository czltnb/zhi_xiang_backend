package com.czltnb.zhi_xiang_backend.knowpost.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.czltnb.zhi_xiang_backend.cache.hotkey.HotKeyDetector;
import com.czltnb.zhi_xiang_backend.common.exception.BusinessException;
import com.czltnb.zhi_xiang_backend.common.exception.ErrorCode;
import com.czltnb.zhi_xiang_backend.counter.service.CounterService;
import com.czltnb.zhi_xiang_backend.counter.service.UserCounterService;
import com.czltnb.zhi_xiang_backend.knowpost.api.dto.FeedPageResponse;
import com.czltnb.zhi_xiang_backend.knowpost.api.dto.KnowPostDetailResponse;
import com.czltnb.zhi_xiang_backend.knowpost.id.SnowflakeIdGenerator;
import com.czltnb.zhi_xiang_backend.knowpost.mapper.KnowPostMapper;
import com.czltnb.zhi_xiang_backend.knowpost.model.KnowPost;
import com.czltnb.zhi_xiang_backend.knowpost.model.KnowPostDetailRow;
import com.czltnb.zhi_xiang_backend.knowpost.service.KnowPostService;
import com.czltnb.zhi_xiang_backend.llm.rag.RagIndexService;
import com.czltnb.zhi_xiang_backend.relation.outbox.OutboxMapper;
import com.czltnb.zhi_xiang_backend.storage.config.OssProperties;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KnowPostServiceImpl implements KnowPostService {

    private final KnowPostMapper mapper;

    @Resource
    private final SnowflakeIdGenerator idGen;

    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;
    private final CounterService counterService;
    private final UserCounterService userCounterService;
    private final StringRedisTemplate redis;

    @Qualifier("feedPublicCache")
    private final Cache<String, FeedPageResponse> feedPublicCache;

    @Qualifier("knowPostDetailCache")
    private final Cache<String, KnowPostDetailResponse> knowPostDetailCache;

    private final HotKeyDetector hotKey;
    private final RagIndexService ragIndexService;
    private final OutboxMapper outboxMapper;

    private static final Logger log = LoggerFactory.getLogger(KnowPostServiceImpl.class);
    private static final int DETAIL_LAYOUT_VER = 1;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    public KnowPostServiceImpl(
            KnowPostMapper mapper,
            SnowflakeIdGenerator idGen,
            ObjectMapper objectMapper,
            OssProperties ossProperties,
            CounterService counterService,
            UserCounterService userCounterService,
            StringRedisTemplate redis,
            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
            @Qualifier("knowPostDetailCache") Cache<String, KnowPostDetailResponse> knowPostDetailCache,
            HotKeyDetector hotKey,
            RagIndexService ragIndexService,
            OutboxMapper outboxMapper) {
        this.mapper             = mapper;
        this.idGen              = idGen;
        this.objectMapper       = objectMapper;
        this.ossProperties      = ossProperties;
        this.counterService     = counterService;
        this.userCounterService = userCounterService;
        this.redis              = redis;
        this.feedPublicCache    = feedPublicCache;
        this.knowPostDetailCache = knowPostDetailCache;
        this.hotKey             = hotKey;
        this.ragIndexService    = ragIndexService;
        this.outboxMapper       = outboxMapper;
    }

    @Override
    public long createDraft(long creatorId) {
        long id = idGen.nextId();
        Instant now = Instant.now();
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .status("draft")
                .type("image_text")
                .visible("public")
                .isTop(false)
                .createTime(now)
                .updateTime(now)
                .build();
        mapper.insertDraft(post);
        return id;
    }
}
