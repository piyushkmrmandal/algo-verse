package com.algoverse.problem.service;

import com.algoverse.problem.application.dto.ProblemPageDto;
import com.algoverse.problem.application.dto.ProblemSummaryDto;
import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.infrastructure.elasticsearch.ProblemSearchDoc;
import com.algoverse.problem.infrastructure.elasticsearch.ProblemSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Delegates full-text problem search to Elasticsearch.
 * Builds a multi-field criteria query combining title, descriptionSnippet,
 * difficulty, and topicSlugs into a single Elasticsearch request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ProblemSearchRepository searchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Search problems in Elasticsearch by keyword (title + description snippet),
     * optionally filtered by difficulty and/or topic slug.
     *
     * @param query      free-text search term
     * @param difficulty optional difficulty filter (EASY / MEDIUM / HARD)
     * @param topicSlug  optional topic slug filter
     * @param page       0-indexed page number
     * @param size       page size
     * @return paginated list of matching problem summaries
     */
    public ProblemPageDto search(String query, String difficulty, String topicSlug, int page, int size) {
        log.debug("ES search: query='{}', difficulty={}, topicSlug={}, page={}, size={}",
                query, difficulty, topicSlug, page, size);

        Criteria criteria = new Criteria();

        if (StringUtils.hasText(query)) {
            criteria = criteria.and(new Criteria("title").contains(query))
                               .or(new Criteria("descriptionSnippet").contains(query));
        }

        if (StringUtils.hasText(difficulty)) {
            criteria = criteria.and(new Criteria("difficulty").is(difficulty.toUpperCase()));
        }

        if (StringUtils.hasText(topicSlug)) {
            criteria = criteria.and(new Criteria("topicSlugs").is(topicSlug));
        }

        CriteriaQuery esQuery = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(page, size));

        SearchHits<ProblemSearchDoc> hits = elasticsearchOperations.search(esQuery, ProblemSearchDoc.class);

        List<ProblemSummaryDto> items = hits.getSearchHits().stream()
                .map(hit -> toSummaryDto(hit.getContent()))
                .toList();

        long totalHits = hits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalHits / size);
        boolean hasNext = (page + 1) < totalPages;

        return new ProblemPageDto(items, totalHits, totalPages, page, size, hasNext);
    }

    /**
     * Index (or re-index) a single problem document in Elasticsearch.
     */
    public void index(ProblemSearchDoc doc) {
        log.debug("Indexing problem in ES: slug={}", doc.getSlug());
        searchRepository.save(doc);
    }

    /**
     * Remove a problem from the search index by its slug (used as ES document id).
     */
    public void delete(String slug) {
        log.debug("Removing problem from ES index: slug={}", slug);
        searchRepository.deleteById(slug);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ProblemSummaryDto toSummaryDto(ProblemSearchDoc doc) {
        Difficulty diff;
        try {
            diff = Difficulty.valueOf(doc.getDifficulty());
        } catch (Exception e) {
            diff = Difficulty.EASY;
        }

        // ES documents don't carry submission counts — return zeroes for list view
        return ProblemSummaryDto.from(
                tryParseUUID(doc.getId()),
                doc.getSlug(),
                doc.getTitle(),
                diff,
                doc.getTopicSlugs() != null ? doc.getTopicSlugs() : List.of(),
                false,
                0,
                0
        );
    }

    private UUID tryParseUUID(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}
