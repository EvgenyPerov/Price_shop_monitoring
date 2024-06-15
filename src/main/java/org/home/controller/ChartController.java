package org.home.controller;

import java.time.LocalDate;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.home.service.PriceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ChartController {

    private final PriceService priceService;

    @GetMapping("/chart")
    public String getPieChart(Model model) {
        Map<String, Map<LocalDate, Float>> chartData = priceService.getResults();
        model.addAttribute("chartData", chartData);
        return "chart";
    }

}
