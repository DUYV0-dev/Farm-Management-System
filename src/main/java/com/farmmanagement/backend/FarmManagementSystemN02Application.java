package com.farmmanagement.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.awt.Desktop;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

@SpringBootApplication
public class FarmManagementSystemN02Application {

    public static void main(String[] args) {
        // Local desktop launcher. Explicit environment variables / IDE arguments take precedence.
        var defaults = new HashMap<String, Object>();
        boolean production=production(args);
        boolean desktop=!production && !java.awt.GraphicsEnvironment.isHeadless();
        if (!production && System.getenv("JWT_SECRET") == null && System.getProperty("JWT_SECRET") == null
                && java.util.Arrays.stream(args).noneMatch(a->a.startsWith("--JWT_SECRET=") || a.startsWith("--auth.jwt.secret="))) {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            defaults.put("JWT_SECRET", Base64.getEncoder().encodeToString(key));
        }
        boolean configuredPassword = System.getenv("DB_PASSWORD") != null || System.getProperty("DB_PASSWORD") != null
            || java.util.Arrays.stream(args).anyMatch(a -> a.startsWith("--DB_PASSWORD=") || a.startsWith("--spring.datasource.password="));
        if (!configuredPassword && desktop) {
            System.setProperty("java.awt.headless", "false");
            var password = new JPasswordField(24);
            int answer = JOptionPane.showConfirmDialog(null, password,
                "Mật khẩu PostgreSQL (DB_USERNAME, mặc định farm_app)", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            char[] value = password.getPassword();
            defaults.put("DB_PASSWORD", new String(value));
            java.util.Arrays.fill(value, '\0');
            password.setText("");
        }
        var application = new SpringApplication(FarmManagementSystemN02Application.class);
        application.setDefaultProperties(defaults);
        var context = application.run(args);
        if (context.isActive() && context.getEnvironment().getProperty("app.open-browser", Boolean.class, true)) {
            String port = context.getEnvironment().getProperty("local.server.port", "5500");
            String url = "http://127.0.0.1:" + port + "/";
            try {
                if (System.getProperty("os.name").startsWith("Windows")) {
                    new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                } else if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception e) {
                System.out.println("Mở dashboard tại " + url);
            }
        }
    }

    private static boolean production(String[] args) {
        var profiles=new java.util.ArrayList<String>();
        profiles.add(System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE",""));
        profiles.add(System.getProperty("spring.profiles.active",""));
        for(String arg:args) if(arg.startsWith("--spring.profiles.active=")) profiles.add(arg.substring(arg.indexOf('=')+1));
        return profiles.stream().flatMap(p->java.util.Arrays.stream(p.split(","))).anyMatch(p->p.trim().equals("production"));
    }
}
