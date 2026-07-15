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
        /**
         * 元数据改了之后，谁需要知道？ 假设有搜索索引、推荐系统之类的下游需要感知这个变更。如果直接在这里同步调用下游服务，
         * 一旦下游慢或挂了，会拖垮这个接口，还可能丢失事件（如果调用失败但数据库已经改了）。
         *
         * 这就是引入 Outbox 模式 的动机——把"要发生的事件"和主业务操作放在同一个数据库事务里落地，后续由专门的任务扫表异步投递：
         * 好像webhook
         */
        try {
            long outId = idGen.nextId();
            String payload = objectMapper.writeValueAsString(
                    Map.of("entity", "knowpost", "op", "upsert", "id", id));
            outboxMapper.insert(outId, "knowpost", id, "KnowPostMetadataUpdated", payload);
        } catch (Exception e) {
            log.warn("Outbox event after metadata update failed, post {}: {}", id, e.getMessage());
        }
    }

    private String toJsonOrNull(List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 处理失败"); }
    }

    @Transactional
    public void publish(long creatorId,long id) {
        int updated = mapper.publish(id, creatorId);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");

        //1.下游服务：用户计数
        try {
            userCounterService.incrementPosts(creatorId,1);
        } catch (Exception ignored) {}

        //2.下游服务：Outbox
        try {
            long outId = idGen.nextId();
            String payload = objectMapper.writeValueAsString(
                    Map.of("entity", "knowpost", "op", "upsert", "id", id));
            outboxMapper.insert(outId, "knowpost", id, "KnowPostPublished", payload);
        } catch (Exception e) { log.warn("Outbox event after publish failed, post {}: {}", id, e.getMessage()); }

        //3.下游服务：RAG 索引
        try { ragIndexService.ensureIndexed(id); }
        catch (Exception e) { log.warn("Pre-index after publish failed, post {}: {}", id, e.getMessage()); }
    }

    /**
     * 双删
     */
    @Transactional
    public void updateTop(long creatorId,long id,boolean isTop) {
        invalidateCache(id);
        int updated = mapper.updateTop(id, creatorId, isTop);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        invalidateCache(id);
    }

    @Transactional
    public void updateVisibility(long creatorId,long id,String visible) {
        if (!isValidVisible(visible)) throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        invalidateCache(id);
        int updated = mapper.updateVisibility(id, creatorId, visible);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        invalidateCache(id);
    }

    private boolean isValidVisible(String visible) {
        if (visible == null) return false;
        return switch (visible) {
            case "public","followers","school","private","unlisted" -> true;
            default -> false;
        };
    }

    /**
     * 软删除 + outbox 通知下游同步删除
     */
    @Transactional
    public void delete(long creatorId,long id) {
        invalidateCache(id);
        int updated = mapper.softDelete(id,creatorId);
        if (updated == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        try {
            long outId = idGen.nextId();
            String payload = objectMapper.writeValueAsString(
                    Map.of("entity","knowpost","op","delete","id",id));
            outboxMapper.insert(outId,"knowpost",id,"KnowPostDeleted",payload);
        } catch (Exception e) {
            log.warn("Outbox event after delete failed, post {}: {}", id, e.getMessage());
        }
        invalidateCache(id);
    }

    /**
     * 同时清理三层缓存：Redis 详情缓存、本地（Caffeine）详情缓存、Feed 流本地缓存：
     */
    private void invalidateCache(long id) {
        String pageKey = "knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER;
        try { redis.delete(pageKey); }
        catch (Exception e) { log.warn("Redis 详情缓存删除失败，key={}", pageKey, e); }
        try { knowPostDetailCache.invalidate(pageKey); }
        catch (Exception e) { log.warn("本地详情缓存删除失败，key={}", pageKey, e); }
        try { invalidateFeedLocalCache(id); }
        catch (Exception e) { log.warn("Feed 本地缓存清理失败，id={}", id, e); }
    }

    /**
     * Feed 流的本地缓存分散在很多不同分页 key 上，不能直接靠一个 key 定位，需要一个反向索引
     */
    private void invalidateFeedLocalCache(long id) {
        long hourSlot = System.currentTimeMillis() / 3600000L;
        // 算出当前是第几个"小时槽"，比如现在是第 486721 个整小时

        for (long slot : List.of(hourSlot,hourSlot-1)) {
            // 同时检查"这个小时"和"上一个小时"两本账，防止跨小时边界漏查

            String indexKey = "feed:public:index" + id + ":" + slot;

            try {
                Set<String> pageKeys = redis.opsForSet().members(indexKey);
                // 查这本账里记了哪些分页 key，比如 {"feed:page:offset=0", "feed:page:hot"}

                if (pageKeys == null || pageKeys.isEmpty()) continue;

                for (String localPageKey : pageKeys) {
                    if (localPageKey == null || localPageKey.isBlank()) continue;

                    feedPublicCache.invalidate(localPageKey);
                    // 把这个分页从本地 Caffeine 缓存里删掉——这才是真正的"失效"动作

                    redis.opsForSet().remove(indexKey,localPageKey);
                    // 顺手把这条记录也从索引账本里划掉，账本自己也要保持干净
                }
            } catch (Exception e) {
                log.warn("Feed 缓存清理异常，indexKey={}", indexKey, e);
                // 这本账查失败了（比如 Redis 抖动），记个日志，但不影响别的账本继续处理
            }
        }
    }

    /**
     * 获取知文详情（含作者信息、图片列表）。
     * <p>
     * 流程：
     * 1. 尝试读取 Redis 缓存。
     * 2. 若缓存命中，直接返回（需叠加实时计数与用户状态）。
     * 3. 若缓存未命中，使用 SingleFlight 锁机制防止缓存击穿。
     * 4. 锁内再次检查缓存（双重检查）。
     * 5. 若仍未命中，回源查询数据库。
     * 6. 校验内容状态与访问权限。
     * 7. 组装数据并写入 Redis 缓存（带随机过期时间与热点自动延期）。
     * 8. 返回最终结果（叠加用户维度状态）。
     * </p>
     *
     * @param id 知文 ID
     * @param currentUserIdNullable 当前用户 ID（可空，用于判断权限与点赞状态）
     * @return 知文详情响应
     */
    @Transactional(readOnly = true)
    public KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable) {
        String pageKey = "knowpost:detail:" + id + ":v" + DETAIL_LAYOUT_VER;

        String cached = redis.opsForValue().get(pageKey);
        if (cached != null) {
            if ("NULL".equals(cached)) throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
            try {
                return objectMapper.readValue(cached, KnowPostDetailResponse.class);
            } catch (Exception ignored) { /* 反序列化失败就当没命中，走查库 */}
        }

        KnowPostDetailRow row = mapper.findDetailById(id);
        if (row == null || "deleted".equals(row.getStatus())) {
            redis.opsForValue().set(pageKey, "NULL",
                    Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(31)));
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
        }

        boolean isPublic = "published".equals(row.getStatus()) && "public".equals(row.getVisible());
        boolean isOwner = currentUserIdNullable != null && row.getCreatorId() != null
                && currentUserIdNullable.equals(row.getCreatorId());
        if (!isPublic && !isOwner) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无权限查看");
        }

        List<String> images = parseStringArray(row.getImageUrls());
        List<String> tags   = parseStringArray(row.getTags());

        KnowPostDetailResponse resp = new KnowPostDetailResponse(
                String.valueOf(row.getId()), row.getTitle(), row.getDescription(),
                row.getContentUrl(), images, tags,
                String.valueOf(row.getCreatorId()), row.getAuthorAvatar(),
                row.getAuthorNickname(), row.getAuthorTagJson(),
                0L, 0L, null, null,
                row.getIsTop(), row.getVisible(), row.getType(), row.getPublishTime());

        //查完存回缓存
        try {
            String json = objectMapper.writeValueAsString(resp);
            int baseTtl = 60;
            // 增加随机抖动（Jitter），防止大量缓存同时过期（雪崩）
            int jitter = ThreadLocalRandom.current().nextInt(30);
            redis.opsForValue().set(pageKey,json,Duration.ofSeconds(baseTtl + jitter));
        } catch (Exception ignored) {}

        return resp;
    }

    /**
     *
     * 这里"续命"两个 key：详情页缓存本身，还有它在 Feed 流里对应的条目缓存（feed:item:{id}）
     * ——因为热帖不仅详情页被频繁点，Feed 流里展示它的那条 item 缓存同样承压。
     */
    private void recordHotKeyAndExtend(long id,String detailPageKey) {
        String hotKeyId = "knowpost:" + id;
        hotKey.record(hotKeyId);
        int baseTtl = 60, target = hotKey.ttlForPublic(baseTtl,hotKeyId); //hotKey热点探测

        Long detailTtl = redis.getExpire(detailPageKey);
        if (detailTtl < target) redis.expire(detailPageKey,Duration.ofSeconds(target));

        String itemKey = "feed:item:" + id;
        Long itemTtl = redis.getExpire(itemKey);
        if (itemTtl < target) redis.expire(itemKey, Duration.ofSeconds(target));

    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

}
