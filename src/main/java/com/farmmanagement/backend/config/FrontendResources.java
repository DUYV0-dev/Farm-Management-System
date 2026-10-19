package com.farmmanagement.backend.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FrontendResources implements WebMvcConfigurer {
    private final boolean workingTree;
    public FrontendResources(@Value("${app.frontend.working-tree:true}") boolean workingTree) { this.workingTree=workingTree; }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path frontend = Path.of("frontend").toAbsolutePath().normalize();
        for (String prefix : new String[] { "frontend", "ui" }) {
            var handler = registry.addResourceHandler("/" + prefix + "/**");
            // IDE Run reads the working tree; packaged deployments use bundled resources.
            if (workingTree && Files.isDirectory(frontend)) handler.addResourceLocations(frontend.toUri().toString());
            handler.addResourceLocations("classpath:/static/ui/");
            handler.setCachePeriod(0);
            for (String[] feature : new String[][] {
                {"farm", "farm", ""}, {"inventory", "material", "material.css"},
                {"expenses", "expenses", "expenses.css"}, {"material-usage", "material-usage", "material-usage.css"}
            }) {
                String directory="features/"+feature[0]+"/";
                resource(registry,frontend,prefix,feature[1]+".html",directory);
                resource(registry,frontend,prefix,"js/"+feature[1]+".js",directory);
                if(!feature[2].isEmpty()) resource(registry,frontend,prefix,"css/"+feature[2],directory);
            }
            for(String file:new String[]{"js/api-client.js","css/style.css","css/connected.css"})
                resource(registry,frontend,prefix,file,"shared/");
        }
    }
    private void resource(ResourceHandlerRegistry registry,Path frontend,String prefix,String url,String directory) {
        var handler=registry.addResourceHandler("/"+prefix+"/"+url);
        Path path=frontend.resolve(directory);
        if(workingTree && Files.isDirectory(path)) handler.addResourceLocations(path.toUri().toString());
        handler.addResourceLocations("classpath:/static/ui/"+directory).setCachePeriod(0);
    }
}
