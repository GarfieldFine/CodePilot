package com.codepilot.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Factory that matches a language/framework combination to the most appropriate ReviewStrategy.
 * Spring auto-injects all beans implementing ReviewStrategy and selects by priority.
 */
@Slf4j
@Component
public class ReviewStrategyFactory {

    private final List<ReviewStrategy> strategies;
    private final ReviewStrategy defaultStrategy;

    public ReviewStrategyFactory(List<ReviewStrategy> strategies) {
        this.strategies = new ArrayList<>(strategies);
        this.strategies.sort(Comparator.comparingInt(ReviewStrategy::getPriority));
        this.defaultStrategy = this.strategies.stream()
                .filter(s -> s instanceof DefaultReviewStrategy)
                .findFirst()
                .orElseGet(DefaultReviewStrategy::new);
        log.info("ReviewStrategyFactory initialized with {} strategies", strategies.size());
    }

    public ReviewStrategy findStrategy(String language, List<String> frameworks) {
        return strategies.stream()
                .filter(s -> s.supports(language, frameworks))
                .findFirst()
                .orElse(defaultStrategy);
    }

    public ReviewStrategy findStrategy(String language) {
        return findStrategy(language, List.of());
    }

    public List<ReviewStrategy> findAllStrategies() {
        return Collections.unmodifiableList(strategies);
    }

    public List<String> collectAllFocusAreas(List<String> languages, List<String> frameworks) {
        Set<String> areas = new LinkedHashSet<>();
        for (ReviewStrategy strategy : strategies) {
            for (String lang : languages) {
                if (strategy.supports(lang, frameworks)) {
                    areas.addAll(strategy.getFocusAreas());
                }
            }
        }
        if (areas.isEmpty()) {
            areas.addAll(defaultStrategy.getFocusAreas());
        }
        return new ArrayList<>(areas);
    }
}
