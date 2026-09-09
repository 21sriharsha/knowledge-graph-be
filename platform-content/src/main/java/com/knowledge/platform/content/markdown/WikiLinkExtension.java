package com.knowledge.platform.content.markdown;

import org.commonmark.Extension;
import org.commonmark.parser.Parser;

/** commonmark extension teaching the parser about internal {@code [[...]]} references. */
public class WikiLinkExtension implements Parser.ParserExtension {

    private WikiLinkExtension() {
    }

    public static Extension create() {
        return new WikiLinkExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        // Custom link processors run ahead of commonmark's core link handling, so a wiki link is
        // recognised before the bracket pair is considered as an ordinary or reference link.
        parserBuilder.linkProcessor(new WikiLinkProcessor());
    }
}
