package com.farmmanagement.backend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class FrontendEntryController {
    @GetMapping({"/", "/index.html"})
    public String home() { return "forward:/index-shell.html"; }
    @GetMapping({"/frontend/index.html","/frontend/login.html","/frontend/dashboard.html","/frontend/workers.html",
        "/frontend/activities.html","/frontend/schedule.html","/frontend/reports.html","/frontend/notifications.html"})
    public String entry(HttpServletRequest request) {
        String page=request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1).replace(".html","");
        page=switch(page) { case "index" -> "tasks"; case "login", "dashboard" -> "home"; case "workers" -> "staff"; default -> page; };
        return "redirect:/#"+page;
    }
    @GetMapping({"/farm.html","/material.html","/expenses.html","/material-usage.html","/workers.html","/dashboard.html","/login.html",
        "/frontend/farm.html","/frontend/material.html","/frontend/expenses.html","/frontend/material-usage.html"})
    public String module(HttpServletRequest request) {
        String page=request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/')+1).replace(".html","");
        if(page.equals("workers")) page="staff";
        if(page.equals("dashboard") || page.equals("login")) page="home";
        return "redirect:/#"+page;
    }
}
