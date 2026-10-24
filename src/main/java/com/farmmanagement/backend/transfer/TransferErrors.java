package com.farmmanagement.backend.transfer;

import com.farmmanagement.backend.common.Api;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=TransferController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransferErrors {
    @ExceptionHandler(TransferException.class)
    public ResponseEntity<?> transfer(HttpServletRequest request,TransferException exception) {
        var body=new LinkedHashMap<>(Api.error(request,"IMPORT_EXPORT_VALIDATION",exception.getMessage()));
        body.put("error",Map.of("code","IMPORT_EXPORT_VALIDATION","message",exception.getMessage(),"details",exception.issues()));
        return ResponseEntity.unprocessableEntity().body(body);
    }
}
