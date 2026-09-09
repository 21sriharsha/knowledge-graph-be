package com.knowledge.platform.delivery.service;

import com.knowledge.platform.common.model.Slug;
import com.knowledge.platform.content.model.entity.Article;
import com.knowledge.platform.content.model.entity.PublicationState;
import com.knowledge.platform.content.service.ArticleService;
import com.knowledge.platform.delivery.model.dto.TrendingArticle;
import com.knowledge.platform.delivery.repository.ArticleViewRepository;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default ArticleViewService.
 *
 * <p>See {@link ArticleViewService} for what this provides and why it exists.
 *
 * <p><b>Views are buffered in memory and flushed periodically.</b> Reading an article is the
 * hottest path in the application, and writing a row on every read would turn every read into a
 * write -- worse, into a write contending on one row per popular article, which is exactly the row
 * every concurrent reader of that article would be waiting for. Buffering makes it one statement per
 * article per flush regardless of traffic.
 *
 * <p>The cost of buffering is that a crash loses up to one flush interval of counts. That is the
 * right trade for this data: an approximate view count is useful and a lost one is not a
 * correctness problem, which is emphatically not true of anything else the platform stores.
 *
 * <p>Safe on more than one instance without coordination. Each instance buffers its own counts and
 * the flush adds rather than assigns, so the totals converge. This is deliberately <em>not</em> a
 * run-once scheduled task and must not be given a ShedLock guard -- every instance has its own
 * counts to write.
 */
@Slf4j
@Service
public class DefaultArticleViewServiceImpl implements ArticleViewService {

    /** Bounds a burst, and bounds what a crash can lose. */
    private static final int FLUSH_INTERVAL_MS = 30_000;

    /** A trending list that changes second to second is noise; five minutes is plenty fresh. */
    static final String TRENDING_CACHE = "trendingArticles";

    private final ArticleViewRepository views;
    private final ArticleService articles;
    private final Map<UUID, AtomicLong> buffer = new ConcurrentHashMap<>();

    public DefaultArticleViewServiceImpl(ArticleViewRepository views, ArticleService articles) {
        this.views = views;
        this.articles = articles;
    }

    @Override
    @Transactional(readOnly = true)
    public void recordView(Slug slug) {
        // findBySlug, not requirePublishedBySlug: the "require" variants signal a missing article by
        // throwing, and an exception thrown inside a transaction marks it rollback-only even when
        // the caller catches it -- the commit then fails anyway, turning a view of a stale link into
        // a failed request. A lookup that returns empty is the correct tool for a condition that is
        // not an error.
        articles.findBySlug(slug)
                .filter(article -> article.getPublicationState() == PublicationState.PUBLISHED)
                .ifPresentOrElse(
                        article -> buffer
                                .computeIfAbsent(article.getId(), id -> new AtomicLong())
                                .incrementAndGet(),
                        () -> log.debug("Ignoring a view for unknown or unpublished '{}'",
                                slug.value()));
    }

    @Override
    // `unless` because an empty result is both cheap to recompute -- an index scan that finds
    // nothing -- and the one answer worth retrying. Caching it means a platform whose first readers
    // have just arrived keeps reporting that nothing has been read for another five minutes.
    @Cacheable(cacheNames = TRENDING_CACHE, key = "#days + ':' + #limit",
            unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<TrendingArticle> trending(int days, int limit) {
        int window = Math.clamp(days, 1, 365);
        int size = Math.clamp(limit, 1, 50);
        // Inclusive of today, so `days = 1` means "today" rather than "today and yesterday".
        LocalDate since = LocalDate.now().minusDays(window - 1L);
        return views.findTrending(since, size);
    }

    @Override
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    // REQUIRES_NEW because the flush is triggered by a timer rather than by a request, and must not
    // silently join or depend on whatever transaction a caller happens to be in.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        // Drain before writing, not after. A failed write then loses those counts rather than
        // replaying them on the next flush and counting them twice -- undercounting is a smaller
        // lie than overcounting, and this path adds rather than assigns.
        Map<UUID, Long> batch = new HashMap<>();
        for (UUID articleId : List.copyOf(buffer.keySet())) {
            AtomicLong counter = buffer.remove(articleId);
            if (counter != null) {
                long count = counter.get();
                if (count > 0) {
                    batch.put(articleId, count);
                }
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            views.addViews(batch, LocalDate.now());
            log.debug("Flushed view counts for {} article(s)", batch.size());
        } catch (RuntimeException e) {
            log.warn("Could not flush {} view count(s); they are lost", batch.size(), e);
        }
    }

    /** Shutdown is the one moment a flush is guaranteed to matter, so it is not left to the timer. */
    @PreDestroy
    void flushOnShutdown() {
        flush();
    }
}
