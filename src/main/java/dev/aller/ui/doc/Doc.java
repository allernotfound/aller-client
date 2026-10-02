package dev.aller.ui.doc;

import dev.aller.feature.store.Text;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A page of formatted text as a tree of blocks, read from Markdown with HTML mixed in (which is
 * what project descriptions and changelogs on Modrinth are). commonmark turns the Markdown into
 * HTML, jsoup makes a tree of that however badly it is nested, and the tree is walked for the
 * handful of things {@link DocView} can draw. Anything else (scripts, styles, forms) is dropped,
 * so nothing in a page can do more than be text, a picture or a link.
 */
public final class Doc {
    public static final int BOLD = 1, ITALIC = 2, CODE = 4, STRIKE = 8, UNDERLINE = 16;

    public sealed interface Inline permits Run, Pic, Break {}

    /** @param small set smaller, for sub- and superscripts and small print */
    public record Run(String text, int style, String link, boolean small) implements Inline {}

    /** @param width the size the page asks for in its own pixels, 0 if it does not say */
    public record Pic(String url, String alt, String link, float width, float height) implements Inline {}

    public record Break() implements Inline {}

    public enum Type { TEXT, HEADING, LIST, ITEM, QUOTE, CODE, RULE, TABLE, ROW, CELL }

    public enum Align { LEFT, CENTER, RIGHT }

    public static final class Block {
        public final Type type;
        /** A heading's level; for a list, 1 if numbered; for an item, its number. */
        public int level;
        public Align align = Align.LEFT;
        public boolean header;
        public String code = "";
        public final List<Inline> inlines = new ArrayList<>();
        public final List<Block> children = new ArrayList<>();

        Block(Type type) {
            this.type = type;
        }
    }

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();
    private static final int MAX_SOURCE = 200_000, MAX_DEPTH = 24;

    private static final Set<String> BLOCKS = Set.of("p", "div", "center", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
            "pre", "hr", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "details", "summary", "section", "article", "figure",
            "figcaption", "header", "footer", "main", "dl", "dt", "dd", "iframe", "video", "address", "aside", "nav");
    private static final Set<String> DROPPED = Set.of("script", "style", "head", "title", "meta", "link", "svg", "object", "embed", "form",
            "input", "button", "select", "textarea", "noscript", "template", "audio", "canvas", "map", "source", "track");

    public final List<Block> blocks = new ArrayList<>();
    /** Whether any picture is in the page: such a page is laid out again as its pictures arrive. */
    public boolean pictures;
    private final String base;

    private Doc(String base) {
        this.base = base;
    }

    /**
     * @param base the site relative links belong to ("https://modrinth.com")
     */
    public static Doc markdown(String source, String base) {
        Doc doc = new Doc(base);
        if (source == null || source.isBlank()) return doc;
        try {
            String text = source.length() > MAX_SOURCE ? source.substring(0, MAX_SOURCE) : source;
            Element body = Jsoup.parseBodyFragment(RENDERER.render(PARSER.parse(text))).body();
            doc.blocks(body, doc.blocks, Align.LEFT, 0);
        } catch (RuntimeException | StackOverflowError e) {
            dev.aller.AllerClient.LOG.debug("Could not read a page", e);
            doc.blocks.clear();
            Block plain = new Block(Type.TEXT);
            plain.inlines.add(new Run(Text.clean(source.length() > 4000 ? source.substring(0, 4000) : source), 0, null, false));
            doc.blocks.add(plain);
        }
        return doc;
    }

    // ---- blocks ----------------------------------------------------------------------------------

    private void blocks(Element parent, List<Block> out, Align align, int depth) {
        if (depth > MAX_DEPTH) return;
        Block text = null;
        for (Node node : parent.childNodes()) {
            if (node instanceof Element e && BLOCKS.contains(e.normalName())) {
                flush(text, out);
                text = null;
                block(e, out, align, depth + 1);
            } else if (node instanceof TextNode || node instanceof Element) {
                if (text == null) {
                    text = new Block(Type.TEXT);
                    text.align = align;
                }
                inline(node, text.inlines, 0, null, false, depth + 1);
            }
        }
        flush(text, out);
    }

    /** Adds a paragraph unless there is nothing in it to see. */
    private static void flush(Block text, List<Block> out) {
        if (text == null) return;
        for (Inline in : text.inlines) {
            if (in instanceof Pic || in instanceof Run run && !run.text().isBlank()) {
                out.add(text);
                return;
            }
        }
    }

    private void block(Element e, List<Block> out, Align inherited, int depth) {
        Align align = align(e, inherited);
        String tag = e.normalName();
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                Block heading = new Block(Type.HEADING);
                heading.level = tag.charAt(1) - '0';
                heading.align = align;
                for (Node child : e.childNodes()) inline(child, heading.inlines, 0, null, false, depth + 1);
                flush(heading, out);
            }
            case "ul", "ol" -> {
                Block list = new Block(Type.LIST);
                list.level = tag.equals("ol") ? 1 : 0;
                int number = 1;
                try {
                    if (e.hasAttr("start")) number = Integer.parseInt(e.attr("start").trim());
                } catch (NumberFormatException ignored) {
                    // start at one
                }
                for (Element child : e.children()) {
                    if (!child.normalName().equals("li")) continue;
                    Block item = new Block(Type.ITEM);
                    item.level = number++;
                    blocks(child, item.children, align, depth + 1);
                    list.children.add(item);
                }
                if (!list.children.isEmpty()) out.add(list);
            }
            case "li" -> blocks(e, out, align, depth);
            case "blockquote" -> {
                Block quote = new Block(Type.QUOTE);
                blocks(e, quote.children, align, depth + 1);
                if (!quote.children.isEmpty()) out.add(quote);
            }
            case "pre" -> {
                Block code = new Block(Type.CODE);
                code.code = printable(e.wholeText()).stripTrailing();
                if (!code.code.isBlank()) out.add(code);
            }
            case "hr" -> out.add(new Block(Type.RULE));
            case "table" -> {
                Block table = new Block(Type.TABLE);
                rows(e, table, align, depth + 1);
                if (!table.children.isEmpty()) out.add(table);
            }
            case "details" -> {
                // Drawn open: there is nothing to gain from hiding text behind a click here.
                for (Element child : e.children()) {
                    if (!child.normalName().equals("summary")) continue;
                    Block summary = new Block(Type.TEXT);
                    summary.align = align;
                    for (Node n : child.childNodes()) inline(n, summary.inlines, BOLD, null, false, depth + 1);
                    flush(summary, out);
                }
                Block inner = new Block(Type.QUOTE);
                for (Node n : e.childNodes()) {
                    if (n instanceof Element child && child.normalName().equals("summary")) continue;
                    if (n instanceof Element child && BLOCKS.contains(child.normalName())) {
                        block(child, inner.children, align, depth + 1);
                    } else {
                        Block text = new Block(Type.TEXT);
                        text.align = align;
                        inline(n, text.inlines, 0, null, false, depth + 1);
                        flush(text, inner.children);
                    }
                }
                if (!inner.children.isEmpty()) out.add(inner);
            }
            case "iframe", "video" -> {
                String target = video(e.attr("src"));
                if (target == null && !e.select("source[src]").isEmpty()) target = link(e.select("source[src]").first().attr("src"));
                if (target != null) {
                    Block text = new Block(Type.TEXT);
                    text.align = align;
                    text.inlines.add(new Run("▶ Watch the video", BOLD, target, false));
                    out.add(text);
                }
            }
            default -> blocks(e, out, align, depth);
        }
    }

    private void rows(Element e, Block table, Align align, int depth) {
        if (depth > MAX_DEPTH) return;
        for (Element child : e.children()) {
            switch (child.normalName()) {
                case "thead", "tbody", "tfoot" -> rows(child, table, align, depth + 1);
                case "tr" -> {
                    Block row = new Block(Type.ROW);
                    for (Element cell : child.children()) {
                        String tag = cell.normalName();
                        if (!tag.equals("td") && !tag.equals("th")) continue;
                        Block c = new Block(Type.CELL);
                        c.header = tag.equals("th");
                        blocks(cell, c.children, align(cell, align), depth + 1);
                        if (c.header) bold(c.children);
                        row.children.add(c);
                    }
                    if (!row.children.isEmpty() && table.children.size() < 200) table.children.add(row);
                }
                default -> {}
            }
        }
    }

    private static void bold(List<Block> blocks) {
        for (Block b : blocks) {
            for (int i = 0; i < b.inlines.size(); i++) {
                if (b.inlines.get(i) instanceof Run run) b.inlines.set(i, new Run(run.text(), run.style() | BOLD, run.link(), run.small()));
            }
            bold(b.children);
        }
    }

    private static Align align(Element e, Align inherited) {
        if (e.normalName().equals("center")) return Align.CENTER;
        String value = e.attr("align").toLowerCase(Locale.ROOT);
        String style = e.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.equals("center") || value.equals("middle") || style.contains("text-align:center")) return Align.CENTER;
        if (value.equals("right") || style.contains("text-align:right")) return Align.RIGHT;
        if (value.equals("left") || style.contains("text-align:left")) return Align.LEFT;
        return inherited;
    }

    // ---- text ------------------------------------------------------------------------------------

    private void inline(Node node, List<Inline> out, int style, String link, boolean small, int depth) {
        if (depth > MAX_DEPTH * 2) return;
        if (node instanceof TextNode text) {
            String value = printable(text.getWholeText()).replaceAll("\\s+", " ");
            if (!value.isEmpty()) out.add(new Run(value, style, link, small));
            return;
        }
        if (!(node instanceof Element e)) return;
        String tag = e.normalName();
        if (DROPPED.contains(tag)) return;
        switch (tag) {
            case "br" -> out.add(new Break());
            case "img" -> {
                String src = link(e.attr("src"));
                if (src != null) {
                    pictures = true;
                    out.add(new Pic(src, Text.clean(e.attr("alt")), link, pixels(e.attr("width")), pixels(e.attr("height"))));
                }
            }
            case "a" -> {
                String target = link(e.attr("href"));
                for (Node child : e.childNodes()) inline(child, out, style, target != null ? target : link, small, depth + 1);
            }
            case "strong", "b" -> children(e, out, style | BOLD, link, small, depth);
            case "em", "i", "cite", "dfn" -> children(e, out, style | ITALIC, link, small, depth);
            case "code", "kbd", "tt", "samp" -> children(e, out, style | CODE, link, small, depth);
            case "del", "s", "strike" -> children(e, out, style | STRIKE, link, small, depth);
            case "u", "ins" -> children(e, out, style | UNDERLINE, link, small, depth);
            case "sub", "sup", "small" -> children(e, out, style, link, true, depth);
            default -> {
                // A block inside a line (a link wrapped round a heading): its text, on a line of its own.
                boolean block = BLOCKS.contains(tag);
                if (block && !out.isEmpty()) out.add(new Break());
                children(e, out, style, link, small, depth);
                if (block) out.add(new Break());
            }
        }
    }

    private void children(Element e, List<Inline> out, int style, String link, boolean small, int depth) {
        for (Node child : e.childNodes()) inline(child, out, style, link, small, depth + 1);
    }

    /** Text with what cannot be drawn taken out; spacing is left as it is. */
    private static String printable(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n' || cp == '\t' || cp == ' ') out.append((char) cp);
            else if (cp == 0xA0) out.append(' ');
            else if (Text.drawable(cp)) out.appendCodePoint(cp);
        }
        return out.toString();
    }

    private static float pixels(String attribute) {
        String value = attribute.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("px")) value = value.substring(0, value.length() - 2).trim();
        try {
            float n = Float.parseFloat(value);
            return n > 0 && n < 10000 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** An address made whole and checked: http and https only. */
    private String link(String href) {
        String value = href == null ? "" : href.trim();
        if (value.isEmpty() || value.startsWith("#")) return null;
        if (value.startsWith("//")) value = "https:" + value;
        else if (value.startsWith("/")) value = base + value;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? value : null;
    }

    /** Where an embedded player can be watched in a browser instead. */
    private String video(String src) {
        String url = link(src);
        if (url == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("youtube(?:-nocookie)?\\.com/embed/([A-Za-z0-9_-]{6,})").matcher(url);
        return m.find() ? "https://www.youtube.com/watch?v=" + m.group(1) : url;
    }
}
