package dev.aller.feature.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aller.AllerClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modrinth's public API (v2), as far as the store needs it: search, a project's page, its
 * versions, and looking files up by hash. Every call blocks, so they are made from {@link Store}'s
 * worker threads. Nothing here needs an account, and nothing but the query is sent.
 */
public final class Modrinth {
    public static final String SITE = "https://modrinth.com";
    private static final String API = "https://api.modrinth.com/v2";
    /** Modrinth asks for an agent that says who is calling. */
    static final String AGENT = "AllerClient/" + AllerClient.VERSION + " (Minecraft mod)";

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private Modrinth() {}

    public enum Sort {
        RELEVANCE("relevance", "Relevance"), DOWNLOADS("downloads", "Downloads"), FOLLOWS("follows", "Followers"),
        NEWEST("newest", "Newest"), UPDATED("updated", "Recently updated");

        final String index;
        public final String label;

        Sort(String index, String label) {
            this.index = index;
            this.label = label;
        }
    }

    /** A filter from Modrinth's own list, under the heading it is shown beneath ("categories", "features", "resolutions"). */
    public record Category(String name, String header) {
        /** Whether a project has only one of this header's values, so several chosen mean "any of these". */
        public boolean exclusive() {
            return header.equals("resolutions") || header.equals("performance impact");
        }

        /** "vanilla-like" as "Vanilla-like", "8x-" as "8x or lower". */
        public String label() {
            if (name.endsWith("x-")) return name.substring(0, name.length() - 1) + " or lower";
            if (name.endsWith("x+")) return name.substring(0, name.length() - 1) + " or higher";
            return Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('-', ' ');
        }
    }

    public record Shot(String url, String rawUrl, String title, String description, boolean featured) {}

    public record Link(String label, String url) {}

    /** A project: what a search result carries, and once {@link #full} the rest of its page. */
    public static final class Project {
        public String id = "", slug = "", title = "", summary = "", author = "", iconUrl, banner, license = "";
        public List<String> categories = List.of();
        public long downloads, follows;
        public Instant updated, created;
        /** The colour Modrinth picked out of the icon, or 0. */
        public int color;

        public boolean full;
        public String body = "";
        public List<Shot> gallery = List.of();
        public List<Link> links = List.of();
        public List<String> gameVersions = List.of();

        /** Takes over everything a freshly fetched page knows. */
        void take(Project page) {
            slug = page.slug;
            title = page.title;
            summary = page.summary;
            author = page.author;
            iconUrl = page.iconUrl;
            banner = page.banner;
            license = page.license;
            categories = page.categories;
            downloads = page.downloads;
            follows = page.follows;
            updated = page.updated;
            created = page.created;
            color = page.color;
            body = page.body;
            gallery = page.gallery;
            links = page.links;
            gameVersions = page.gameVersions;
            full = true;
        }

        public String page(Kind kind) {
            return SITE + "/" + kind.type + "/" + (slug.isEmpty() ? id : slug);
        }
    }

    public record FileRef(String url, String filename, long size, String sha1, String sha512) {}

    public static final class Version {
        public String id = "", projectId = "", name = "", number = "", changelog = "", type = "release";
        public Instant published;
        public long downloads;
        public List<String> gameVersions = List.of();
        /** The file to fetch; null for a version with nothing usable in it. */
        public FileRef file;
    }

    public record Page(List<Project> hits, int total) {}

    /**
     * @param categories filters that must all hold, except the exclusive ones, where any one of those chosen will do
     * @param gameVersion only projects with a file for this version, or null for any
     */
    public record Query(Kind kind, String text, Sort sort, Set<Category> categories, String gameVersion, int offset, int limit) {}

    // ---- calls ---------------------------------------------------------------------------------

    public static Page search(Query q) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + q.kind.type));
        if (q.gameVersion != null) facets.add(group("versions:" + q.gameVersion));
        if (q.kind.searchLoaders) {
            JsonArray loaders = new JsonArray();
            for (String loader : q.kind.loaders) loaders.add("categories:" + loader);
            facets.add(loaders);
        }
        // Filters that exclude one another (a pack has one resolution, one performance cost) are "any of".
        java.util.Map<String, JsonArray> either = new java.util.HashMap<>();
        for (Category c : q.categories) {
            if (c.exclusive()) either.computeIfAbsent(c.header, k -> new JsonArray()).add("categories:" + c.name);
            else facets.add(group("categories:" + c.name));
        }
        either.values().forEach(facets::add);
        JsonObject root = get("/search?query=" + enc(q.text) + "&index=" + q.sort.index + "&offset=" + q.offset + "&limit=" + q.limit
                + "&facets=" + enc(facets.toString())).getAsJsonObject();
        List<Project> hits = new ArrayList<>();
        for (JsonElement e : array(root, "hits")) {
            if (!e.isJsonObject()) continue;
            JsonObject h = e.getAsJsonObject();
            Project p = new Project();
            p.id = str(h, "project_id");
            p.slug = str(h, "slug");
            p.title = Text.clean(str(h, "title"));
            p.summary = Text.clean(str(h, "description"));
            p.author = Text.clean(str(h, "author"));
            p.iconUrl = url(h, "icon_url");
            p.banner = url(h, "featured_gallery");
            if (p.banner == null) {
                JsonArray gallery = array(h, "gallery");
                if (!gallery.isEmpty() && gallery.get(0).isJsonPrimitive()) p.banner = safe(gallery.get(0).getAsString());
            }
            p.categories = strings(h, "display_categories");
            if (p.categories.isEmpty()) p.categories = strings(h, "categories");
            p.downloads = num(h, "downloads");
            p.follows = num(h, "follows");
            p.updated = time(h, "date_modified");
            p.created = time(h, "date_created");
            p.license = str(h, "license");
            p.color = (int) num(h, "color");
            if (!p.id.isEmpty()) hits.add(p);
        }
        return new Page(hits, (int) num(root, "total_hits"));
    }

    /** Fills in the rest of a project's page. Safe to call with a project that only has its id. */
    public static void project(Project p) throws IOException {
        JsonObject o = get("/project/" + enc(p.id)).getAsJsonObject();
        fill(p, o);
        if (p.author.isEmpty()) {
            try {
                for (JsonElement e : get("/project/" + enc(p.id) + "/members").getAsJsonArray()) {
                    JsonObject member = e.getAsJsonObject();
                    if (!member.has("user") || !member.get("user").isJsonObject()) continue;
                    String name = Text.clean(str(member.getAsJsonObject("user"), "username"));
                    if (p.author.isEmpty() || str(member, "role").equalsIgnoreCase("owner")) p.author = name;
                }
            } catch (IOException | RuntimeException ignored) {
                // a page without an author line is still a page
            }
        }
    }

    private static void fill(Project p, JsonObject o) {
        p.id = str(o, "id");
        p.slug = str(o, "slug");
        p.title = Text.clean(str(o, "title"));
        p.summary = Text.clean(str(o, "description"));
        p.body = str(o, "body");
        p.iconUrl = url(o, "icon_url");
        p.downloads = num(o, "downloads");
        p.follows = num(o, "followers");
        p.updated = time(o, "updated");
        p.created = time(o, "published");
        p.color = (int) num(o, "color");
        p.categories = strings(o, "categories");
        p.gameVersions = strings(o, "game_versions");
        if (o.has("license") && o.get("license").isJsonObject()) {
            JsonObject license = o.getAsJsonObject("license");
            String name = str(license, "name");
            p.license = name.isEmpty() ? str(license, "id") : name;
        }
        if (p.license.startsWith("LicenseRef-")) p.license = p.license.substring(11).replace('-', ' ');

        List<Shot> gallery = new ArrayList<>();
        record Ordered(Shot shot, long order) {}
        List<Ordered> ordered = new ArrayList<>();
        for (JsonElement e : array(o, "gallery")) {
            if (!e.isJsonObject()) continue;
            JsonObject g = e.getAsJsonObject();
            String small = url(g, "url"), raw = url(g, "raw_url");
            if (small == null && raw == null) continue;
            Shot shot = new Shot(small != null ? small : raw, raw != null ? raw : small, Text.clean(str(g, "title")),
                    Text.clean(str(g, "description")), flag(g, "featured"));
            ordered.add(new Ordered(shot, num(g, "ordering")));
        }
        ordered.sort((a, b) -> a.shot.featured != b.shot.featured ? (a.shot.featured ? -1 : 1) : Long.compare(a.order, b.order));
        for (Ordered each : ordered) gallery.add(each.shot);
        p.gallery = gallery;
        if (p.banner == null && !gallery.isEmpty()) p.banner = gallery.get(0).url;

        List<Link> links = new ArrayList<>();
        link(links, "Source", url(o, "source_url"));
        link(links, "Issues", url(o, "issues_url"));
        link(links, "Wiki", url(o, "wiki_url"));
        link(links, "Discord", url(o, "discord_url"));
        for (JsonElement e : array(o, "donation_urls")) {
            if (e.isJsonObject()) link(links, Text.clean(str(e.getAsJsonObject(), "platform")), url(e.getAsJsonObject(), "url"));
        }
        p.links = links;
        p.full = true;
    }

    private static void link(List<Link> links, String label, String url) {
        if (url != null && !label.isBlank() && links.size() < 8) links.add(new Link(label, url));
    }

    /** Several projects' pages in one request, by id. */
    public static Map<String, Project> projects(Collection<String> ids) throws IOException {
        Map<String, Project> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        JsonArray list = new JsonArray();
        ids.forEach(list::add);
        for (JsonElement e : get("/projects?ids=" + enc(list.toString())).getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            Project p = new Project();
            fill(p, e.getAsJsonObject());
            // Summaries only: the list has no use for the page text.
            p.full = false;
            p.body = "";
            out.put(p.id, p);
        }
        return out;
    }

    /** A project's versions for this kind, newest first. */
    public static List<Version> versions(Kind kind, String projectId) throws IOException {
        JsonArray loaders = new JsonArray();
        kind.loaders.forEach(loaders::add);
        List<Version> out = new ArrayList<>();
        for (JsonElement e : get("/project/" + enc(projectId) + "/version?loaders=" + enc(loaders.toString())).getAsJsonArray()) {
            Version v = e.isJsonObject() ? version(e.getAsJsonObject()) : null;
            if (v != null && v.file != null) out.add(v);
        }
        return out;
    }

    /** The versions the given files (by SHA-1) belong to; files Modrinth does not know are left out. */
    public static Map<String, Version> identify(Collection<String> hashes) throws IOException {
        JsonObject body = new JsonObject();
        body.add("hashes", strings(hashes));
        body.addProperty("algorithm", "sha1");
        return versions(post("/version_files", body));
    }

    /** The newest version of each file's project that suits this game version, by the file's SHA-1. */
    public static Map<String, Version> updates(Kind kind, Collection<String> hashes, String gameVersion) throws IOException {
        JsonObject body = new JsonObject();
        body.add("hashes", strings(hashes));
        body.addProperty("algorithm", "sha1");
        body.add("loaders", strings(kind.loaders));
        body.add("game_versions", strings(List.of(gameVersion)));
        return versions(post("/version_files/update", body));
    }

    public static List<Category> categories(Kind kind) throws IOException {
        List<Category> out = new ArrayList<>();
        for (JsonElement e : get("/tag/category").getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject c = e.getAsJsonObject();
            if (str(c, "project_type").equals(kind.type) && !str(c, "name").isEmpty()) out.add(new Category(str(c, "name"), str(c, "header")));
        }
        return out;
    }

    // ---- reading -------------------------------------------------------------------------------

    private static Map<String, Version> versions(JsonElement root) {
        Map<String, Version> out = new LinkedHashMap<>();
        if (!root.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
            Version v = e.getValue().isJsonObject() ? version(e.getValue().getAsJsonObject()) : null;
            if (v != null) out.put(e.getKey(), v);
        }
        return out;
    }

    private static Version version(JsonObject o) {
        Version v = new Version();
        v.id = str(o, "id");
        v.projectId = str(o, "project_id");
        v.number = Text.clean(str(o, "version_number"));
        v.name = Text.clean(str(o, "name"));
        if (v.name.isEmpty()) v.name = v.number;
        v.changelog = str(o, "changelog");
        v.type = str(o, "version_type");
        v.published = time(o, "date_published");
        v.downloads = num(o, "downloads");
        v.gameVersions = strings(o, "game_versions");
        JsonObject chosen = null;
        for (JsonElement e : array(o, "files")) {
            if (!e.isJsonObject()) continue;
            JsonObject f = e.getAsJsonObject();
            if (chosen == null || flag(f, "primary")) chosen = f;
        }
        if (chosen != null) {
            JsonObject hashes = chosen.has("hashes") && chosen.get("hashes").isJsonObject() ? chosen.getAsJsonObject("hashes") : new JsonObject();
            String url = url(chosen, "url"), name = str(chosen, "filename");
            if (url != null && !name.isEmpty()) v.file = new FileRef(url, name, num(chosen, "size"), str(hashes, "sha1"), str(hashes, "sha512"));
        }
        return v.id.isEmpty() ? null : v;
    }

    private static JsonElement get(String path) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(API + path)).GET());
    }

    private static JsonElement post(String path, JsonObject body) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(API + path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
    }

    private static JsonElement send(HttpRequest.Builder request) throws IOException {
        try {
            HttpResponse<String> response = HTTP.send(request.timeout(Duration.ofSeconds(20)).header("User-Agent", AGENT).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) throw new IOException("Modrinth is asking for fewer requests; try again in a minute");
            if (response.statusCode() != 200) throw new IOException("Modrinth answered " + response.statusCode());
            return JsonParser.parseString(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } catch (RuntimeException e) {
            throw new IOException("Modrinth sent something unreadable", e);
        }
    }

    private static JsonArray group(String facet) {
        JsonArray one = new JsonArray();
        one.add(facet);
        return one;
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray out = new JsonArray();
        values.forEach(out::add);
        return out;
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static long num(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean flag(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : array(o, key)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    private static Instant time(JsonObject o, String key) {
        try {
            String text = str(o, key);
            return text.isEmpty() ? null : OffsetDateTime.parse(text).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String url(JsonObject o, String key) {
        return safe(str(o, key));
    }

    /** An address worth following: http or https, nothing else. */
    static String safe(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? trimmed : null;
    }
}
