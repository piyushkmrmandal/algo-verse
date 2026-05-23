package com.algoverse.execution.domain;

/**
 * Re-export shim so {@link com.algoverse.execution.kafka.SubmissionEventProducer}
 * (which imports {@code com.algoverse.execution.domain.Submission}) compiles
 * without modification.
 *
 * <p>The actual JPA entity lives in
 * {@link com.algoverse.execution.domain.model.Submission}. This class delegates
 * all producer-facing methods to the entity.
 */
public class Submission extends com.algoverse.execution.domain.model.Submission {

    public Submission() {
        super();
    }
}
