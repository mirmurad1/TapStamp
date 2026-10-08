package az.cafeloyalty.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import az.cafeloyalty.support.AdminIntegrationTest;
import az.cafeloyalty.support.TestTenants;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** PWA: hər şirkətin öz manifesti və brend rəngində ikonları; platforma domenində yoxdur. */
class PwaIT extends AdminIntegrationTest {

    private ResultActions on(String host, String path) throws Exception {
        return mvc.perform(get(path).with(r -> {
            r.setServerName(host);
            return r;
        }));
    }

    @Test
    void eachCompanyHasItsOwnManifestAndIcons() throws Exception {
        on(TestTenants.ALPHA_HOST, "/manifest.webmanifest")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.display").value("standalone"))
                .andExpect(jsonPath("$.start_url").value("/"))
                .andExpect(jsonPath("$.theme_color").value(org.hamcrest.Matchers.matchesPattern("^#[0-9A-Fa-f]{6}$")))
                .andExpect(jsonPath("$.icons[?(@.purpose == 'maskable')]").isNotEmpty());
        String alpha = on(TestTenants.ALPHA_HOST, "/manifest.webmanifest").andReturn().getResponse().getContentAsString();
        String beta = on(TestTenants.BETA_HOST, "/manifest.webmanifest").andReturn().getResponse().getContentAsString();
        assertThat(alpha).isNotEqualTo(beta);

        for (String name : new String[] {"180", "192", "512", "maskable-512"}) {
            byte[] png = on(TestTenants.ALPHA_HOST, "/api/v1/public/icons/" + name + ".png")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")))
                    .andReturn().getResponse().getContentAsByteArray();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            int expected = name.startsWith("maskable") ? 512 : Integer.parseInt(name);
            assertThat(image.getWidth()).isEqualTo(expected);
            assertThat(image.getHeight()).isEqualTo(expected);
        }
        on(TestTenants.ALPHA_HOST, "/api/v1/public/icons/64.png").andExpect(status().isNotFound());
        on(TestTenants.ALPHA_HOST, "/api/v1/public/icons/abc.png").andExpect(status().isNotFound());
        on("platform.localhost", "/manifest.webmanifest").andExpect(status().isNotFound());
    }
}
