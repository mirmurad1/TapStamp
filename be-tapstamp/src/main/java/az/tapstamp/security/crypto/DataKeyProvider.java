package az.tapstamp.security.crypto;

import java.util.UUID;

import javax.crypto.SecretKey;

import org.jspecify.annotations.NonNull;

/** Məlumat açarlarının (DEK) mənbəyi: platforma bazası və ya cari tenant-ın bazası. */
public interface DataKeyProvider {

    /** Yeni şifrələmə üçün məqsədə uyğun aktiv açar (yoxdursa yaradılır). */
    DataKey activeKey(KeyPurpose purpose);

    /** Şifrəli mətndə göstərilən açar (rotasiyadan sonra köhnə açarlar da açıla bilməlidir). */
    DataKey keyById(UUID id);

    record DataKey(UUID id, KeyPurpose purpose, SecretKey key) {
        @Override
        public @NonNull String toString() {
            return "DataKey[id=" + id + ", purpose=" + purpose + "]";
        }
    }
}
