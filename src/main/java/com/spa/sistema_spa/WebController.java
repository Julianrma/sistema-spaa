package com.spa.sistema_spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/sucursales")
    public String sucursales() {
        return "sucursales";
    }

    @GetMapping("/agendar")
    public String agendar() {
        return "agendar";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "admin-dashboard";
    }
}