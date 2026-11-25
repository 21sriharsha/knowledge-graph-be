package com.knowledge.platform.search.service;

import com.knowledge.platform.ai.model.dto.DateRange;
import com.knowledge.platform.ai.model.dto.SearchIntent;
import com.knowledge.platform.ai.service.TextEmbeddingModel;
import com.knowledge.platform.author.model.entity.Author;
import com.knowledge.platform.author.service.AuthorService;
import com.knowledge.platform.content.model.entity.Tag;
import com.knowledge.platform.content.model.entity.Topic;
import com.knowledge.platform.content.repository.TagRepository;
import com.knowledge.platform.content.repository.TopicRepository;
import com.knowledge.platform.search.model.dto.SearchFilters;
import com.knowledge.platform.search.model.dto.SearchMode;
import com.knowledge.platform.search.model.dto.SearchPlan;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default SearchPlanner.
 *
 * <p>See {@link SearchPlanner} for what this provides and why it exists.
 */
@Slf4j
@Service
public class DefaultSearchPlannerImpl implements SearchPlanner {

    private final AuthorService authorService;
    private final TopicRepository topicRepository;
    private final TagRepository tagRepository;
    private final TextEmbeddingModel embeddingModel;
    private final SearchProperties properties;

    public DefaultSearchPlannerImpl(
            AuthorService authorService,
            TextEmbeddingModel embeddingModel,
            TopicRepository topicRepository,
            TagRepository tagRepository,
            SearchProperties properties) {
        this.authorService = authorService;
        this.embeddingModel = embeddingModel;
        this.topicRepository = topicRepository;
        this.tagRepository = tagRepository;
        this.properties = properties;
    }

    @Override
    public SearchPlan plan(SearchIntent intent, boolean usedQueryUnderstanding) {
        boolean authorRequested = intent.author() != null && !intent.author().isBlank();
        List<UUID> authorIds = authorRequested ? resolveAuthors(intent.author()) : List.of();

        SearchFilters filters = new SearchFilters(
                authorIds,
                usedQueryUnderstanding ? knownTopics(intent.topics()) : intent.topics(),
                usedQueryUnderstanding ? knownTags(intent.tags()) : intent.tags(),
                toInstantStartOfDay(datesToHonour(intent, usedQueryUnderstanding).from()),
                toInstantEndOfDay(datesToHonour(intent, usedQueryUnderstanding).to()));

        boolean unsatisfiable = filters.hasUnsatisfiableAuthorFilter(authorRequested);

        log.debug("Plan: text='{}' authors={} topics={} tags={} mode={} after={} before={}",
                textToMatch(intent.queryText(), intent.author(),
                        usedQueryUnderstanding && !authorIds.isEmpty()),
                authorIds, filters.topicSlugs(), filters.tagSlugs(), resolveMode(intent, usedQueryUnderstanding),
                filters.publishedAfter(), filters.publishedBefore());

        return new SearchPlan(
                textToMatch(intent.queryText(), intent.author(),
                        usedQueryUnderstanding && !authorIds.isEmpty()),
                filters,
                resolveMode(intent, usedQueryUnderstanding),
                properties.candidateLimit(),
                unsatisfiable,
                usedQueryUnderstanding);
    }

    /**
     * The text to match against article content, with a resolved author's name removed.
     *
     * <p>An author who resolved is already a filter. Leaving their name in the text as well counts
     * the same signal twice, and the second count can only ever subtract: full-text matching is
     * conjunctive, so requiring "Anita" to appear in the prose excludes every article she wrote but
     * did not sign inside the body -- which is all of them.
     *
     * <p>This is not hypothetical. "what has Anita written about postgres" resolved her correctly,
     * kept "Anita" in the query text, and returned nothing, while "by:Anita postgres" returned two
     * articles. The model was told to strip filter phrasing and did not; a model that is asked for
     * a hint should not be able to break a query by giving a slightly wrong one.
     *
     * <p>Applied only to the model's output. The deterministic parser decides its own query text and
     * is trusted with it -- {@code by:harsh} deliberately keeps the name, because an author's name
     * is indexed and matching it in free text is a feature rather than an accident.
     *
     * <p>If nothing is left, that is the right answer too: a query that was entirely a filter
     * has no text to rank by, and the metadata retriever exists for exactly that.
     */
    private String textToMatch(String queryText, String author, boolean authorResolved) {
        if (!authorResolved || queryText == null || author == null || author.isBlank()) {
            return queryText;
        }
        String stripped = queryText;
        for (String token : author.strip().split("\\s+")) {
            if (token.length() < 2) {
                continue;
            }
            stripped = stripped.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(token) + "\\b", " ");
        }
        stripped = stripped.replaceAll("\\s+", " ").strip();
        if (!stripped.equals(queryText)) {
            log.debug("Removed the resolved author's name from the matched text: '{}' -> '{}'",
                    queryText, stripped);
        }
        return stripped;
    }

    /** A year, or a word that implies one. Cheap, and it only has to be better than "always trust". */
    private static final java.util.regex.Pattern TEMPORAL = java.util.regex.Pattern.compile(
            "(?i)\\b(19|20)\\d{2}\\b|\\b(since|before|after|between|recent|recently|latest|last|"
            + "this|past|today|yesterday|week|month|year|quarter|jan|feb|mar|apr|may|jun|jul|aug|sep|"
            + "oct|nov|dec)\\b");

    /**
     * The date range to filter by, dropped when the query never mentioned time.
     *
     * <p>The costliest invention a model made here, and the hardest to see. Asked "what has Anita
     * written about postgres" -- no date, no year, nothing temporal -- qwen2.5:1.5b returned
     * 2022-01-01 to 2022-12-31. Every article on this platform is from 2026, so a correctly
     * resolved author, a valid tag and a working text match all returned nothing, and the response
     * gave no hint why because the interpretation did not report dates at all.
     *
     * <p>Same principle as the invented topics: what the reader typed is honoured, what the model
     * inferred has to be justified by the query. A date filter is far more destructive than a
     * topic one, because it excludes on a dimension the reader was not even thinking about.
     */
    private DateRange datesToHonour(SearchIntent intent, boolean usedQueryUnderstanding) {
        DateRange range = intent.dateRange();
        if (!usedQueryUnderstanding || range == null || (range.from() == null && range.to() == null)) {
            return range == null ? DateRange.unbounded() : range;
        }
        String query = intent.queryText() == null ? "" : intent.queryText();
        if (TEMPORAL.matcher(query).find()) {
            return range;
        }
        log.debug("Ignoring an inferred date range ({} to {}); the query mentions no time period",
                range.from(), range.to());
        return DateRange.unbounded();
    }

    private List<UUID> resolveAuthors(String name) {
        return authorService.resolveByName(name).stream().map(Author::getId).toList();
    }

    /**
     * Keeps only taxonomy the corpus actually has.
     *
     * <p>Applied only to taxonomy the <em>model</em> inferred. A small model told to extract only
     * what is present will enrich anyway: asked "what has Anita written about postgres", one
     * produced topics {@code [database, software development]} -- plausible, related, and matching
     * no article here. Passed through as a filter it erased a correctly resolved author filter and
     * returned nothing, so the model made the search strictly worse than not running it.
     *
     * <p>A filter the reader typed is left exactly as typed, even when it matches nothing. They
     * asked for {@code topic:foo}; quietly searching for something else would be answering a
     * different question. The difference is who chose it.
     *
     * <p>A slug no article carries can only ever match nothing, which makes it useless as a filter
     * and harmful as a conjunction. Dropped rather than left to zero out the result.
     *
     * <p>Unlike an unresolved <em>author</em>, this is not reported as unsatisfiable. A reader who
     * names a person means it, and an empty result explains itself through {@code authorResolved}.
     * Nobody asked for an inferred topic, so its absence is not news -- it is the interpreter being
     * quietly overruled.
     */
    private List<String> knownTopics(List<String> slugs) {
        return retainKnown(slugs, topicRepository.findBySlugIn(normalise(slugs)).stream()
                .map(Topic::getSlug).collect(Collectors.toSet()), "topic");
    }

    private List<String> knownTags(List<String> slugs) {
        return retainKnown(slugs, tagRepository.findBySlugIn(normalise(slugs)).stream()
                .map(Tag::getSlug).collect(Collectors.toSet()), "tag");
    }

    private List<String> retainKnown(List<String> requested, Set<String> known, String kind) {
        if (requested.isEmpty()) {
            return requested;
        }
        List<String> kept = requested.stream()
                .filter(slug -> known.contains(slug.toLowerCase(Locale.ROOT)))
                .toList();
        if (kept.size() < requested.size()) {
            List<String> dropped = requested.stream().filter(slug -> !kept.contains(slug)).toList();
            log.debug("Ignoring {} {} filter(s) no article carries: {}", dropped.size(), kind, dropped);
        }
        return kept;
    }

    /** Slugs are lowercase; a model that capitalises should still match. */
    private List<String> normalise(List<String> slugs) {
        return slugs.stream().map(slug -> slug.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * The plan's mode, constrained by what the system can currently do.
     *
     * <p>A HYBRID or SEMANTIC intent degrades to LEXICAL when no embedding provider is available.
     * Planning semantic retrieval that cannot run would produce an empty contribution and, worse, a
     * ranking whose semantic weight silently applied to nothing.
     */
    /**
     * The retrieval mode, which the model may widen but never narrow.
     *
     * <p>A <em>model's</em> mode choice is a hint, and a small one's is often wrong in the
     * expensive direction. Asked "what has Anita written about postgres" it answered LEXICAL, so
     * only full-text ran -- and "postgres" and "PostgreSQL" stem to different lexemes, so her two
     * PostgreSQL articles matched nothing. The author had resolved correctly; the model's mode
     * choice alone turned a good query into an empty page.
     *
     * <p>An <em>explicit</em> lexical request is different and is honoured. A reader who quotes a
     * phrase or pastes an error string means exactly that, and widening it would answer a question
     * they did not ask.
     *
     * <p>Letting it narrow has no upside. Running the vector retriever as well costs about 24ms and
     * only adds candidates; the ranker already weighs lexical against semantic, so an exact
     * identifier still ranks on its exact match. Letting it narrow risks exactly what happened.
     *
     * <p>Narrowing in the other direction stays, because that one is not a judgement: HYBRID and
     * SEMANTIC degrade to LEXICAL with no embedding provider, since planning retrieval that cannot
     * run would contribute nothing and misreport what happened.
     */
    private SearchMode resolveMode(SearchIntent intent, boolean usedQueryUnderstanding) {
        if (!embeddingModel.isAvailable()) {
            return SearchMode.LEXICAL;
        }
        return switch (intent.mode()) {
            case SEMANTIC -> SearchMode.SEMANTIC;
            case HYBRID -> SearchMode.HYBRID;
            // The only case that turns on who asked.
            case LEXICAL -> usedQueryUnderstanding ? SearchMode.HYBRID : SearchMode.LEXICAL;
        };
    }

    /** A date range from a query is a day, not an instant; the bounds cover the whole day in UTC. */
    private Instant toInstantStartOfDay(java.time.LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant toInstantEndOfDay(java.time.LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }
}
