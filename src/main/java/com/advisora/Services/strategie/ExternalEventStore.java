package com.advisora.Services.strategie;

import com.advisora.Model.strategie.ExternalEvent;

import java.util.List;

public interface ExternalEventStore {
    void upsert(ExternalEvent e) throws Exception;
    List<ExternalEvent> getActiveEvents() throws Exception;
}