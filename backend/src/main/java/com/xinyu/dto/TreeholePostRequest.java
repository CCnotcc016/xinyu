package com.xinyu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TreeholePostRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容过长")
    private String content;

    private List<String> images;

    /** 是否匿名，默认匿名 */
    private Boolean isAnonymous = true;
}
