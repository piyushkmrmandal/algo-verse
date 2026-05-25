package com.algoverse.problem.infrastructure.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * MongoDB document storing rich problem content:
 * markdown descriptions, editorial, test cases, and per-language starter code.
 */
@Document(collection = "problem_content")
public class ProblemDocument {

    @Id
    private String id;          // same value as Problem.slug

    private String slug;
    private String descriptionMd;
    private String editorialMd;
    private List<TestCase> examples;
    private List<TestCase> hiddenTests;
    private Map<String, String> starterCode; // key = language: java, python, javascript

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public ProblemDocument() {}

    public ProblemDocument(String id, String slug, String descriptionMd, String editorialMd,
                           List<TestCase> examples, List<TestCase> hiddenTests,
                           Map<String, String> starterCode) {
        this.id = id;
        this.slug = slug;
        this.descriptionMd = descriptionMd;
        this.editorialMd = editorialMd;
        this.examples = examples;
        this.hiddenTests = hiddenTests;
        this.starterCode = starterCode;
    }

    // -----------------------------------------------------------------------
    // Nested record for test cases
    // -----------------------------------------------------------------------

    public record TestCase(String input, String output, String explanation) {}

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescriptionMd() { return descriptionMd; }
    public void setDescriptionMd(String descriptionMd) { this.descriptionMd = descriptionMd; }

    public String getEditorialMd() { return editorialMd; }
    public void setEditorialMd(String editorialMd) { this.editorialMd = editorialMd; }

    public List<TestCase> getExamples() { return examples; }
    public void setExamples(List<TestCase> examples) { this.examples = examples; }

    public List<TestCase> getHiddenTests() { return hiddenTests; }
    public void setHiddenTests(List<TestCase> hiddenTests) { this.hiddenTests = hiddenTests; }

    public Map<String, String> getStarterCode() { return starterCode; }
    public void setStarterCode(Map<String, String> starterCode) { this.starterCode = starterCode; }
}
