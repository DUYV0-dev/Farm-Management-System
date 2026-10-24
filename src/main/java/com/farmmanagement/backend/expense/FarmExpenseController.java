package com.farmmanagement.backend.expense;

import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;
import com.farmmanagement.backend.common.DomainException;
import com.farmmanagement.backend.common.Input;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.JacksonException;

@RestController
@RequestMapping("/api/v1/expenses")
public class FarmExpenseController {
    private final FarmExpenseService service;
    private final ObjectReader expenseReader;
    public FarmExpenseController(FarmExpenseService service, ObjectMapper mapper) {
        this.service = service;
        this.expenseReader = mapper.reader()
            .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    private int positiveId(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0)
            throw new IllegalArgumentException();
        return value.asInt();
    }

    private LocalDate date(String value) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}") || value.startsWith("0000-")) throw new IllegalArgumentException();
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException(); }
    }

    private void query(HttpServletRequest request, boolean paginated) {
        Set<String> allowed = paginated
            ? Set.of("page", "size", "farmId", "seasonId", "category", "fromDate", "toDate")
            : Set.of("farmId", "seasonId", "category", "fromDate", "toDate");
        if (!allowed.containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1 || v[0].isBlank()))
            throw new IllegalArgumentException();
    }

    @GetMapping("/categories")
    public Map<String, Object> categories(HttpServletRequest request) {
        AuthController.noQuery(request);
        return Api.success(service.categories(), request);
    }

    @GetMapping("/options")
    public Map<String, Object> options(HttpServletRequest request) {
        AuthController.noQuery(request);
        return Api.success(service.options(), request);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> recordExpense(@AuthenticationPrincipal Jwt jwt, @RequestBody String json,
                                           HttpServletRequest request) {
        AuthController.noQuery(request);
        JsonNode body;
        try { body = expenseReader.readTree(json); }
        catch (JacksonException ex) {
            throw new DomainException(400, "MALFORMED_REQUEST", "JSON không hợp lệ.");
        }
        if (body == null || !body.isObject()
                || !Set.of("farmId", "seasonId", "category", "amount", "expenseDate", "description")
                    .containsAll(body.propertyNames())) throw new IllegalArgumentException();
        int farmId = positiveId(body, "farmId");
        Integer seasonId = body.has("seasonId") && !body.get("seasonId").isNull() ? positiveId(body, "seasonId") : null;
        ExpenseCategory category = ExpenseCategory.valueOf(Input.text(body, "category", 30));
        JsonNode value = body.get("amount");
        if (value == null || (!value.isNumber() && !value.isTextual())) throw new IllegalArgumentException();
        String amountText = value.isNumber() ? value.decimalValue().toPlainString() : value.asText();
        if (!amountText.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
        BigDecimal amount = new BigDecimal(amountText);
        LocalDate expenseDate = date(Input.text(body, "expenseDate", 10));
        String description = null;
        if (body.has("description") && !body.get("description").isNull()) {
            if (!body.get("description").isTextual()) throw new IllegalArgumentException();
            description = body.get("description").asText();
        }
        FarmExpense expense = service.recordExpense(Integer.parseInt(jwt.getSubject()), farmId, seasonId,
            category, amount, expenseDate, description, Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + expense.id()))
            .body(Api.success(expense, request));
    }

    @GetMapping("/{id}")
    public Map<String, Object> expense(@PathVariable int id, HttpServletRequest request) {
        AuthController.noQuery(request);
        return Api.success(service.getExpense(id), request);
    }

    @GetMapping
    public Map<String, Object> history(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) Integer farmId,
                                      @RequestParam(required = false) Integer seasonId,
                                      @RequestParam(required = false) ExpenseCategory category,
                                      @RequestParam(required = false) String fromDate,
                                      @RequestParam(required = false) String toDate,
                                      HttpServletRequest request) {
        query(request, true);
        return Api.success(service.history(page, size,
            new ExpenseFilter(farmId, seasonId, category, date(fromDate), date(toDate))), request);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) Integer farmId,
                                      @RequestParam(required = false) Integer seasonId,
                                      @RequestParam(required = false) ExpenseCategory category,
                                      @RequestParam(required = false) String fromDate,
                                      @RequestParam(required = false) String toDate,
                                      HttpServletRequest request) {
        query(request, false);
        return Api.success(service.summary(
            new ExpenseFilter(farmId, seasonId, category, date(fromDate), date(toDate))), request);
    }
}
