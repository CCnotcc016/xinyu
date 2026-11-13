package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DiaryRequest {

    @NotBlank(message = "日记内容不能为空")
    @Size(max = 5000, message = "日记内容过长")
    private String content;

    /** 用户选择/补充的情绪标签 */
    private List<String> emotionTags;

    private String weather;

    private String scene;

    /** PRIVATE / PUBLIC */
    private String privacy = "PRIVATE";

    /**
     * 离线补传的幂等键：App 在没网时先生成本地 id，联网后原样带回。
     * 同一个用户重复提交同一个值只会落一条日记，超时重试也安全。
     */
    @Size(max = 64, message = "clientUid 过长")
    private String clientUid;
}
