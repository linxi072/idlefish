package com.idlefish.trade.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：将 BizException / 校验异常 / 未登录 / 未知异常统一为 Result。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : Code.PARAM_INVALID.getMsg();
        return Result.fail(Code.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getMessage() != null ? e.getMessage() : Code.PARAM_INVALID.getMsg();
        return Result.fail(Code.PARAM_INVALID.getCode(), msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.fail(Code.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(Code.NOT_FOUND.getCode(), "资源不存在");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingHeader(MissingRequestHeaderException e) {
        return Result.fail(Code.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("unexpected error at {}", request.getRequestURI(), e);
        return Result.fail(Code.SYSTEM_ERROR);
    }
}
