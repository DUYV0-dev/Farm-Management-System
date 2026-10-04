package com.farmmanagement.backend.common;

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
    ResponseEntity<?> conflict(HttpServletRequest r, DataIntegrityViolationException ex) {
        // Translate only known database guard messages; never expose raw SQL or exception details.
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
        var messages = Map.ofEntries(
            Map.entry("Farm area is smaller than its plots", "Diện tích trang trại không được nhỏ hơn tổng diện tích các khu đất."),
            Map.entry("Plot areas exceed farm area", "Tổng diện tích khu đất vượt quá diện tích trang trại."),
            Map.entry("Deactivate active plots and warehouses first", "Hãy ngừng hoạt động các khu đất và kho trước khi ngừng trang trại."),
            Map.entry("Plot has open seasons", "Khu đất đang có mùa vụ chưa hoàn tất."),
            Map.entry("Crop has open seasons", "Cây trồng đang được sử dụng trong mùa vụ chưa hoàn tất."),
            Map.entry("Category has active materials", "Nhóm còn vật tư đang hoạt động. Hãy ngừng các vật tư trước."),
            Map.entry("Warehouse has stock", "Kho còn tồn vật tư. Hãy xử lý tồn kho trước khi ngừng hoạt động."),
            Map.entry("Clear stock before deactivating material or changing its unit", "Vật tư còn tồn kho. Không thể ngừng hoạt động hoặc đổi đơn vị."),
            Map.entry("Category is missing or inactive", "Nhóm vật tư không tồn tại hoặc đã ngừng hoạt động."),
            Map.entry("Farm is missing or inactive", "Trang trại không tồn tại hoặc đã ngừng hoạt động."),
            Map.entry("Farm is inactive", "Trang trại đã ngừng hoạt động."),
            Map.entry("Season requires active farm, plot and crop", "Mùa vụ cần trang trại, khu đất và cây trồng đang hoạt động."),
            Map.entry("Inventory requires active warehouse, farm, material and category", "Chỉ cập nhật tồn kho khi kho, trang trại, vật tư và nhóm vật tư đang hoạt động.")
        );
        String message = messages.entrySet().stream().filter(e -> detail.contains(e.getKey()))
            .map(Map.Entry::getValue).findFirst().orElse("Dữ liệu bị trùng hoặc vi phạm ràng buộc. Kiểm tra tên và bản ghi liên quan.");
        return err(r,409,"CONFLICT",message);
    }
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
