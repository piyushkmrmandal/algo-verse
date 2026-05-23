package com.algoverse.problem.infrastructure.spec;

import com.algoverse.problem.domain.model.Difficulty;
import com.algoverse.problem.domain.model.Problem;
import com.algoverse.problem.domain.model.ProblemTopic;
import com.algoverse.problem.domain.model.Topic;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

public final class ProblemSpecification {

    private ProblemSpecification() {
    }

    public static Specification<Problem> withDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    public static Specification<Problem> withTopicSlug(String topicSlug) {
        if (!StringUtils.hasText(topicSlug)) {
            return Specification.where(null);
        }
        return (root, query, cb) -> {
            // Subquery: find problem IDs that have this topic slug via problem_topics join
            assert query != null;
            Subquery<UUID> subquery = query.subquery(UUID.class);
            var ptRoot = subquery.from(ProblemTopic.class);
            var topicRoot = subquery.correlate(ptRoot);

            // Join ProblemTopic -> Topic by topicId
            Subquery<UUID> topicSubquery = query.subquery(UUID.class);
            var topicFrom = topicSubquery.from(Topic.class);
            topicSubquery.select(topicFrom.get("id"))
                    .where(cb.equal(topicFrom.get("slug"), topicSlug));

            subquery.select(ptRoot.get("problemId"))
                    .where(ptRoot.get("topicId").in(topicSubquery));

            return root.get("id").in(subquery);
        };
    }

    public static Specification<Problem> withSearch(String query) {
        if (!StringUtils.hasText(query)) {
            return Specification.where(null);
        }
        return (root, criteriaQuery, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + query.toLowerCase() + "%");
    }

    public static Specification<Problem> isPremium(Boolean premium) {
        if (premium == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("isPremium"), premium);
    }

    public static Specification<Problem> isPublished(Boolean published) {
        if (published == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get("isPublished"), published);
    }

    public static Specification<Problem> compose(List<Specification<Problem>> specs) {
        Specification<Problem> result = Specification.where(null);
        for (Specification<Problem> spec : specs) {
            result = result.and(spec);
        }
        return result;
    }
}
