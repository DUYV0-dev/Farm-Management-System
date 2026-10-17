package com.farmmanagement.backend.management;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManagementPages {
    @GetMapping(value="/admin/users",produces=MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('users:read')")
    public Resource users() { return new ClassPathResource("protected/users.html"); }

    @GetMapping(value="/admin/roles",produces=MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('roles:read')")
    public Resource roles() { return new ClassPathResource("protected/roles.html"); }

    @GetMapping(value="/admin/farms",produces=MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAuthority('farms:read')")
    public Resource farms() { return new ClassPathResource("static/ui/farm.html"); }
}

