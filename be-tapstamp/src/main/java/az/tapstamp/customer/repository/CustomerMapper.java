package az.tapstamp.customer.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.TenantMapper;
import org.jspecify.annotations.NonNull;

/**
 * Müştərilər (cari tenant-ın bazası). Telefon şifrəli ({@code phone_enc}), axtarış HMAC ilə ({@code phone_hash}).
 * SQL: {@code mapper/tenant/customer/CustomerMapper.xml}.
 */
@TenantMapper
public interface CustomerMapper {

    enum Status {
        ACTIVE,
        BLOCKED,
        DELETED
    }

    record Customer(UUID id, String name, byte[] phoneEnc, byte[] phoneHash, String phoneMasked, Language language,
                    Status status, Instant registeredAt) {

        @Override
        public @NonNull String toString() {
            return "Customer[id=" + id + ", status=" + status + "]";
        }
    }

    Optional<Customer> findById(UUID id);

    /** Silinmiş müştərinin hash-i NULL olduğu üçün o, nömrə ilə tapılmır. */
    Optional<Customer> findByPhoneHash(byte[] phoneHash);

    void insertCustomer(UUID id, byte[] phoneEnc, byte[] phoneHash, String phoneMasked, String name, Language language,
                        Instant now);

    void insertAccount(UUID id, UUID customerId, Instant now);

    /** Yeni müştəri və onun boş loyallıq hesabı (çağıranın tranzaksiyasında). */
    default void insert(UUID id, byte[] phoneEnc, byte[] phoneHash, String phoneMasked, String name, Language language,
                        Instant now) {
        insertCustomer(id, phoneEnc, phoneHash, phoneMasked, name, language, now);
        insertAccount(Ids.newId(), id, now);
    }

    /** {@code keepName} — ad dəyişmir; əks halda {@code name} yazılır (null — silinir). {@code language} null — dəyişmir. */
    void updateProfile(UUID id, boolean keepName, String name, Language language, Instant now);

    /** {@code name}: null — dəyişmir, boş sətir — silinir. */
    default void updateProfile(UUID id, String name, Language language, Instant now) {
        updateProfile(id, name == null, name == null || name.isBlank() ? null : name.strip(), language, now);
    }

    /** Maskası köhnə formatda qalmış müştərilər, id-yə görə {@code after}-dən sonra ({@code marker} — köhnə formatın parçası). */
    record LegacyMask(UUID id, byte[] phoneEnc) {

        @Override
        public @NonNull String toString() {
            return "LegacyMask[id=" + id + "]";
        }
    }

    List<LegacyMask> findLegacyMasks(String marker, UUID after, int limit);

    void updatePhoneMasked(UUID id, String phoneMasked);

    /** Şəxsi məlumat silinir; möhürlər və mükafatlar anonim ID ilə statistikada qalır. */
    void anonymize(UUID id, Instant now);
}
