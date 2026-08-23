package com.agentic.sdlc.agents.design;

import java.util.List;

/** One architectural component: what it's responsible for and the key decisions made about it. */
public record ComponentDesign(String name, String responsibility, List<String> keyDecisions) {

    public ComponentDesign {
        keyDecisions = List.copyOf(keyDecisions);
    }
}
