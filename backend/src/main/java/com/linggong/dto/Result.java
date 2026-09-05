package com.linggong.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一返回体
 */
@Data
@Schema(description = "统一返回体")
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /** 是否成功 */
    private Boolean success;
    /** 错误信息（成功时为 null） */
    private String errorMsg;
    /** 返回数据 */
    private Object data;
    /** 分页总条数（分页接口用） */
    private Long total;

    public static Result ok() {
        return new Result(true, null, null, null);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }

    public static Result fail(String errorMsg) {
        return new Result(false, errorMsg, null, null);
    }
}
