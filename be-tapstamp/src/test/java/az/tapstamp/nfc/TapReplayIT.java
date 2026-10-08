package az.tapstamp.nfc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import az.tapstamp.support.AdminClient;
import az.tapstamp.support.CustomerClient;
import az.tapstamp.support.TapIntegrationTest;
import az.tapstamp.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** NFC toxunuşunun yoxlanılması: replay qorunması (paralel), imza xətaları, açar rotasiyası, önizləmə botları. */
class TapReplayIT extends TapIntegrationTest {

    @Test
    void sameSignedUrlFromTwentyParallelRequestsIsAcceptedExactlyOnce() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        String path = tapPath(tag, 1);
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                CustomerClient client = new CustomerClient(mvc, HOST, AdminClient.uniqueIp());
                Callable<String> attempt = () -> {
                    start.await();
                    return tap(client, path);
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            List<String> locations = new ArrayList<>();
            for (Future<String> result : results) {
                locations.add(result.get());
            }
            assertThat(locations.stream().filter(l -> !l.startsWith("/tap/error"))).hasSize(1);
            assertThat(locations.stream().filter(l -> l.equals("/tap/error?code=TAP_REPLAYED"))).hasSize(threads - 1);
        }

        Map<String, Object> byTag = Map.of("tagId", tag.id());
        assertThat(count("SELECT count(*) FROM tap_events WHERE tag_id = :tagId AND stage = 'VERIFY' AND outcome = 'ACCEPTED'",
                byTag)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tap_events WHERE tag_id = :tagId AND reason = 'COUNTER_REPLAY'", byTag))
                .isEqualTo(threads - 1);
        assertThat(count("SELECT count(*) FROM tap_tokens WHERE tag_id = :tagId", byTag)).isEqualTo(1);
        // Fırıldaq işarəsi bir dəfə (hücum zamanı cədvəl dolmur)
        assertThat(count("SELECT count(*) FROM fraud_flags WHERE tag_id = :tagId AND type = 'REPLAY_ATTEMPT'", byTag))
                .isEqualTo(1);
        assertThat(count("SELECT last_counter FROM nfc_tags WHERE id = :tagId", byTag)).isEqualTo(1);
    }

    @Test
    void olderCounterIsReplayAndCounterGapIsFlagged() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient client = client();

        tapToken(client, tag, 5);
        assertThat(tap(client, tapPath(tag, 5))).isEqualTo("/tap/error?code=TAP_REPLAYED");
        assertThat(tap(client, tapPath(tag, 3))).isEqualTo("/tap/error?code=TAP_REPLAYED");
        tapToken(client, tag, 6);
        // Server görmədiyi 50 oxunuş: əvvəlcədən yığılmış linklər ola bilər
        tapToken(client, tag, 57);

        Map<String, Object> byTag = Map.of("tagId", tag.id());
        assertThat(count("SELECT counter_gap FROM tap_events WHERE tag_id = :tagId AND counter = 57", byTag)).isEqualTo(50);
        assertThat(count("SELECT count(*) FROM fraud_flags WHERE tag_id = :tagId AND type = 'COUNTER_GAP'", byTag))
                .isEqualTo(1);
    }

    @Test
    void forgedSignatureIsRejectedWithGenericCodeAndDoesNotMoveCounter() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        TestTag other = newTag(tag.branchId());
        CustomerClient client = client();
        String valid = tapPath(tag, 10);
        String forgedCmac = valid.substring(0, valid.length() - 2) + (valid.endsWith("00") ? "01" : "00");
        // Başqa çipin açarları ilə imzalanmış link bu çipin UID-i ilə
        String foreignKeys = tapPath(new TestTag(tag.id(), tag.branchId(), tag.uid(), other.k1(), other.k2()), 11);

        assertThat(tap(client, forgedCmac)).isEqualTo("/tap/error?code=TAP_SIGNATURE_INVALID");
        assertThat(tap(client, foreignKeys)).isEqualTo("/tap/error?code=TAP_SIGNATURE_INVALID");

        Map<String, Object> byTag = Map.of("tagId", tag.id());
        assertThat(count("SELECT last_counter FROM nfc_tags WHERE id = :tagId", byTag)).isEqualTo(-1);
        assertThat(count("SELECT count(*) FROM tap_events WHERE tag_id = :tagId AND reason = 'CMAC_INVALID'", byTag))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM tap_events WHERE tag_id = :tagId AND reason = 'PICC_INVALID'", byTag))
                .isEqualTo(1);
        // Saxta sorğular həqiqi toxunuşu bloklamır
        tapToken(client, tag, 10);
    }

    @Test
    void malformedUnknownAndDisabledTags() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient client = client();

        assertThat(tap(client, "/t/" + tag.uidHex() + "?picc=XYZ&cmac=00")).isEqualTo("/tap/error?code=TAP_MALFORMED");
        assertThat(tap(client, "/t/" + tag.uidHex())).isEqualTo("/tap/error?code=TAP_MALFORMED");
        String unknown = tapPath(tag, 1).replace(tag.uidHex(), "04FFFFFFFFFFFF");
        assertThat(tap(client, unknown)).isEqualTo("/tap/error?code=TAG_UNKNOWN");

        TenantContext.runAs(alpha(), () -> tenantJdbc
                .sql("UPDATE nfc_tags SET status = 'DISABLED' WHERE id = :id").param("id", tag.id()).update());
        assertThat(tap(client, tapPath(tag, 1))).isEqualTo("/tap/error?code=TAG_DISABLED");
    }

    @Test
    void redirectHidesSignedParametersAndBindsBrowser() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        MvcResult result = mvc.perform(get(tapPath(tag, 1)).with(request -> {
                    request.setServerName(HOST);
                    request.setRemoteAddr(AdminClient.uniqueIp());
                    return request;
                }))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).doesNotContain("picc").doesNotContain("cmac");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).anySatisfy(cookie -> assertThat(cookie)
                .startsWith(BrowserIdCookie.NAME + "=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
                .contains("Path=/"));
    }

    @Test
    void headRequestsAndLinkPreviewBotsDoNotConsumeCounter() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        String path = tapPath(tag, 1);

        mvc.perform(head(path).with(request -> {
            request.setServerName(HOST);
            return request;
        })).andExpect(status().isSeeOther()).andExpect(header().string(HttpHeaders.LOCATION, "/"));
        mvc.perform(get(path).header(HttpHeaders.USER_AGENT, "WhatsApp/2.24.1 A").with(request -> {
            request.setServerName(HOST);
            return request;
        })).andExpect(status().isSeeOther()).andExpect(header().string(HttpHeaders.LOCATION, "/"));

        // Eyni link sonra həqiqi brauzerdə işləyir
        tapToken(client(), tag, 1);
    }

    @Test
    void firstTapWithPendingKeysCompletesRotation() throws Exception {
        TestTag tag = newTag(newBranch(Policy.relaxed()));
        CustomerClient client = client();
        tapToken(client, tag, 1);
        TestTag rotated = rotateKeys(tag);

        // Rotasiya tamamlanana qədər köhnə açar işləyir
        tapToken(client, tag, 2);
        tapToken(client, rotated, 3);

        Map<String, Object> byTag = Map.of("tagId", tag.id());
        assertThat(count("SELECT key_version FROM nfc_tag_keys WHERE tag_id = :tagId AND status = 'ACTIVE'", byTag))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM nfc_tag_keys WHERE tag_id = :tagId AND status = 'RETIRED'", byTag))
                .isEqualTo(1);
        // Köhnə açarla imzalanmış yeni sayğac artıq qəbul olunmur
        assertThat(tap(client, tapPath(tag, 4))).isEqualTo("/tap/error?code=TAP_SIGNATURE_INVALID");
    }
}
