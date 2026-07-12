package com.sky.logistics.common;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("分页响应")
public class PageResponse<T> {

    @ApiModelProperty("当前页数据")
    private List<T> content;

    @ApiModelProperty(value = "当前页码，从 1 开始", example = "1")
    private Integer page;

    @ApiModelProperty(value = "每页条数", example = "20")
    private Integer size;

    @ApiModelProperty(value = "总条数", example = "156")
    private Long totalElements;

    @ApiModelProperty(value = "总页数", example = "8")
    private Integer totalPages;

    public static <T> PageResponse<T> of(List<T> content, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : size;
        long total = content == null ? 0 : content.size();
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        return new PageResponse<>(content, safePage, safeSize, total, pages);
    }
}
