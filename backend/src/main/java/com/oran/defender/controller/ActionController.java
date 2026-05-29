package com.oran.defender.controller;

import com.oran.defender.dto.ActionResponse;
import com.oran.defender.repository.ActionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The remediation action catalog. Read-only; the rows are seeded by DatabaseSeeder. */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionRepository actionRepository;

    public ActionController(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @GetMapping
    public List<ActionResponse> getActions() {
        return actionRepository.findAll().stream().map(ActionResponse::from).toList();
    }
}
