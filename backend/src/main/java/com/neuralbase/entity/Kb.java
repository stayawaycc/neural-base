package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("kb")
public class Kb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String color;
    private OffsetDateTime createdAt;
}
