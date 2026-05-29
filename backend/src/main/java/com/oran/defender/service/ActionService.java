package com.oran.defender.service;

import com.oran.defender.model.Action;
import com.oran.defender.repository.ActionRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionService {

    private final ActionRepository actionRepository;

    public ActionService(ActionRepository actionRepository) {
        this.actionRepository = actionRepository;
    }

    @Transactional(readOnly = true)
    public List<Action> listActions() {
        return actionRepository.findAll(Sort.by("id"));
    }
}
