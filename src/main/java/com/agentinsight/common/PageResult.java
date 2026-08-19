package com.agentinsight.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long page;
    private long pageSize;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long page, long pageSize, List<T> records) {
        return new PageResult<>(total, page, pageSize, records);
    }
}
