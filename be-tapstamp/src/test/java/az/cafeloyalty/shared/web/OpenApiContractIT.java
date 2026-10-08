package az.cafeloyalty.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import az.cafeloyalty.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAPI müqaviləsi: hər əməliyyatın Azərbaycan dilində təsviri var və sənəd frontend-in tipli API klienti üçün
 * {@code fe-tapstamp/src/shared/api/openapi.json}-a yazılır ({@code npm run api:types} ondan TypeScript tipləri yaradır).
 * Sənəd dəyişibsə, fayl yenilənir və dəyişiklik git-də görünür — CI-da {@code git diff --exit-code} ilə yoxlanılır.
 */
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.writer-with-order-by-keys=true",
        "springdoc.writer-with-default-pretty-printer=true"})
class OpenApiContractIT extends IntegrationTest {

    private static final Path FRONTEND_SPEC = Path.of("..", "fe-tapstamp", "src", "shared", "api", "openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Autowired
    MockMvc mvc;

    @Test
    void everyOperationIsDocumentedAndSpecIsExportedForTheFrontend() throws Exception {
        String body = mvc.perform(get("/api/v1/openapi.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode spec = JSON.readTree(body);

        List<String> undocumented = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
            if (path.getKey().startsWith("/api/v1/public/test")) {
                continue;
            }
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                JsonNode summary = operation.getValue().get("summary");
                if (summary == null || summary.asString().isBlank()) {
                    undocumented.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey());
                }
            }
        }
        assertThat(undocumented).as("OpenAPI summary (Azərbaycan dilində) olmayan əməliyyatlar").isEmpty();
        assertThat(spec.get("paths").has("/api/v1/admin/auth/login")).isTrue();
        assertThat(spec.get("paths").has("/api/v1/platform/tenants")).isTrue();

        // Yalnız testlər üçün olan endpoint-lər və test domeninə bağlı server ünvanı frontend müqaviləsinə düşmür
        ((ObjectNode) spec).remove("servers");
        ObjectNode paths = (ObjectNode) spec.get("paths");
        List<String> testPaths = new ArrayList<>();
        paths.propertyNames().forEach(name -> {
            if (name.startsWith("/api/v1/public/test")) {
                testPaths.add(name);
            }
        });
        testPaths.forEach(paths::remove);
        ((ObjectNode) spec.get("components").get("schemas")).remove("EchoRequest");

        if (Files.isDirectory(FRONTEND_SPEC.getParent().getParent())) {
            Files.createDirectories(FRONTEND_SPEC.getParent());
            String exported = JSON.writeValueAsString(spec).replace("\r\n", "\n") + "\n";
            String current = Files.exists(FRONTEND_SPEC) ? Files.readString(FRONTEND_SPEC, StandardCharsets.UTF_8) : "";
            if (!exported.equals(current)) {
                Files.writeString(FRONTEND_SPEC, exported, StandardCharsets.UTF_8);
            }
        }
    }
}
