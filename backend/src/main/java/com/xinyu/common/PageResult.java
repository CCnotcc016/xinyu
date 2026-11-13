package com.xinyu.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.total = page.getTotal();
        r.page = page.getCurrent();
        r.size = page.getSize();
        r.records = page.getRecords();
        return r;
    }

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.total = total;
        r.page = page;
        r.size = size;
        r.records = records;
        return r;
    }
}
