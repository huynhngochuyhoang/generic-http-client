package io.github.huynhngochuyhoang.httpstarter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationReleaseArtifactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern PROJECT_VERSION_SNIPPET = Pattern.compile(
            "<groupId>io\\.github\\.huynhngochuyhoang</groupId>\\s*"
                    + "<artifactId>reactive-http-client-[^<]+</artifactId>\\s*"
                    + "<version>([^<]+)</version>",
            Pattern.DOTALL);

    @Test
    void localMarkdownLinksResolve() throws IOException {
        Path root = projectRoot();
        List<String> brokenLinks = new ArrayList<>();

        try (Stream<Path> files = Stream.concat(
                Stream.of(root.resolve("README.md")),
                Stream.concat(Files.walk(root.resolve("docs")), Files.walk(root.resolve("roadmaps"))))) {
            for (Path markdown : files.filter(path -> path.toString().endsWith(".md")).toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(markdown));
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (isExternalOrAnchor(target)) {
                        continue;
                    }
                    String pathOnly = target.split("#", 2)[0];
                    Path resolved = markdown.getParent()
                            .resolve(URLDecoder.decode(pathOnly, StandardCharsets.UTF_8))
                            .normalize();
                    if (!Files.exists(resolved)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target);
                    }
                }
            }
        }

        assertThat(brokenLinks).as("broken local Markdown links").isEmpty();
    }

    @Test
    void readmeAndQuickStartVersionsMatchProjectVersion() throws Exception {
        String projectVersion = projectVersion(projectRoot().resolve("pom.xml"));

        assertVersionSnippets(projectRoot().resolve("README.md"), projectVersion);
        assertVersionSnippets(projectRoot().resolve("docs/01-quick-start.md"), projectVersion);
    }

    @Test
    void generatedConfigurationReferenceMatchesMetadata() throws IOException {
        Path reference = projectRoot().resolve("docs/configuration-properties.md");

        assertThat(Files.readString(reference))
                .isEqualTo(configurationReferenceMarkdown(metadata()));
    }

    private static boolean isExternalOrAnchor(String target) {
        return target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:")
                || target.startsWith("#");
    }

    private static void assertVersionSnippets(Path markdown, String projectVersion) throws IOException {
        Matcher matcher = PROJECT_VERSION_SNIPPET.matcher(Files.readString(markdown));
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertThat(versions)
                .as("%s reactive-http-client dependency snippets", markdown.getFileName())
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static String projectVersion(Path pom) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(pom.toFile());
        return document.getElementsByTagName("version").item(0).getTextContent();
    }

    private static JsonNode metadata() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            assertThat(input).as("configuration metadata resource").isNotNull();
            return OBJECT_MAPPER.readTree(input);
        }
    }

    private static String configurationReferenceMarkdown(JsonNode metadata) throws IOException {
        StringWriter out = new StringWriter();
        out.append("# Configuration Properties\n\n");
        out.append("> Generated from `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`.\n");
        out.append("> `DocumentationReleaseArtifactTest` fails when this file drifts from metadata.\n\n");
        out.append("| Property | Type | Default | Description | Deprecated |\n");
        out.append("|---|---|---|---|---|\n");

        List<JsonNode> properties = new ArrayList<>();
        metadata.path("properties").forEach(properties::add);
        properties.sort(Comparator.comparing(property -> property.path("name").asText()));

        for (JsonNode property : properties) {
            out.append("| `").append(escapeCell(property.path("name").asText())).append("` ");
            out.append("| `").append(escapeCell(property.path("type").asText())).append("` ");
            out.append("| ").append(escapeCell(defaultValue(property))).append(" ");
            out.append("| ").append(escapeCell(property.path("description").asText())).append(" ");
            out.append("| ").append(escapeCell(deprecation(property))).append(" |\n");
        }
        return out.toString();
    }

    private static String defaultValue(JsonNode property) throws IOException {
        if (!property.has("defaultValue")) {
            return "";
        }
        return "`" + OBJECT_MAPPER.writeValueAsString(property.get("defaultValue")) + "`";
    }

    private static String deprecation(JsonNode property) {
        JsonNode deprecation = property.path("deprecation");
        if (deprecation.isMissingNode()) {
            return "";
        }
        String replacement = deprecation.path("replacement").asText("");
        if (replacement.isBlank()) {
            return deprecation.path("level").asText("warning");
        }
        return deprecation.path("level").asText("warning") + "; replacement: `" + replacement + "`";
    }

    private static String escapeCell(String value) {
        return value
                .replace("\r", "")
                .replace("\n", " ")
                .replace("|", "\\|");
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("README.md")) && Files.isDirectory(cwd.resolve("docs"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
