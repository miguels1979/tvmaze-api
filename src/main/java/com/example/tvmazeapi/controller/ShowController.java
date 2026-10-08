package com.example.tvmazeapi.controller;

import com.example.tvmazeapi.dto.response.ShowSearchResponse;

import com.example.tvmazeapi.service.ShowService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{show_id}")
    public ObjectNode getShow(@PathVariable(name = "show_id") @Positive Integer showId) {
        return showService.getShow(showId);
    }
}
