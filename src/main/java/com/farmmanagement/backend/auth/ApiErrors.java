package com.farmmanagement.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
    private ResponseEntity<Map<String,Object>> err(HttpServletRequest r,int status,String code,String msg) {
        return ResponseEntity.status(status).body(Api.error(r,code,msg));
    }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<?> credentials(HttpServletRequest r) { return err(r,401,"INVALID_CREDENTIALS","Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản không khả dụng."); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> validation(HttpServletRequest r) { return err(r,422,"VALIDATION_ERROR","Dữ liệu không hợp lệ. Kiểm tra các trường yêu cầu."); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpServletRequest r) { return err(r,400,"MALFORMED_REQUEST","JSON không hợp lệ."); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(HttpServletRequest r) { return err(r,415,"UNSUPPORTED_MEDIA_TYPE","Yêu cầu Content-Type application/json."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(HttpServletRequest r,DataAccessException ex) {
        log.error("requestId={} database failure type={}",Api.requestId(r),ex.getClass().getSimpleName());
        return err(r,503,"SERVICE_UNAVAILABLE","Database tạm thời không sẵn sàng.");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(HttpServletRequest r,Exception ex) {
        // Do not log exception messages, request bodies or headers (may contain credentials).
        log.error("requestId={} unexpected failure type={}",Api.requestId(r),ex.getClass().getName());
        return err(r,500,"INTERNAL_ERROR","Có lỗi hệ thống. Vui lòng cung cấp mã yêu cầu khi báo lỗi.");
    }
}
