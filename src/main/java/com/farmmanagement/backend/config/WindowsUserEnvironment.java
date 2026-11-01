package com.farmmanagement.backend.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Refresh missing User environment values for source runs started by an older Windows IDE. */
public final class WindowsUserEnvironment {
    private static final List<String> KEYS = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET",
        "SERVER_ADDRESS", "SERVER_PORT", "SERVER_SSL_ENABLED", "TLS_KEY_STORE", "TLS_KEY_STORE_PASSWORD",
        "LOG_FILE", "APP_OPEN_BROWSER", "APP_FRONTEND_WORKING_TREE", "DB_POOL_SIZE");

    private WindowsUserEnvironment() { }

    public static Map<String, Object> defaults(boolean packaged) {
        return defaults(packaged, System.getProperty("os.name", ""), System.getenv(), System.getProperties(),
            WindowsUserEnvironment::readUser);
    }

    static Map<String, Object> defaults(boolean packaged, String os, Map<String, String> process,
            Properties system, Function<List<String>, Map<String, String>> reader) {
        // Deployed JARs must receive their environment from the deployment, not a personal Windows profile.
        if (packaged || !os.startsWith("Windows")) return Map.of();
        var missing = KEYS.stream().filter(key -> !process.containsKey(key) && !system.containsKey(key)).toList();
        if (missing.isEmpty()) return Map.of();
        var user = reader.apply(missing);
        var values = new HashMap<String, Object>();
        for (String key : missing) {
            String value = user.get(key);
            if (value != null && !value.isBlank()) values.put(key, value);
        }
        return values;
    }

    private static Map<String, String> readUser(List<String> keys) {
        // Only fixed application keys are queried. Values never become shell code or log messages.
        String names = keys.stream().map(key -> "'" + key + "'").collect(Collectors.joining(","));
        String script = "$ErrorActionPreference='Stop';foreach($name in @(" + names + ")){"
            + "$value=[Environment]::GetEnvironmentVariable($name,'User');if($null -ne $value){"
            + "[Console]::Out.WriteLine($name+'='+[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($value)))}}";
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            final Process child = process;
            var output = CompletableFuture.supplyAsync(() -> {
                try (var stream = child.getInputStream()) {
                    return new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
                } catch (IOException error) { throw new UncheckedIOException(error); }
            });
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException();
            var values = new HashMap<String, String>();
            for (String line : output.get(2, TimeUnit.SECONDS).split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String key = line.substring(0, separator);
                if (keys.contains(key)) values.put(key,
                    new String(Base64.getDecoder().decode(line.substring(separator + 1)), StandardCharsets.UTF_8));
            }
            return values;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception error) {
            throw unavailable();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Không đọc được biến môi trường Windows User. Mở lại IntelliJ hoặc chạy scripts/Start-Local.ps1.");
    }
}
