package az.tapstamp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import az.tapstamp.security.admin.AdminRole;
import az.tapstamp.support.AdminClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Şirkətin loqosu: yükləmə (yoxlama və PNG-yə çevirmə), ictimai ünvan, silinmə. */
class CompanyLogoIT extends TapIntegrationTest {

    private static final String LOGO = "/api/v1/admin/settings/company/logo";

    @Test
    void ownerUploadsLogoAndCustomersSeeVersionedPng() throws Exception {
        String host = TestTenants.BETA_HOST;
        AdminClient owner = signedInStaff(host, AdminRole.COMPANY_OWNER, null);
        try {
            // Kvadrat olmayan foto: mərkəzdən kəsilir və 512 piksələ kiçildilir
            upload(owner, image(1200, 800, "jpeg")).andExpect(status().isNoContent());

            String logoUrl = publicCompany(host).andExpect(jsonPath("$.logoUrl").isString())
                    .andReturn().getResponse().getContentAsString().replaceAll("(?s).*\"logoUrl\":\"([^\"]+)\".*", "$1");
            assertThat(logoUrl).startsWith("/api/v1/public/company/logo?v=");
            byte[] png = mvc.perform(get(logoUrl).with(r -> {
                        r.setServerName(host);
                        return r;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/png"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                    .andReturn().getResponse().getContentAsByteArray();
            BufferedImage stored = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(stored.getWidth()).isEqualTo(512);
            assertThat(stored.getHeight()).isEqualTo(512);

            // Uyğun olmayanlar: qısa tərəf çox kiçik, şəkil deyil
            upload(owner, image(400, 150, "png")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("LOGO_INVALID"));
            upload(owner, image(64, 64, "png")).andExpect(status().isBadRequest());
            upload(owner, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()).andExpect(status().isBadRequest());

            owner.delete("/settings/company/logo").andExpect(status().isNoContent());
            publicCompany(host).andExpect(jsonPath("$.logoUrl").doesNotExist());
        } finally {
            owner.delete("/settings/company/logo");
        }
    }

    private ResultActions upload(AdminClient owner, byte[] content) throws Exception {
        return owner.perform(multipart(HttpMethod.PUT, LOGO)
                .file(new MockMultipartFile("file", "logo", "application/octet-stream", content)));
    }

    private ResultActions publicCompany(String host) throws Exception {
        return mvc.perform(get("/api/v1/public/company").with(r -> {
            r.setServerName(host);
            return r;
        })).andExpect(status().isOk());
    }

    private static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
