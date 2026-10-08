package com.example.tvmazeapi.controller;

import com.example.tvmazeapi.dto.response.ShowSearchResponse;

import com.example.tvmazeapi.service.ShowService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/search")
    public List<ShowSearchResponse> search(@RequestParam(name = "search_query") @NotBlank String searchQuery) {
        return showService.search(searchQuery);
    }
}
