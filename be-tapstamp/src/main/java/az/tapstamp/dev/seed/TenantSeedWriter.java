package az.tapstamp.dev.seed;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import az.tapstamp.dev.seed.DemoDataGenerator.DemoCustomer;
import az.tapstamp.dev.seed.DemoDataGenerator.Visit;
import az.tapstamp.dev.seed.SeedCatalog.BranchSeed;
import az.tapstamp.dev.seed.SeedCatalog.TagSeed;
import az.tapstamp.dev.seed.SeedCatalog.TenantSeed;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.crypto.KeyPurpose;
import az.tapstamp.security.crypto.PhoneHasher;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.shared.pii.PhoneMasker;
import az.tapstamp.tenancy.TenantInfo;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Bir tenant-ın test məlumatlarını JDBC batch ilə yazır (yalnız dev). Tarixçə ardıcıl emal olunur:
 * hər ziyarət → möhür + toxunuş hadisələri; kart dolanda mükafat; mükafat sonrakı ziyarətdə istifadə olunur.
 */
@Profile("dev")
final class TenantSeedWriter {

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();
    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom KEY_RANDOM = new SecureRandom();

    private final NamedParameterJdbcTemplate jdbc;
    private final EnvelopeCipher cipher;
    private final PhoneHasher phoneHasher;
    private final TenantInfo tenant;
    private final Instant now;
    private final Random random;

    private final List<SqlParameterSource> customers = new ArrayList<>();
    private final List<SqlParameterSource> consents = new ArrayList<>();
    private final List<SqlParameterSource> rewards = new ArrayList<>();
    private final List<SqlParameterSource> stamps = new ArrayList<>();
    private final List<SqlParameterSource> redemptions = new ArrayList<>();
    private final List<SqlParameterSource> accounts = new ArrayList<>();
    private final List<SqlParameterSource> fraudFlags = new ArrayList<>();
    private final List<TapEvent> events = new ArrayList<>();

    TenantSeedWriter(NamedParameterJdbcTemplate jdbc, EnvelopeCipher cipher, PhoneHasher phoneHasher,
                     TenantInfo tenant, Instant now, long randomSeed) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.phoneHasher = phoneHasher;
        this.tenant = tenant;
        this.now = now;
        this.random = new Random(randomSeed);
    }

    record Summary(int customers, int stamps, int rewards, int redemptions, int tapEvents) {
    }

    /** Toxunuş hadisəsi; sayğac sonda, çip üzrə vaxt sırası ilə təyin olunur. */
    private static final class TapEvent {
        final UUID id = Ids.newId();
        final TagRow tag;
        final String stage;
        final String outcome;
        final String reason;
        final UUID customerId;
        final UUID stampId;
        final Instant at;
        Integer counter;
        Integer counterGap;

        TapEvent(TagRow tag, String stage, String outcome, String reason, UUID customerId, UUID stampId, Instant at) {
            this.tag = tag;
            this.stage = stage;
            this.outcome = outcome;
            this.reason = reason;
            this.customerId = customerId;
            this.stampId = stampId;
            this.at = at;
        }

        boolean acceptedVerify() {
            return "VERIFY".equals(stage) && "ACCEPTED".equals(outcome);
        }
    }

    private record TagRow(UUID id, UUID branchId) {
    }

    private static final class RewardRow {
        final UUID id = Ids.newId();
        final Instant earnedAt;
        Instant redeemedAt;
        UUID redeemedBranch;

        RewardRow(Instant earnedAt) {
            this.earnedAt = earnedAt;
        }
    }

    Summary write(TenantSeed seed) {
        updateProfile(seed);
        List<UUID> branchIds = new ArrayList<>();
        Map<UUID, List<TagRow>> tagsByBranch = new HashMap<>();
        for (BranchSeed branch : seed.branches()) {
            UUID branchId = insertBranch(branch);
            branchIds.add(branchId);
            List<TagRow> tags = new ArrayList<>();
            for (TagSeed tag : branch.tags()) {
                tags.add(insertTag(branchId, tag));
            }
            tagsByBranch.put(branchId, tags);
        }
        UUID ruleId = insertRule(seed.rule());
        UUID privacyId = insertLegalDocument("PRIVACY_POLICY", privacyText());
        UUID termsId = insertLegalDocument("TERMS", termsText());

        List<DemoCustomer> generated = DemoDataGenerator.generate(
                seed.randomSeed(), seed.customerCount(), branchIds.size(), now, tenant.timezone());
        for (DemoCustomer customer : generated) {
            processCustomer(customer, branchIds, tagsByBranch, ruleId, seed.rule(), privacyId, termsId);
        }
        addRejectedVerifications(tagsByBranch);
        List<TagRow> allTags = tagsByBranch.values().stream().flatMap(List::stream).toList();
        assignCounters(allTags);
        addFraudFlags();

        batch("""
                INSERT INTO customers (id, phone_enc, phone_hash, phone_masked, name, preferred_language, status,
                                       registered_at, last_visit_at, last_branch_id, created_at, updated_at)
                VALUES (:id, :phoneEnc, :phoneHash, :phoneMasked, :name, :language, 'ACTIVE',
                        :registeredAt, :lastVisitAt, :lastBranchId, :registeredAt, :now)""", customers);
        batch("""
                INSERT INTO consents (id, customer_id, document_id, document_type, document_version, language,
                                      accepted_at, ip)
                VALUES (:id, :customerId, :documentId, :documentType, 1, :language, :acceptedAt, CAST(:ip AS inet))""",
                consents);
        batch("""
                INSERT INTO rewards (id, customer_id, rule_id, stamps_required_snapshot, reward_name_snapshot, status,
                                     earned_at, redeemed_at, created_at)
                VALUES (:id, :customerId, :ruleId, :stampsRequired, CAST(:rewardName AS jsonb), :status,
                        :earnedAt, :redeemedAt, :earnedAt)""", rewards);
        batch("""
                INSERT INTO stamps (id, branch_id, customer_id, tag_id, source, status, local_date, confirmed_at,
                                    reward_id, created_at)
                VALUES (:id, :branchId, :customerId, :tagId, 'NFC_TAP', 'CONFIRMED', :localDate, :createdAt,
                        :rewardId, :createdAt)""", stamps);
        batch("""
                INSERT INTO redemptions (id, reward_id, customer_id, code_hash, qr_token_hash, status, expires_at,
                                         branch_id, confirmed_at, created_at)
                VALUES (:id, :rewardId, :customerId, :codeHash, :qrHash, 'COMPLETED', :expiresAt,
                        :branchId, :confirmedAt, :createdAt)""", redemptions);
        batch("""
                INSERT INTO loyalty_accounts (id, customer_id, current_stamps, total_stamps, rewards_earned,
                                              rewards_redeemed, last_stamp_at, created_at, updated_at)
                VALUES (:id, :customerId, :current, :total, :earned, :redeemed, :lastStampAt, :createdAt, :now)""",
                accounts);
        batch("""
                INSERT INTO tap_events (id, branch_id, tag_id, stage, outcome, reason, counter, counter_gap,
                                        customer_id, stamp_id, ip, browser_id_hash, user_agent_hash, created_at)
                VALUES (:id, :branchId, :tagId, :stage, :outcome, :reason, :counter, :counterGap,
                        :customerId, :stampId, CAST(:ip AS inet), :browserHash, :uaHash, :createdAt)""",
                events.stream().map(this::eventParams).toList());
        batch("""
                INSERT INTO fraud_flags (id, customer_id, tag_id, type, severity, details, created_at)
                VALUES (:id, :customerId, :tagId, :type, :severity, CAST(:details AS jsonb), :createdAt)""", fraudFlags);
        updateTagCounters(allTags);

        return new Summary(customers.size(), stamps.size(), rewards.size(), redemptions.size(), events.size());
    }

    private void processCustomer(DemoCustomer customer, List<UUID> branchIds, Map<UUID, List<TagRow>> tagsByBranch,
                                 UUID ruleId, SeedCatalog.RuleSeed rule, UUID privacyId, UUID termsId) {
        UUID customerId = Ids.newId();
        String aad = Aad.tenantField(tenant.id(), "customers", "phone_enc", customerId);
        Visit last = customer.lastVisit();
        customers.add(params()
                .addValue("id", customerId)
                .addValue("phoneEnc", cipher.encryptString(KeyPurpose.PII, customer.phoneE164(), aad))
                .addValue("phoneHash", phoneHasher.hash(tenant.id(), customer.phoneE164()))
                .addValue("phoneMasked", PhoneMasker.mask(customer.phoneE164()))
                .addValue("name", customer.name())
                .addValue("language", customer.language().tag())
                .addValue("registeredAt", ts(customer.registeredAt()))
                .addValue("lastVisitAt", ts(last.at()))
                .addValue("lastBranchId", branchIds.get(last.branchIndex())));
        for (var doc : List.of(new Object[] {privacyId, "PRIVACY_POLICY"}, new Object[] {termsId, "TERMS"})) {
            consents.add(params()
                    .addValue("id", Ids.newId())
                    .addValue("customerId", customerId)
                    .addValue("documentId", doc[0])
                    .addValue("documentType", doc[1])
                    .addValue("language", customer.language().tag())
                    .addValue("acceptedAt", ts(customer.registeredAt()))
                    .addValue("ip", randomIp()));
        }

        int current = 0;
        int earned = 0;
        int redeemed = 0;
        Deque<RewardRow> available = new ArrayDeque<>();
        List<RewardRow> customerRewards = new ArrayList<>();
        for (Visit visit : customer.visits()) {
            UUID branchId = branchIds.get(visit.branchIndex());
            List<TagRow> tags = tagsByBranch.get(branchId);
            TagRow tag = tags.get(random.nextInt(tags.size()));

            // Mövcud mükafat əksər hallarda növbəti ziyarətdə istifadə olunur
            if (!available.isEmpty() && random.nextDouble() < 0.75) {
                RewardRow reward = Objects.requireNonNull(available.poll());
                reward.redeemedAt = visit.at().minus(Duration.ofMinutes(2));
                reward.redeemedBranch = branchId;
                redeemed++;
            }

            UUID stampId = Ids.newId();
            UUID rewardId = null;
            current++;
            if (current == rule.stampsRequired()) {
                RewardRow reward = new RewardRow(visit.at());
                available.add(reward);
                customerRewards.add(reward);
                rewardId = reward.id;
                current = 0;
                earned++;
            }
            stamps.add(params()
                    .addValue("id", stampId)
                    .addValue("branchId", branchId)
                    .addValue("customerId", customerId)
                    .addValue("tagId", tag.id())
                    .addValue("localDate", visit.at().atZone(tenant.timezone()).toLocalDate())
                    .addValue("rewardId", rewardId)
                    .addValue("createdAt", ts(visit.at())));
            events.add(new TapEvent(tag, "VERIFY", "ACCEPTED", null, null, null, visit.at().minusSeconds(4)));
            events.add(new TapEvent(tag, "CLAIM", "ACCEPTED", null, customerId, stampId, visit.at()));

            // Bəzi müştərilər cooldown müddətində yenidən toxunur — rədd edilir
            if (random.nextDouble() < 0.025) {
                Instant retry = visit.at().plus(Duration.ofMinutes(5 + random.nextInt(10)));
                if (retry.isBefore(now)) {
                    events.add(new TapEvent(tag, "VERIFY", "ACCEPTED", null, null, null, retry.minusSeconds(4)));
                    events.add(new TapEvent(tag, "CLAIM", "REJECTED", "STAMP_COOLDOWN_ACTIVE", customerId, null, retry));
                }
            }
        }

        for (RewardRow reward : customerRewards) {
            rewards.add(params()
                    .addValue("id", reward.id)
                    .addValue("customerId", customerId)
                    .addValue("ruleId", ruleId)
                    .addValue("stampsRequired", rule.stampsRequired())
                    .addValue("rewardName", TEXT.toJson(rule.name()))
                    .addValue("status", reward.redeemedAt == null ? "AVAILABLE" : "REDEEMED")
                    .addValue("earnedAt", ts(reward.earnedAt))
                    .addValue("redeemedAt", reward.redeemedAt == null ? null : ts(reward.redeemedAt)));
            if (reward.redeemedAt != null) {
                redemptions.add(params()
                        .addValue("id", Ids.newId())
                        .addValue("rewardId", reward.id)
                        .addValue("customerId", customerId)
                        .addValue("codeHash", randomHash())
                        .addValue("qrHash", randomHash())
                        .addValue("expiresAt", ts(reward.redeemedAt.plus(Duration.ofMinutes(2))))
                        .addValue("branchId", reward.redeemedBranch)
                        .addValue("confirmedAt", ts(reward.redeemedAt.plusSeconds(40)))
                        .addValue("createdAt", ts(reward.redeemedAt)));
            }
        }

        accounts.add(params()
                .addValue("id", Ids.newId())
                .addValue("customerId", customerId)
                .addValue("current", current)
                .addValue("total", customer.visits().size())
                .addValue("earned", earned)
                .addValue("redeemed", redeemed)
                .addValue("lastStampAt", ts(last.at()))
                .addValue("createdAt", ts(customer.registeredAt())));
    }

    /** Köhnə URL-in təkrar istifadəsi və saxta imza cəhdləri (fırıldaq panelində görünsün deyə). */
    private void addRejectedVerifications(Map<UUID, List<TagRow>> tagsByBranch) {
        List<TagRow> tags = tagsByBranch.values().stream().flatMap(List::stream).toList();
        int replays = Math.max(2, customers.size() / 8);
        for (int i = 0; i < replays; i++) {
            Instant at = now.minus(Duration.ofMinutes(60L * 24 * (1 + random.nextInt(60)) + random.nextInt(600)));
            events.add(new TapEvent(tags.get(random.nextInt(tags.size())), "VERIFY", "REJECTED", "COUNTER_REPLAY",
                    null, null, at));
        }
        for (int i = 0; i < Math.max(1, replays / 2); i++) {
            Instant at = now.minus(Duration.ofMinutes(60L * 24 * (1 + random.nextInt(60)) + random.nextInt(600)));
            events.add(new TapEvent(tags.get(random.nextInt(tags.size())), "VERIFY", "REJECTED", "CMAC_INVALID",
                    null, null, at));
        }
    }

    /**
     * Sayğaclar hər çip üzrə vaxt sırası ilə artır. Bəzən boşluq olur (açılmamış oxunuşlar → COUNTER_GAP siqnalı);
     * təkrar istifadə cəhdləri artıq istifadə olunmuş sayğacı daşıyır.
     */
    private void assignCounters(List<TagRow> tags) {
        events.sort(Comparator.comparing(e -> e.at));
        Map<UUID, Integer> lastCounter = new HashMap<>();
        for (TagRow tag : tags) {
            lastCounter.put(tag.id(), 0);
        }
        for (TapEvent event : events) {
            if (event.tag == null || !"VERIFY".equals(event.stage)) {
                continue;
            }
            int last = lastCounter.get(event.tag.id());
            if (event.acceptedVerify()) {
                int gap = random.nextDouble() < 0.03 ? 1 + random.nextInt(3) : 0;
                event.counter = last + 1 + gap;
                event.counterGap = gap;
                lastCounter.put(event.tag.id(), event.counter);
            } else if ("COUNTER_REPLAY".equals(event.reason)) {
                event.counter = last <= 1 ? 1 : 1 + random.nextInt(last);
            }
        }
    }

    private void addFraudFlags() {
        events.stream().filter(e -> "COUNTER_REPLAY".equals(e.reason)).limit(2).forEach(e -> fraudFlags.add(flag(
                null, e.tag.id(), "REPLAY_ATTEMPT", "HIGH", "{\"counter\": " + e.counter + "}", e.at)));
        events.stream().filter(e -> e.counterGap != null && e.counterGap >= 2).findFirst().ifPresent(e -> fraudFlags.add(
                flag(null, e.tag.id(), "COUNTER_GAP", "LOW", "{\"gap\": " + e.counterGap + "}", e.at)));
        events.stream().filter(e -> "STAMP_COOLDOWN_ACTIVE".equals(e.reason)).findFirst().ifPresent(e -> fraudFlags.add(
                flag(e.customerId, e.tag.id(), "COOLDOWN_HAMMERING", "MEDIUM", "{\"attempts\": 1}", e.at)));
    }

    private MapSqlParameterSource flag(UUID customerId, UUID tagId, String type, String severity, String details,
                                       Instant at) {
        return params()
                .addValue("id", Ids.newId())
                .addValue("customerId", customerId)
                .addValue("tagId", tagId)
                .addValue("type", type)
                .addValue("severity", severity)
                .addValue("details", details)
                .addValue("createdAt", ts(at));
    }

    private SqlParameterSource eventParams(TapEvent event) {
        return params()
                .addValue("id", event.id)
                .addValue("branchId", event.tag == null ? null : event.tag.branchId())
                .addValue("tagId", event.tag == null ? null : event.tag.id())
                .addValue("stage", event.stage)
                .addValue("outcome", event.outcome)
                .addValue("reason", event.reason)
                .addValue("counter", event.counter)
                .addValue("counterGap", event.counterGap)
                .addValue("customerId", event.customerId)
                .addValue("stampId", event.stampId)
                .addValue("ip", randomIp())
                .addValue("browserHash", randomHash())
                .addValue("uaHash", randomHash())
                .addValue("createdAt", ts(event.at));
    }

    private void updateTagCounters(List<TagRow> tags) {
        for (TagRow tag : tags) {
            jdbc.update("""
                            UPDATE nfc_tags SET
                                last_counter = COALESCE((SELECT max(counter) FROM tap_events
                                                         WHERE tag_id = :tagId AND stage = 'VERIFY' AND outcome = 'ACCEPTED'), -1),
                                last_tap_at = (SELECT max(created_at) FROM tap_events WHERE tag_id = :tagId)
                            WHERE id = :tagId""",
                    params().addValue("tagId", tag.id()));
        }
    }

    private void updateProfile(TenantSeed seed) {
        jdbc.update("""
                        UPDATE company_profile SET description = CAST(:description AS jsonb), sms_sender_name = :sender,
                                                   brand_color = :brandColor,
                                                   updated_at = :now""",
                params()
                        .addValue("description", TEXT.toJson(seed.description()))
                        .addValue("sender", seed.smsSenderName())
                        .addValue("brandColor", seed.brandColor()));
    }

    private UUID insertBranch(BranchSeed branch) {
        UUID id = Ids.newId();
        jdbc.update("""
                        INSERT INTO branches (id, name, address, description, business_hours, status, created_at, updated_at)
                        VALUES (:id, CAST(:name AS jsonb), CAST(:address AS jsonb), CAST(:description AS jsonb),
                                CAST(:hours AS jsonb), 'ACTIVE', :createdAt, :now)""",
                params()
                        .addValue("id", id)
                        .addValue("name", TEXT.toJson(branch.name()))
                        .addValue("address", TEXT.toJson(branch.address()))
                        .addValue("description", TEXT.toJson(branch.description()))
                        .addValue("hours", branch.businessHours())
                        .addValue("createdAt", ts(now.minus(Duration.ofDays(DemoDataGenerator.HISTORY_DAYS + 30)))));
        return id;
    }

    private TagRow insertTag(UUID branchId, TagSeed tag) {
        UUID id = Ids.newId();
        jdbc.update("""
                        INSERT INTO nfc_tags (id, branch_id, uid, type, label, status, created_at, updated_at)
                        VALUES (:id, :branchId, :uid, :type, :label, 'ACTIVE', :createdAt, :now)""",
                params()
                        .addValue("id", id)
                        .addValue("branchId", branchId)
                        .addValue("uid", HEX.parseHex(tag.uidHex()))
                        .addValue("type", tag.type())
                        .addValue("label", tag.label())
                        .addValue("createdAt", ts(now.minus(Duration.ofDays(DemoDataGenerator.HISTORY_DAYS + 30)))));
        if ("NTAG424_SUN".equals(tag.type())) {
            insertTagKeys(id);
        }
        return new TagRow(id, branchId);
    }

    /** NTAG 424 DNA çipinin 3 AES-128 açarı (K0, K1, K2) — envelope encryption ilə. */
    private void insertTagKeys(UUID tagId) {
        UUID keyId = Ids.newId();
        MapSqlParameterSource params = params().addValue("id", keyId).addValue("tagId", tagId);
        for (String column : List.of("app_master_key_enc", "meta_read_key_enc", "file_read_key_enc")) {
            // Açar materialı həmişə kriptoqrafik təsadüfi (seed-in təkrarlanan Random-u ilə deyil)
            byte[] key = new byte[16];
            KEY_RANDOM.nextBytes(key);
            params.addValue(column, cipher.encrypt(KeyPurpose.NFC_KEYS, key,
                    Aad.tenantField(tenant.id(), "nfc_tag_keys", column, keyId)));
        }
        jdbc.update("""
                        INSERT INTO nfc_tag_keys (id, tag_id, key_version, status, app_master_key_enc, meta_read_key_enc,
                                                  file_read_key_enc, activated_at, created_at)
                        VALUES (:id, :tagId, 1, 'ACTIVE', :app_master_key_enc, :meta_read_key_enc, :file_read_key_enc,
                                :now, :now)""",
                params);
    }

    private UUID insertRule(SeedCatalog.RuleSeed rule) {
        UUID id = Ids.newId();
        jdbc.update("""
                        INSERT INTO reward_rules (id, rule_version, stamps_required, reward_name, reward_description,
                                                  active, created_at)
                        VALUES (:id, 1, :stamps, CAST(:name AS jsonb), CAST(:description AS jsonb), true, :createdAt)""",
                params()
                        .addValue("id", id)
                        .addValue("stamps", rule.stampsRequired())
                        .addValue("name", TEXT.toJson(rule.name()))
                        .addValue("description", TEXT.toJson(rule.description()))
                        .addValue("createdAt", ts(now.minus(Duration.ofDays(DemoDataGenerator.HISTORY_DAYS + 30)))));
        return id;
    }

    private UUID insertLegalDocument(String type, LocalizedText content) {
        UUID id = Ids.newId();
        jdbc.update("""
                        INSERT INTO legal_documents (id, type, doc_version, content, published_at, is_current)
                        VALUES (:id, :type, 1, CAST(:content AS jsonb), :publishedAt, true)""",
                params()
                        .addValue("id", id)
                        .addValue("type", type)
                        .addValue("content", TEXT.toJson(content))
                        .addValue("publishedAt", ts(now.minus(Duration.ofDays(DemoDataGenerator.HISTORY_DAYS + 30)))));
        return id;
    }

    private static LocalizedText privacyText() {
        return LocalizedText.of("""
                        # Məxfilik siyasəti

                        Biz yalnız loyallıq proqramı üçün lazım olan məlumatları toplayırıq: telefon nömrəniz, \
                        istəyə bağlı adınız və ziyarətlərinizin tarixçəsi. Telefon nömrəniz şifrələnmiş saxlanılır. \
                        Hesabınızı istənilən vaxt silə bilərsiniz.""",
                """
                        # Privacy policy

                        We only collect what the loyalty programme needs: your phone number, your name (optional) \
                        and your visit history. Your phone number is stored encrypted. You can delete your account \
                        at any time.""",
                """
                        # Политика конфиденциальности

                        Мы собираем только то, что нужно для программы лояльности: номер телефона, имя (по желанию) \
                        и историю посещений. Номер телефона хранится в зашифрованном виде. Вы можете удалить аккаунт \
                        в любой момент.""");
    }

    /**
     * {@code {{stampRules}}} və {@code {{rewardRule}}} — müştəri tətbiqi bunları şirkətin cari parametrləri ilə
     * (möhürlər arası vaxt, gündəlik limit, hədiyyə) seçilmiş dildə əvəz edir; sahib parametrləri dəyişəndə mətn də
     * dəyişir.
     */
    private static LocalizedText termsText() {
        return LocalizedText.of("""
                        # İstifadə şərtləri

                        Möhürlər yalnız şirkətimizdə alınmış məhsul və xidmətlər üçün verilir. {{stampRules}}

                        {{rewardRule}} Hədiyyə nağd pula dəyişdirilmir.""",
                """
                        # Terms of use

                        Stamps are given only for products and services bought from us. {{stampRules}}

                        {{rewardRule}} The reward cannot be exchanged for cash.""",
                """
                        # Условия использования

                        Штампы начисляются только за товары и услуги, приобретённые у нас. {{stampRules}}

                        {{rewardRule}} Подарок не обменивается на деньги.""");
    }

    private void batch(String sql, List<SqlParameterSource> rows) {
        if (!rows.isEmpty()) {
            jdbc.batchUpdate(sql, rows.toArray(SqlParameterSource[]::new));
        }
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource("now", ts(now));
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private String randomIp() {
        // TEST-NET-3 (RFC 5737): sənədləşmə üçün ayrılmış, real olmayan ünvanlar
        return "203.0.113." + (1 + random.nextInt(254));
    }

    /** SHA-256 ölçüsündə (32 bayt) təsadüfi dəyər. */
    private byte[] randomHash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytes;
    }
}
