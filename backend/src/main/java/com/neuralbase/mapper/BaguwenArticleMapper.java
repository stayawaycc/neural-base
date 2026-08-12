package com.neuralbase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neuralbase.entity.BaguwenArticle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;

public interface BaguwenArticleMapper extends BaseMapper<BaguwenArticle> {

    /** tags 列是 jsonb，MyBatis-Plus 默认按 varchar 绑定会报类型不匹配，显式 CAST 为 jsonb（同 MessageMapper.insertWithSources 模式）。 */
    @Insert("""
        INSERT INTO baguwen_article (title, url, content, tags, created_at, updated_at)
        VALUES (#{title}, #{url}, #{content}, CAST(#{tags} AS jsonb), now(), now())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithTags(BaguwenArticle article);
}
