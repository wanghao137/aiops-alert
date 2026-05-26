package com.aiops.alert.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 通用分页响应 wrapper。
 *
 * @param <T> 行类型
 */
@Data
@Builder
public class PageResult<T> {
    private long total;
    private int page;
    private int size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, int page, int size, List<T> records) {
        return PageResult.<T>builder()
                .total(total).page(page).size(size).records(records).build();
    }
}
