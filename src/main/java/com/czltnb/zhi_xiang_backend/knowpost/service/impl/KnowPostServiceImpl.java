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

    @Transactional
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

    /**
     * 注意 updated == 0 这个模式——
     * mapper.updateContent 的 SQL 里应该带了 WHERE id = ? AND creator_id = ?，
     * 如果返回影响行数是 0，说明要么这个草稿不存在，要么当前用户不是它的创建者。
     * 这是一种很省心的"鉴权+存在性检查"合一写法，不用先 SELECT 再判断再 UPDATE，少一次数据库往返。
     *
     * 草稿创建之后，前端要先把图片/正文传到对象存储（OSS），拿到 objectKey 再回填。
     * 这是典型的"先上传、再确认"模式，避免大文件直接走应用服务器：
     */
    @Transactional
    public void confirmContent(long creatorId,long id,String objectKey,String etag,Long size,String sha256) {
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .contentObjectKey(objectKey)
                .contentEtag(etag)
                .contentSha256(sha256)
                .contentUrl(publicUrl(objectKey))
                .updateTime(Instant.now())
                .build();
        int updated = mapper.updateContent(post);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
    }

    //把 objectKey 拼接成可访问的 URL：
    private String publicUrl(String objectKey) {
        String publicDomain = ossProperties.getPublicDomain();
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain.replace("/$","") + "/" + objectKey;
        }
        return "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    @Transactional
    public void updateMetadata(long creatorId,long id,String title,Long tagId,List<String> tags,
                               List<String> imgUrls,String visible,Boolean isTop,String description) {
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .title(title)
                .tagId(tagId)
                .tags(toJsonOrNull(tags))
                .imgUrls(toJsonOrNull(imgUrls))
                .visible(visible)
                .isTop(isTop)
                .description(description)
                .type("image_text")
                .updateTime(Instant.now())
                .build();
        int updated = mapper.updateMetadata(post);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
    }

    private String toJsonOrNull(List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 处理失败"); }
    }
}
