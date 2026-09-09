package com.knowledge.platform.content.markdown;

import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.InlineParserContext;
import org.commonmark.parser.beta.LinkInfo;
import org.commonmark.parser.beta.LinkProcessor;
import org.commonmark.parser.beta.LinkResult;
import org.commonmark.parser.beta.Scanner;

/**
 * Recognises {@code [[Target]]} and {@code [[Target|display text]]} and replaces them with
 * {@link WikiLinkNode}s.
 *
 * <p><b>Why a {@code LinkProcessor} rather than a custom inline parser.</b> commonmark's inline
 * parser handles {@code '['} in a dedicated branch before it consults any custom inline content
 * parser, so a parser registered against {@code '['} is never reached. {@code LinkProcessor} is the
 * supported hook: it runs when a bracket pair closes, ahead of the core link handling, and it is
 * given the bracket's text and a scanner positioned just after the closing bracket.
 *
 * <p>The consequence that matters is that wiki links are recognised <em>by the parser</em>, not by a
 * regular expression over raw Markdown. A {@code [[reference]]} inside a fenced block, an inline code
 * span or an autolink is never offered to this processor at all, so an article that documents the
 * wiki-link syntax does not generate links to its own examples.
 *
 * <p>Shape of the match. For input {@code [[Target]]} the parser opens two brackets, and this
 * processor is invoked when the inner one closes:
 *
 * <pre>
 *   [ [ Target ] ]
 *   ^ ^          ^ scanner is here, peeking at the second ']'
 *   | └─ info.openingBracket()
 *   └─ info.openingBracket().getPrevious(), a Text node ending in '['
 * </pre>
 */
public class WikiLinkProcessor implements LinkProcessor {

    private static final char ALIAS_SEPARATOR = '|';
    private static final char OPENING_BRACKET = '[';
    private static final char CLOSING_BRACKET = ']';

    /** Bounds a reference to the width of the {@code target_reference} column. */
    private static final int MAX_REFERENCE_LENGTH = 500;

    @Override
    public LinkResult process(LinkInfo linkInfo, Scanner scanner, InlineParserContext context) {
        // An explicit inline destination means the author wrote a real link; a wiki link has none.
        if (linkInfo.destination() != null) {
            return LinkResult.none();
        }

        Node previous = linkInfo.openingBracket() == null ? null : linkInfo.openingBracket().getPrevious();
        if (!(previous instanceof Text outerBracket)
                || !outerBracket.getLiteral().endsWith(String.valueOf(OPENING_BRACKET))) {
            return LinkResult.none();
        }
        if (scanner.peek() != CLOSING_BRACKET) {
            return LinkResult.none();
        }

        String raw = linkInfo.text() == null ? "" : linkInfo.text().trim();
        if (raw.isEmpty() || raw.length() > MAX_REFERENCE_LENGTH) {
            return LinkResult.none();
        }

        int separator = raw.indexOf(ALIAS_SEPARATOR);
        String target = separator < 0 ? raw : raw.substring(0, separator).trim();
        String alias = separator < 0 ? null : raw.substring(separator + 1).trim();

        // "[[]]" and "[[|alias]]" reference nothing. Returning none() leaves them as the literal
        // text the author typed, which is more honest than emitting a permanently broken link.
        if (target.isEmpty()) {
            return LinkResult.none();
        }

        // Consume the second ']' so it is not later emitted as stray text.
        scanner.next();

        // LinkResult replaces everything from the inner bracket onwards, but the outer '[' belongs to
        // a bracket that is still open and would survive as literal text. Trim it here.
        String literal = outerBracket.getLiteral();
        if (literal.length() == 1) {
            outerBracket.unlink();
        } else {
            outerBracket.setLiteral(literal.substring(0, literal.length() - 1));
        }

        return LinkResult.replaceWith(new WikiLinkNode(target, alias), scanner.position());
    }
}
