package com.algoverse.problem.infrastructure.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

/**
 * Elasticsearch document for full-text problem search.
 * Stores a lightweight projection of each problem — no heavy content.
 */
@Document(indexName = "problems")
public class ProblemSearchDoc {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String difficulty;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String descriptionSnippet;  // first 200 chars of description

    @Field(type = FieldType.Keyword)
    private List<String> topicSlugs;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public ProblemSearchDoc() {}

    public ProblemSearchDoc(String id, String slug, String title, String difficulty,
                            String descriptionSnippet, List<String> topicSlugs) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.difficulty = difficulty;
        this.descriptionSnippet = descriptionSnippet;
        this.topicSlugs = topicSlugs;
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getDescriptionSnippet() { return descriptionSnippet; }
    public void setDescriptionSnippet(String descriptionSnippet) {
        this.descriptionSnippet = descriptionSnippet;
    }

    public List<String> getTopicSlugs() { return topicSlugs; }
    public void setTopicSlugs(List<String> topicSlugs) { this.topicSlugs = topicSlugs; }
}
