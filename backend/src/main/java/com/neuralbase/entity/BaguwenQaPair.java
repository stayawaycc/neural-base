package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_qa_pair")
public class BaguwenQaPair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private String question;
    private String answer;
    private String difficulty;   // 简单 / 中等 / 困难
    private Integer sortOrder;
    private OffsetDateTime createdAt;
}
