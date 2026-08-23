package com.agentic.sdlc.agents.design;

import java.util.List;

/**
 * The output of {@link ArchitectureDesignAgent}: the component breakdown
 * plus the architectural risks that follow directly from which components
 * were (or were not) included. This is also this pipeline's first pass at
 * the assignment's "Validation and Risk Control" requirement -- a risk
 * like "no authentication component" falls straight out of the same scan
 * that produced the design.
 */
public record DesignDocument(String rawRequirement, List<ComponentDesign> components, List<String> architecturalRisks) {

    public DesignDocument {
        components = List.copyOf(components);
        architecturalRisks = List.copyOf(architecturalRisks);
    }
}
