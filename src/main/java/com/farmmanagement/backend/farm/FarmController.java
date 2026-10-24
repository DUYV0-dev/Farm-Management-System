package com.farmmanagement.backend.farm;

import com.farmmanagement.backend.common.Input;
import com.farmmanagement.backend.auth.AuthController;
import com.farmmanagement.backend.common.Api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class FarmController {
    private final FarmService service;
    public FarmController(FarmService service) { this.service = service; }

    private int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private void id(int id) { if (id <= 0) throw new IllegalArgumentException(); }

    private String optText(JsonNode body, String name, int max) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw new IllegalArgumentException();
        String s = v.asText().trim();
        if (s.length() > max) throw new IllegalArgumentException();
        return s.isEmpty() ? null : s;
    }

    private int reqInt(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt()) throw new IllegalArgumentException();
        int val = v.asInt();
        if (val <= 0) throw new IllegalArgumentException();
        return val;
    }

    private LocalDate optDate(JsonNode body, String name) {
        JsonNode v = body.get(name);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) throw new IllegalArgumentException();
        String s = v.asText().trim();
        if (s.isEmpty()) return null;
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}") || s.startsWith("0000-")) throw new IllegalArgumentException();
        try { return LocalDate.parse(s); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException(); }
    }

    // ─── Farms ───────────────────────────────────────────────────────────────
    @GetMapping("/farms")
    public Map<String, Object> farms(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(defaultValue = "") String search,
                                     HttpServletRequest request) {
        if (!Set.of("page", "size", "search").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listFarms(page, size, search.trim()), request);
    }

    @GetMapping("/farms/{id}")
    public Map<String, Object> farm(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getFarm(id), request);
    }

    @PostMapping(value = "/farms", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createFarm(@AuthenticationPrincipal Jwt jwt,
                                        @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "name", "address", "areaHa", "description");
        String name = Input.text(body, "name", 100);
        String address = Input.text(body, "address", 255);
        var farm = service.createFarm(actor(jwt), name, address,
            optText(body, "areaHa", 20),
            optText(body, "description", 1000),
            Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/farms/" + farm.id()))
            .body(Api.success(farm, request));
    }

    @PutMapping(value = "/farms/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateFarm(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "address", "areaHa", "description");
        String name = Input.text(body, "name", 100);
        String address = Input.text(body, "address", 255);
        return Api.success(service.updateFarm(actor(jwt), id, name, address,
            optText(body, "areaHa", 20),
            optText(body, "description", 1000),
            Api.requestId(request)), request);
    }

    @PutMapping(value = "/farms/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> farmStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.farmStatus(actor(jwt), id, Input.text(body, "status", 10),
            Api.requestId(request)), request);
    }

    // ─── Plots ───────────────────────────────────────────────────────────────
    @GetMapping("/plots")
    public Map<String, Object> plots(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(defaultValue = "") String search,
                                     @RequestParam(required = false) Integer farmId,
                                     HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "farmId").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listPlots(page, size, search.trim(), farmId), request);
    }

    @GetMapping("/plots/{id}")
    public Map<String, Object> plot(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getPlot(id), request);
    }

    @PostMapping(value = "/plots", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPlot(@AuthenticationPrincipal Jwt jwt,
                                        @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "farmId", "name", "areaHa", "soilType", "location", "description");
        int farmId = reqInt(body, "farmId");
        String name = Input.text(body, "name", 100);
        var plot = service.createPlot(actor(jwt), farmId, name,
            optText(body, "areaHa", 20),
            optText(body, "soilType", 100),
            optText(body, "location", 255),
            optText(body, "description", 1000),
            Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/plots/" + plot.id()))
            .body(Api.success(plot, request));
    }

    @PutMapping(value = "/plots/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updatePlot(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "areaHa", "soilType", "location", "description");
        String name = Input.text(body, "name", 100);
        return Api.success(service.updatePlot(actor(jwt), id, name,
            optText(body, "areaHa", 20),
            optText(body, "soilType", 100),
            optText(body, "location", 255),
            optText(body, "description", 1000),
            Api.requestId(request)), request);
    }

    @PutMapping(value = "/plots/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> plotStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.plotStatus(actor(jwt), id, Input.text(body, "status", 10),
            Api.requestId(request)), request);
    }

    // ─── Crop Catalog ────────────────────────────────────────────────────────
    @GetMapping("/crops")
    public Map<String, Object> crops(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(defaultValue = "") String search,
                                     HttpServletRequest request) {
        if (!Set.of("page", "size", "search").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listCrops(page, size, search.trim()), request);
    }

    @GetMapping("/crops/{id}")
    public Map<String, Object> crop(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getCrop(id), request);
    }

    @PostMapping(value = "/crops", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCrop(@AuthenticationPrincipal Jwt jwt,
                                        @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        Input.fields(body, "name", "scientificName", "category", "growthDays", "description");
        String name = Input.text(body, "name", 100);
        var crop = service.createCrop(actor(jwt), name,
            optText(body, "scientificName", 150),
            optText(body, "category", 100),
            optText(body, "growthDays", 10),
            optText(body, "description", 1000),
            Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/crops/" + crop.id()))
            .body(Api.success(crop, request));
    }

    @PutMapping(value = "/crops/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateCrop(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "name", "scientificName", "category", "growthDays", "description");
        String name = Input.text(body, "name", 100);
        return Api.success(service.updateCrop(actor(jwt), id, name,
            optText(body, "scientificName", 150),
            optText(body, "category", 100),
            optText(body, "growthDays", 10),
            optText(body, "description", 1000),
            Api.requestId(request)), request);
    }

    @PutMapping(value = "/crops/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> cropStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.cropStatus(actor(jwt), id, Input.text(body, "status", 10),
            Api.requestId(request)), request);
    }
    
    // ─── Crop Seasons ────────────────────────────────────────────────────────
    @GetMapping("/seasons")
    public Map<String, Object> seasons(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(defaultValue = "") String search,
                                       @RequestParam(required = false) Integer plotId,
                                       HttpServletRequest request) {
        if (!Set.of("page", "size", "search", "plotId").containsAll(request.getParameterMap().keySet())
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new IllegalArgumentException();
        return Api.success(service.listSeasons(page, size, search.trim(), plotId), request);
    }
    
    @GetMapping("/seasons/{id}")
    public Map<String, Object> season(@PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        return Api.success(service.getSeason(id), request);
    }
    
    @PostMapping(value = "/seasons", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSeason(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody JsonNode body, HttpServletRequest request) {
        AuthController.noQuery(request);
        int plotId, cropId;
        String status, seasonName = null, description = null;
        LocalDate startDate = null, endDate = null;

        if (body.has("seasonName") || body.has("startDate") || body.has("endDate") || body.has("description")) {
            Input.fields(body, "seasonName", "plotId", "cropId", "startDate", "endDate", "description", "status");
            seasonName = optText(body, "seasonName", 100);
            plotId = reqInt(body, "plotId");
            cropId = reqInt(body, "cropId");
            startDate = optDate(body, "startDate");
            endDate = optDate(body, "endDate");
            description = optText(body, "description", 1000);
            status = Input.text(body, "status", 20);
        } else {
            Input.fields(body, "plotId", "cropId", "status");
            plotId = reqInt(body, "plotId");
            cropId = reqInt(body, "cropId");
            status = Input.text(body, "status", 20);
        }

        var season = service.createSeason(actor(jwt), seasonName, plotId, cropId, startDate, endDate, description, status, Api.requestId(request));
        return ResponseEntity.created(URI.create("/api/v1/seasons/" + season.id()))
            .body(Api.success(season, request));
    }

    @PutMapping(value = "/seasons/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updateSeason(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                            @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        Input.fields(body, "seasonName", "startDate", "endDate", "description", "status");
        String seasonName = optText(body, "seasonName", 100);
        LocalDate startDate = optDate(body, "startDate");
        LocalDate endDate = optDate(body, "endDate");
        String description = optText(body, "description", 1000);
        String status = Input.text(body, "status", 20);

        var season = service.updateSeason(actor(jwt), id, seasonName, startDate, endDate, description, status, Api.requestId(request));
        return Api.success(season, request);
    }

    @PutMapping(value = "/seasons/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> seasonStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                            @RequestBody JsonNode body, HttpServletRequest request) {
        id(id); AuthController.noQuery(request); Input.fields(body, "status");
        return Api.success(service.updateSeasonStatus(actor(jwt), id, Input.text(body, "status", 20),
            Api.requestId(request)), request);
    }

    @DeleteMapping("/seasons/{id}")
    public Map<String, Object> deleteSeason(@AuthenticationPrincipal Jwt jwt, @PathVariable int id, HttpServletRequest request) {
        id(id); AuthController.noQuery(request);
        service.deleteSeason(actor(jwt), id, Api.requestId(request));
        return Api.success(Map.of("deleted", true, "id", id), request);
    }
}
