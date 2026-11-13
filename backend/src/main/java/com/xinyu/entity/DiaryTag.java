package com.xinyu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("diary_tag")
public class DiaryTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long diaryId;
    private Long tagId;
}
