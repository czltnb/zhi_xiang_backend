package com.czltnb.zhi_xiang_backend.knowpost.mapper;

import com.czltnb.zhi_xiang_backend.knowpost.model.KnowPost;
import com.czltnb.zhi_xiang_backend.knowpost.model.KnowPostDetailRow;
import com.czltnb.zhi_xiang_backend.knowpost.model.KnowPostFeedRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowPostMapper {

    //1.【增】写草稿 -> 点击发布按钮
    void insertDraft(KnowPost post);

    int publish(@Param("id") Long id,@Param("creatorId") Long creatorId);

    //2.【删】软删除
    int softDelete(@Param("id") Long id,@Param("creatorId") Long creatorId);

    //3.【改】
    int updateContent(KnowPost post);

    int updateMetadata(KnowPost post);

    // 设置置顶
    int updateTop(@Param("id") Long id, @Param("creatorId") Long creatorId,
                  @Param("isTop") Boolean isTop);

    // 设置可见性
    int updateVisibility(@Param("id") Long id, @Param("creatorId") Long creatorId,
                         @Param("visible") String visible);

    //4.【查】
    KnowPost findById(@Param("id") Long id);

    // 详情查询（联表查询，含作者信息）
    KnowPostDetailRow findDetailById(@Param("id") Long id);

    // 统计我的已发布知文数量
    long countMyPublished(@Param("creatorId") long creatorId);

    // 列出我的已发布知文ID列表
    List<Long> listMyPublishedIds(@Param("creatorId") long creatorId);

    // 首页 Feed 列表（已发布、公开可见），按发布时间倒序
    List<KnowPostFeedRow> listFeedPublic(@Param("limit") int limit,
                                         @Param("offset") int offset);

    // 我的知文列表（当前用户已发布），置顶优先，其次按发布时间倒序
    List<KnowPostFeedRow> listMyPublished(@Param("creatorId") long creatorId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);


}
