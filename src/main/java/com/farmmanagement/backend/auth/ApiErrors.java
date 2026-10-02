package com.farmmanagement.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> validation(HttpServletRequest r) { return err(r,422,"VALIDATION_ERROR","Dữ liệu không hợp lệ. Kiểm tra các trường yêu cầu."); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed(HttpServletRequest r) { return err(r,400,"MALFORMED_REQUEST","JSON không hợp lệ."); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(HttpServletRequest r) { return err(r,415,"UNSUPPORTED_MEDIA_TYPE","Yêu cầu Content-Type application/json."); }
    @ExceptionHandler(DomainException.class)
    ResponseEntity<?> domain(HttpServletRequest r, DomainException ex) { return err(r,ex.status(),ex.code(),ex.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(HttpServletRequest r) { return err(r,403,"FORBIDDEN","Bạn không có quyền thực hiện thao tác này."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(HttpServletRequest r) { return err(r,409,"CONFLICT","Dữ liệu bị trùng hoặc vi phạm ràng buộc."); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(HttpServletRequest r) { return err(r,405,"METHOD_NOT_ALLOWED","Phương thức không được hỗ trợ."); }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> notFound(HttpServletRequest r) { return err(r,404,"NOT_FOUND","Không tìm thấy dữ liệu."); }
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
