package com.knowledge.platform.content.markdown;

import org.commonmark.node.CustomNode;
import org.commonmark.node.Visitor;

/**
 * An internal {@code [[Target]]} or {@code [[Target|display text]]} reference in the AST.
 *
 * <p>Wiki links are real AST nodes rather than the output of a regular expression pass over raw
 * Markdown, and the difference is correctness rather than taste. A {@code [[...]]} inside a fenced
 * code block, an inline code span, a link title or an HTML comment is not a reference -- it is an
 * example of one. Only the parser knows which is which, and an article about this platform would
 * otherwise generate broken links to every reference it documents.
 */
public class WikiLinkNode extends CustomNode {

    private final String target;
    private final String displayText;

    public WikiLinkNode(String target, String displayText) {
        this.target = target;
        this.displayText = displayText;
    }

    /** The referenced article as the author wrote it, before slug normalization. */
    public String getTarget() {
        return target;
    }

    /** The text to render. Falls back to the target when no alias was supplied. */
    public String getDisplayText() {
        return displayText == null || displayText.isBlank() ? target : displayText;
    }

    /** Whether the author wrote an explicit {@code |alias}. */
    public boolean hasAlias() {
        return displayText != null && !displayText.isBlank();
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    protected String toStringAttributes() {
        return "target=" + target + ", displayText=" + displayText;
    }
}
