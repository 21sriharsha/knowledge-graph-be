package com.knowledge.platform.ingestion.pipeline;

/**
 * The pipeline order, in one place.
 *
 * <p>Handlers carry {@code @Order(HandlerOrder.X)} rather than bare numbers, so the sequence is
 * readable here as a whole rather than reconstructed by grepping for annotations. Values are spaced
 * by ten so a step can be inserted between two existing ones without renumbering anything.
 *
 * <p>The order encodes real dependencies, not preference:
 *
 * <pre>
 *   Validate      nothing downstream should run on an empty or oversized file
 *   Parse         everything after this needs the AST
 *   Metadata      resolves the author and taxonomy the persistence step writes
 *   Persist       canonical content must exist before anything derived can reference it
 *   Graph         needs the persisted article id
 *   Search        needs the article and its final taxonomy
 *   Embedding     scheduled after indexing, so lexical search works even if embedding never does
 *   Read model    needs the graph, since it embeds link resolution and neighbours
 *   Cache         last: invalidate only once everything it would serve is correct
 * </pre>
 */
public final class HandlerOrder {

    public static final int VALIDATION = 10;
    public static final int MARKDOWN_PARSING = 20;
    public static final int METADATA_EXTRACTION = 30;
    public static final int CONTENT_PERSISTENCE = 40;
    public static final int GRAPH_UPDATE = 50;
    public static final int SEARCH_INDEX = 60;
    public static final int EMBEDDING = 70;
    public static final int READ_MODEL = 80;
    public static final int CACHE_INVALIDATION = 90;

    private HandlerOrder() {
        throw new AssertionError("constants holder");
    }
}
