package com.oran.defender.controller;

import com.oran.defender.dto.ActionResponse;
import com.oran.defender.service.ActionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    @GetMapping
    public List<ActionResponse> listActions() {
        return actionService.listActions().stream()
                .map(ActionResponse::from)
                .toList();
    }
}
