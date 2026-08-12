package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_article")
public class BaguwenArticle {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String url;
    private String content;
    private String tags;   // JSONB：JSON 数组文本，如 ["Java","JVM"]；插入必须 CAST(? AS jsonb)
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
