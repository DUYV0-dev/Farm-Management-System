package com.farmmanagement.backend.portal;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
@RequestMapping("/api/v1/store")
public class StoreController {
    private final StorefrontService service;
    public StoreController(StorefrontService service) {this.service=service;}
    @GetMapping("/products") public Map<String,Object> catalog(HttpServletRequest r) {
        AuthController.noQuery(r);return Api.success(service.catalog(),r);
    }
    @PostMapping("/orders") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Map<String,Object> checkout(@RequestBody JsonNode b,HttpServletRequest r) {
        AuthController.noQuery(r);return Api.success(service.checkout(b),r);
    }
    @GetMapping("/orders/{id}") public Map<String,Object> receipt(@PathVariable UUID id,HttpServletRequest r) {
        AuthController.noQuery(r);return Api.success(service.receipt(id),r);
    }
}
