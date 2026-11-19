package az.tapstamp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

import az.tapstamp.plan.PlanService;
import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestTenants;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Menyu və xidmətlər kataloqu (əlavə CATALOG): əlavə olmadan bağlıdır; kateqoriya və məhsulların idarəsi, sıralama,
 * şəkil, gizlətmə, filiala görə süzgəc və ictimai səhifə. Hər test öz kateqoriyalarını yaradır və sonda silir.
 */
class CatalogIT extends TapIntegrationTest {

    @Autowired
    @Qualifier("platformJdbc")
    private JdbcClient platformJdbc;

    @Autowired
    private PlanService plans;

    @BeforeEach
    void enableAddon() {
        platformJdbc.sql("INSERT INTO tenant_addons (tenant_id, addon, enabled_at) VALUES (:id, 'CATALOG', now()) "
                + "ON CONFLICT DO NOTHING").param("id", alpha().id()).update();
        plans.evictCaches();
    }

    @AfterEach
    void cleanUp() {
        az.tapstamp.tenancy.TenantContext.runAs(alpha(), () -> {
            tenantJdbc.sql("DELETE FROM catalog_items").update();
            tenantJdbc.sql("DELETE FROM catalog_categories").update();
            tenantJdbc.sql("UPDATE catalog_settings SET enabled = true, title = 'MENU'").update();
        });
        platformJdbc.sql("DELETE FROM tenant_addons WHERE tenant_id = :id").param("id", alpha().id()).update();
        plans.evictCaches();
    }

    private AdminClient owner() throws Exception {
        return signedInStaff(HOST, AdminRole.COMPANY_OWNER, null);
    }

    private static Map<String, Object> item(String categoryId, String name, Object price) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", Map.of("az", name, "en", name + " EN"));
        body.put("price", price);
        return body;
    }

    private static String idOf(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions publicCatalog(String query) throws Exception {
        return client().get("/api/v1/public/catalog" + query).andExpect(status().isOk());
    }

    @Test
    void ownerBuildsMenuAndCustomersSeeOnlyVisibleItems() throws Exception {
        AdminClient owner = owner();
        String drinks = idOf(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "İçkilər")))
                .andExpect(status().isCreated())), "$.categories[0].id");
        String desserts = idOf(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "Desertlər")))),
                "$.categories[1].id");
        String hidden = idOf(body(owner.post("/catalog/categories",
                Map.of("name", Map.of("az", "Gizli"), "active", false))), "$.categories[2].id");

        owner.post("/catalog/items", item(drinks, "Latte", 4.5)).andExpect(status().isCreated());
        owner.post("/catalog/items", item(drinks, "Kapuçino", 4)).andExpect(status().isCreated());
        Map<String, Object> soldOut = item(desserts, "Çizkeyk", "6.90");
        soldOut.put("available", false);
        soldOut.put("description", Map.of("az", "Ev üsulu"));
        owner.post("/catalog/items", soldOut).andExpect(status().isCreated());
        owner.post("/catalog/items", item(hidden, "Görünməz", 1)).andExpect(status().isCreated());

        // Admin hamısını görür
        String all = body(owner.get("/catalog").andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.title").value("MENU"))
                .andExpect(jsonPath("$.categories.length()").value(3)));
        List<String> drinkItems = JsonPath.read(all, "$.categories[0].items[*].id");
        assertThat(drinkItems).hasSize(2);

        // Müştəri: gizli kateqoriya yoxdur, "hazırda yoxdur" göstərilir, qiymət iki rəqəmlə
        publicCatalog("").andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].items[0].name.az").value("Latte"))
                .andExpect(jsonPath("$.categories[0].items[0].price").value(4.5))
                .andExpect(jsonPath("$.categories[1].items[0].available").value(false))
                .andExpect(jsonPath("$.categories[1].items[0].description.az").value("Ev üsulu"));

        // Sıralama: kateqoriyalar və kateqoriyadakı məhsullar
        owner.put("/catalog/categories/order", Map.of("ids", List.of(desserts, drinks, hidden)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].id").value(desserts));
        owner.put("/catalog/categories/" + drinks + "/order", Map.of("ids", List.of(drinkItems.get(1), drinkItems.get(0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[1].items[0].name.az").value("Kapuçino"));
        // Natamam siyahı qəbul olunmur
        owner.put("/catalog/categories/order", Map.of("ids", List.of(drinks))).andExpect(status().isBadRequest());

        // İçində məhsul olan kateqoriya silinmir; məhsulu köçürəndə sona düşür
        owner.delete("/catalog/categories/" + hidden).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CATEGORY_NOT_EMPTY"));
        String invisible = JsonPath.<List<String>>read(body(owner.get("/catalog")), "$.categories[2].items[*].id").get(0);
        owner.put("/catalog/items/" + invisible, item(drinks, "Artıq görünür", 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[1].items[2].name.az").value("Artıq görünür"));
        owner.delete("/catalog/categories/" + hidden).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2));

        // Bölmənin adı və söndürülməsi
        owner.put("/catalog/settings", Map.of("enabled", true, "title", "SERVICES")).andExpect(status().isOk());
        publicCatalog("").andExpect(jsonPath("$.title").value("SERVICES"));
        owner.put("/catalog/settings", Map.of("enabled", true, "title", "OTHER")).andExpect(status().isBadRequest());
        owner.put("/catalog/settings", Map.of("enabled", false, "title", "MENU")).andExpect(status().isOk());
        publicCatalog("").andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.categories").isEmpty());

        // Yoxlamalar: mənfi qiymət, 3 onluq, naməlum kateqoriya
        owner.post("/catalog/items", item(drinks, "Səhv", -1)).andExpect(status().isBadRequest());
        owner.post("/catalog/items", item(drinks, "Səhv", "1.234")).andExpect(status().isBadRequest());
        owner.post("/catalog/items", item(UUID.randomUUID().toString(), "Səhv", 1)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action LIKE 'CATALOG_%'", Map.of())).isGreaterThanOrEqualTo(10);
    }

    @Test
    void itemsCanBeLimitedToOneBranch() throws Exception {
        AdminClient owner = owner();
        UUID branch = newBranch(Policy.relaxed());
        UUID other = newBranch(Policy.relaxed());
        String category = idOf(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "Xidmətlər")))),
                "$.categories[0].id");
        owner.post("/catalog/items", item(category, "Hamısında", 10)).andExpect(status().isCreated());
        Map<String, Object> local = item(category, "Yalnız bir filialda", 20);
        local.put("branchId", branch.toString());
        local.put("durationMinutes", 30);
        owner.post("/catalog/items", local).andExpect(status().isCreated());
        Map<String, Object> unknown = item(category, "Səhv filial", 5);
        unknown.put("branchId", UUID.randomUUID().toString());
        owner.post("/catalog/items", unknown).andExpect(status().isBadRequest());

        publicCatalog("").andExpect(jsonPath("$.categories[0].items.length()").value(2));
        publicCatalog("?branchId=" + branch).andExpect(jsonPath("$.categories[0].items.length()").value(2))
                .andExpect(jsonPath("$.categories[0].items[1].durationMinutes").value(30));
        publicCatalog("?branchId=" + other).andExpect(jsonPath("$.categories[0].items.length()").value(1));
    }

    @Test
    void itemImageIsNormalizedAndServedWithVersionedUrl() throws Exception {
        AdminClient owner = owner();
        String category = idOf(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "Şəkilli")))),
                "$.categories[0].id");
        String itemId = idOf(body(owner.post("/catalog/items", item(category, "Burger", 9))), "$.categories[0].items[0].id");
        String imagePath = "/api/v1/admin/catalog/items/" + itemId + "/image";

        upload(owner, imagePath, image(1200, 900, "jpeg")).andExpect(status().isOk());
        String imageUrl = idOf(body(publicCatalog("")), "$.categories[0].items[0].imageUrl");
        assertThat(imageUrl).startsWith("/api/v1/public/catalog/items/" + itemId + "/image?v=");
        byte[] jpeg = client().get(imageUrl).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(stored.getWidth()).isEqualTo(640);
        assertThat(stored.getHeight()).isEqualTo(640);

        // Siyahı üçün kiçik variant: 192 px JPEG, əsas şəkildən xeyli yüngül
        String thumbUrl = idOf(body(publicCatalog("")), "$.categories[0].items[0].thumbUrl");
        assertThat(thumbUrl).startsWith("/api/v1/public/catalog/items/" + itemId + "/thumb?v=");
        byte[] thumb = client().get(thumbUrl).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(thumb)).getWidth()).isEqualTo(192);
        assertThat(thumb.length).isLessThan(jpeg.length);

        // Yenidən yükləmə — yeni versiya; uyğun olmayanlar
        upload(owner, imagePath, image(300, 300, "png")).andExpect(status().isOk());
        assertThat(idOf(body(publicCatalog("")), "$.categories[0].items[0].imageUrl")).isNotEqualTo(imageUrl);
        upload(owner, imagePath, image(150, 150, "png")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_IMAGE_INVALID"));
        upload(owner, imagePath, "<svg/>".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());

        owner.delete("/catalog/items/" + itemId + "/image").andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].items[0].imageUrl").doesNotExist());
        client().get("/api/v1/public/catalog/items/" + itemId + "/image").andExpect(status().isNotFound());
        client().get("/api/v1/public/catalog/items/" + itemId + "/thumb").andExpect(status().isNotFound());
    }

    @Test
    void thumbnailOfImageUploadedBeforeThumbnailsExistedIsCreatedOnFirstRequest() throws Exception {
        AdminClient owner = owner();
        String category = idOf(body(owner.post("/catalog/categories", Map.of("name", Map.of("az", "Köhnə")))),
                "$.categories[0].id");
        String itemId = idOf(body(owner.post("/catalog/items", item(category, "Plov", 12))), "$.categories[0].items[0].id");
        // Köhnə vəziyyət: əsas şəkil PNG, kiçik variant yoxdur
        byte[] legacyPng = image(640, 640, "png");
        az.tapstamp.tenancy.TenantContext.runAs(alpha(), () -> tenantJdbc.sql(
                        "UPDATE catalog_items SET image = :png, image_thumb = NULL, image_version = 1 WHERE id = :id")
                .param("png", legacyPng).param("id", UUID.fromString(itemId)).update());

        client().get("/api/v1/public/catalog/items/" + itemId + "/image").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        byte[] thumb = client().get("/api/v1/public/catalog/items/" + itemId + "/thumb").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(thumb)).getWidth()).isEqualTo(192);
        Integer saved = az.tapstamp.tenancy.TenantContext.callAs(alpha(), () -> tenantJdbc.sql(
                        "SELECT count(*) FROM catalog_items WHERE id = :id AND image_thumb IS NOT NULL")
                .param("id", UUID.fromString(itemId)).query(Integer.class).single());
        assertThat(saved).isEqualTo(1);
    }

    @Test
    void platformPrintCardOffersTheMenuQrOnlyWhileTheMenuIsOpen() throws Exception {
        AdminClient platform = signedInPlatformAdmin();
        String path = "/tenants/" + alpha().id() + "/review-cards";
        // Əlavə açıq, bölmə aktiv (cleanUp-da MENU) — menyunun linki şirkətin domenindədir
        platform.get(path).andExpect(status().isOk())
                .andExpect(jsonPath("$.menuTitle").value("MENU"))
                .andExpect(jsonPath("$.menuUrl").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(HOST), org.hamcrest.Matchers.endsWith("/menu"))));
        // Bölmə söndürülüb — ikinci QR təklif olunmur
        az.tapstamp.tenancy.TenantContext.runAs(alpha(), () ->
                tenantJdbc.sql("UPDATE catalog_settings SET enabled = false").update());
        platform.get(path).andExpect(jsonPath("$.menuUrl").doesNotExist());
    }

    @Test
    void menuLinkPreviewHasOpenGraphTagsInTheCompanyLanguage() throws Exception {
        AdminClient owner = owner();
        owner.post("/catalog/categories", Map.of("name", Map.of("az", "Önizləmə"))).andExpect(status().isCreated());
        owner.put("/catalog/settings", Map.of("enabled", true, "title", "SERVICES")).andExpect(status().isOk());
        String html = client().get("/api/v1/public/share/menu").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/html")))
                .andExpect(header().stringValues("Vary", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("User-Agent"))))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<meta property=\"og:title\" content=\"")
                .contains(" · Xidmətlər\">")
                .contains("xidmətləri və qiymətləri ilə tanış olun.")
                .contains("<meta property=\"og:url\" content=\"http://" + HOST + "/menu\">")
                .contains("/api/v1/public/icons/512.png")
                // Yönləndirmə "/menu/"-ya: nginx-in bot qaydası (dəqiq "/menu") təkrarlanmır — dövr yoxdur
                .contains("http-equiv=\"refresh\" content=\"0; url=http://" + HOST + "/menu/\"")
                // Şirkət adı HTML-də qaçırılır
                .doesNotContain("<script");

        // Menyu söndürülübsə — önizləmə yoxdur
        owner.put("/catalog/settings", Map.of("enabled", false, "title", "MENU")).andExpect(status().isOk());
        client().get("/api/v1/public/share/menu").andExpect(status().isNotFound());
    }

    @Test
    void catalogIsClosedWithoutTheAddonAndOnlyForOwners() throws Exception {
        platformJdbc.sql("DELETE FROM tenant_addons WHERE tenant_id = :id").param("id", alpha().id()).update();
        plans.evictCaches();
        owner().get("/catalog").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_FEATURE_UNAVAILABLE"));
        publicCatalog("").andExpect(jsonPath("$.enabled").value(false));

        enableAddon();
        UUID branch = newBranch(Policy.relaxed());
        signedInStaff(HOST, AdminRole.BRANCH_MANAGER, branch).get("/catalog").andExpect(status().isForbidden());
        // Başqa şirkətin kataloqu ayrıdır
        owner().post("/catalog/categories", Map.of("name", Map.of("az", "Yalnız alfa"))).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/public/catalog").with(r -> {
            r.setServerName(TestTenants.BETA_HOST);
            return r;
        })).andExpect(status().isOk()).andExpect(jsonPath("$.categories").isEmpty());
    }

    private ResultActions upload(AdminClient owner, String path, byte[] content) throws Exception {
        return owner.perform(multipart(HttpMethod.PUT, path)
                .file(new MockMultipartFile("file", "photo", "application/octet-stream", content)));
    }

    private static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
