package com.farmmanagement.backend;

import java.awt.Desktop;
import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FarmManagementSystemN02Application {
    public static void main(String[] args) {
        var application = new SpringApplication(FarmManagementSystemN02Application.class);
        application.setDefaultProperties(com.farmmanagement.backend.config.WindowsUserEnvironment.defaults(isPackaged()));
        application.addInitializers(new com.farmmanagement.backend.config.DatabaseStartupCheck());
        var context = application.run(args);
        if (!isPackaged() && context.isActive()
                && context.getEnvironment().getProperty("app.open-browser", Boolean.class, false)) {
            String port = context.getEnvironment().getProperty("local.server.port", "8443");
            boolean https = context.getEnvironment().getProperty("server.ssl.enabled", Boolean.class, false);
            String url = (https ? "https://localhost:" : "http://127.0.0.1:") + port + "/";
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

    static boolean isPackaged() {
        var resource = FarmManagementSystemN02Application.class.getResource("FarmManagementSystemN02Application.class");
        return resource != null && resource.getProtocol().equals("jar");
    }
}
