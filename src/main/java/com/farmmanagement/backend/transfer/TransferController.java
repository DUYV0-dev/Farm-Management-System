package com.farmmanagement.backend.transfer;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data-transfer")
public class TransferController {
    private final TransferService service;
    public TransferController(TransferService service) { this.service=service; }
    @GetMapping
    public Map<String,Object> capabilities(HttpServletRequest request) {
        AuthController.noQuery(request);return Api.success(service.capabilities(),request);
    }
    @GetMapping("/{dataset}/export")
    public ResponseEntity<byte[]> export(@PathVariable String dataset,@RequestParam String format,
        @RequestParam(defaultValue="false") boolean template,HttpServletRequest request) {
        query(request,Set.of("format","template"));
        var result=service.export(dataset,format,template);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("csv")?"text/csv;charset=UTF-8":"application/json;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+dataset+(template?"-template":"")+"."+format+"\"")
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header("X-Data-SHA256",result.checksum()).header("X-Row-Count",Integer.toString(result.rowCount())).body(result.bytes());
    }
    @PostMapping(value="/{dataset}/import",consumes={"application/json","text/csv","application/octet-stream"})
    public Map<String,Object> importFile(@PathVariable String dataset,@RequestParam String format,
        @RequestParam(defaultValue="true") boolean validateOnly,
        @AuthenticationPrincipal Jwt jwt,HttpServletRequest request) throws java.io.IOException {
        query(request,Set.of("format","validateOnly"));
        if(request.getContentLengthLong()>TransferSchema.MAX_BYTES)
            throw TransferException.file("FILE_SIZE","Tệp nhập tối đa 1 MiB.");
        byte[] bytes=request.getInputStream().readNBytes(TransferSchema.MAX_BYTES+1);
        return Api.success(service.importFile(dataset,format,bytes,validateOnly,Integer.parseInt(jwt.getSubject()),Api.requestId(request)),request);
    }
    private void query(HttpServletRequest request,Set<String> allowed) {
        if(!allowed.containsAll(request.getParameterMap().keySet()) || request.getParameterMap().values().stream().anyMatch(v -> v.length!=1))
            throw new IllegalArgumentException();
    }
}
