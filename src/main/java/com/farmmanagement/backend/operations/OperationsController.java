package com.farmmanagement.backend.operations;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.farmmanagement.backend.common.Api;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service=service; }

    @GetMapping("/overview")
    public Object overview(HttpServletRequest r) {
        return Api.success(service.overview(Api.requestId(r)),r);
    }

    @GetMapping("/options")
    public Object options(HttpServletRequest r) {
        return Api.success(service.options(Api.requestId(r)),r);
    }

    @GetMapping("/tasks")
    public Object tasks(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size,
            @RequestParam(defaultValue="") String status,@RequestParam(required=false) Integer seasonId,HttpServletRequest r) {
        return Api.success(service.tasks(page,size,status,seasonId,Api.requestId(r)),r);
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        return ResponseEntity.status(201).body(Api.success(service.createTask(Integer.parseInt(jwt.getSubject()),b,Api.requestId(r)),r));
    }

    @GetMapping("/calendar")
    public Object calendar(@RequestParam java.time.LocalDate fromDate,@RequestParam java.time.LocalDate toDate,
            @RequestParam(defaultValue="") String status,@RequestParam(required=false) Integer seasonId,
            @RequestParam(required=false) Integer assignedTo,HttpServletRequest r) {
        return Api.success(service.calendar(fromDate,toDate,status,seasonId,assignedTo),r);
    }

    @PutMapping("/tasks/{id}")
    public Object progress(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.progress(Integer.parseInt(jwt.getSubject()),id,b,Api.requestId(r)),r);
    }

    @GetMapping("/tasks/{id}/history")
    public Object history(@PathVariable int id,HttpServletRequest r) {
        return Api.success(service.history(id,Api.requestId(r)),r);
    }

    @PutMapping("/tasks/{id}/assignment")
    public Object assign(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.assign(Integer.parseInt(jwt.getSubject()),id,b,Api.requestId(r)),r);
    }

    @GetMapping("/notifications")
    public Object notifications(@AuthenticationPrincipal Jwt jwt,HttpServletRequest r) {
        return Api.success(service.notifications(Integer.parseInt(jwt.getSubject()),Api.requestId(r)),r);
    }

    @PutMapping("/notifications/{key}/read")
    public Object markRead(@AuthenticationPrincipal Jwt jwt,@PathVariable String key,HttpServletRequest r) {
        return Api.success(service.markRead(Integer.parseInt(jwt.getSubject()),key,Api.requestId(r)),r);
    }

    @GetMapping("/traceability")
    public Object lots(@RequestParam(defaultValue="") String search,HttpServletRequest r) {
        return Api.success(service.lots(search,Api.requestId(r)),r);
    }

    @GetMapping("/traceability/{id}")
    public Object trace(@PathVariable int id,HttpServletRequest r) {
        return Api.success(service.trace(id,Api.requestId(r)),r);
    }

    @GetMapping("/products")
    public Object products(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size,HttpServletRequest r) {
        return Api.success(service.products(page,size,Api.requestId(r)),r);
    }

    @PutMapping("/products/{id}")
    public Object productStorage(@PathVariable int id,@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.productStorage(id,Integer.parseInt(jwt.getSubject()),b,Api.requestId(r)),r);
    }

    @GetMapping("/growth")
    public Object growth(HttpServletRequest r) {
        return Api.success(service.growth(Api.requestId(r)),r);
    }

    @PostMapping("/growth")
    public Object growth(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.growth(Integer.parseInt(jwt.getSubject()),b,Api.requestId(r)),r);
    }

    @GetMapping("/movements")
    public Object movements(HttpServletRequest r) {
        return Api.success(service.movements(Api.requestId(r)),r);
    }

    @PostMapping("/usage")
    public Object consume(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.consume(Integer.parseInt(jwt.getSubject()),b,Api.requestId(r)),r);
    }

    @PostMapping("/movements")
    public Object move(@AuthenticationPrincipal Jwt jwt,@RequestBody JsonNode b,HttpServletRequest r) {
        return Api.success(service.move(Integer.parseInt(jwt.getSubject()),b,Api.requestId(r)),r);
    }
}
