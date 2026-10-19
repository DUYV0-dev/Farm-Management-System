package com.farmmanagement.backend.usage;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.common.Input;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@RestController
@RequestMapping("/api/v1/material-usages")
public class MaterialUsageController {
    private final MaterialUsageService service;
    private final ObjectReader reader;
    public MaterialUsageController(MaterialUsageService service, ObjectMapper mapper) {
        this.service = service;
        reader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private int id(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0)
            throw new IllegalArgumentException();
        return value.asInt();
    }

    private LocalDate date(String value) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}") || value.startsWith("0000-")) throw new IllegalArgumentException();
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException(); }
    }

    private void query(HttpServletRequest request, boolean paginated) {
        Set<String> allowed = paginated
            ? Set.of("page", "size", "farmId", "seasonId", "materialId", "type", "fromDate", "toDate")
            : Set.of("farmId", "seasonId", "materialId", "type", "fromDate", "toDate");
        if (!allowed.containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1 || v[0].isBlank()))
            throw new IllegalArgumentException();
    }

    @GetMapping("/options")
    public Map<String,Object> options(HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(service.options(), request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> record(@AuthenticationPrincipal Jwt jwt, @RequestBody String json, HttpServletRequest request) {
        AuthController.noQuery(request);
        JsonNode body;
        try { body = reader.readTree(json); }
        catch (JacksonException ex) { throw new DomainException(400, "MALFORMED_REQUEST", "JSON không hợp lệ."); }
        if (body == null || !body.isObject()
                || !Set.of("seasonId", "materialId", "type", "quantity", "applicationDate", "notes").containsAll(body.propertyNames()))
            throw new IllegalArgumentException();
        JsonNode value = body.get("quantity");
        if (value == null || (!value.isNumber() && !value.isTextual())) throw new IllegalArgumentException();
        String quantity = value.isNumber() ? value.decimalValue().toPlainString() : value.asText();
        if (!quantity.matches("[0-9]{1,12}(\\.[0-9]{1,3})?")) throw new IllegalArgumentException();
        String notes = null;
        if (body.has("notes") && !body.get("notes").isNull()) {
            if (!body.get("notes").isTextual()) throw new IllegalArgumentException();
            notes = body.get("notes").asText();
        }
        var usage = service.record(Integer.parseInt(jwt.getSubject()), id(body, "seasonId"), id(body, "materialId"),
            UsageType.valueOf(Input.text(body, "type", 20)), new BigDecimal(quantity),
            date(Input.text(body, "applicationDate", 10)), notes, Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/material-usages/" + usage.id())).body(Api.success(usage, request));
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable int id, HttpServletRequest request) {
        AuthController.noQuery(request); return Api.success(service.get(id), request);
    }

    @GetMapping
    public Map<String,Object> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer farmId, @RequestParam(required = false) Integer seasonId,
            @RequestParam(required = false) Integer materialId, @RequestParam(required = false) UsageType type,
            @RequestParam(required = false) String fromDate, @RequestParam(required = false) String toDate,
            HttpServletRequest request) {
        query(request, true);
        return Api.success(service.history(page, size, new UsageFilter(farmId, seasonId, materialId, type, date(fromDate), date(toDate))), request);
    }

    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(required = false) Integer farmId, @RequestParam(required = false) Integer seasonId,
            @RequestParam(required = false) Integer materialId, @RequestParam(required = false) UsageType type,
            @RequestParam(required = false) String fromDate, @RequestParam(required = false) String toDate,
            HttpServletRequest request) {
        query(request, false);
        return Api.success(service.summary(new UsageFilter(farmId, seasonId, materialId, type, date(fromDate), date(toDate))), request);
    }
}
