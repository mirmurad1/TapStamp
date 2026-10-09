package az.tapstamp.security.admin;

import java.util.Map;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.config.AppProperties;
import az.tapstamp.security.admin.repository.AdminAccountMapper;
import az.tapstamp.security.admin.repository.AdminSessionMapper;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.registry.TenantDirectory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Realm-ə görə infrastruktur: hansı bazanın, şifrələmə açarının, tranzaksiya menecerinin və audit jurnalının
 * işlədiləcəyi. Şirkət işçiləri üçün hər şey cari tenant-ın bazasındadır, platforma adminləri üçün platforma bazasında.
 */
@Component
public class AdminRealms {

    private final AdminAccountStore staffAccounts;
    private final AdminAccountStore platformAccounts;
    private final AdminSessionStore staffSessions;
    private final AdminSessionStore platformSessions;
    private final TransactionTemplate tenantTx;
    private final TransactionTemplate platformTx;
    private final EnvelopeCipher tenantCipher;
    private final EnvelopeCipher platformCipher;
    private final AuditLogger audit;
    private final TenantDirectory directory;
    private final AppProperties appProperties;
    private final AdminAuthProperties authProperties;

    /**
     * Hesab və sessiya mapper-ləri hər realm üçün öz bazasının {@link SqlSessionFactory}-si ilə yaradılır: eyni SQL,
     * fərqli cədvəllər ({@code AdminRealm.mapperVariables()}).
     */
    @SuppressWarnings("resource") // SqlSessionTemplate bağlanmır: sessiyaları Spring tranzaksiyası idarə edir
    public AdminRealms(@Qualifier("tenantSqlSessionFactory") SqlSessionFactory tenantSql,
                       @Qualifier("platformSqlSessionFactory") SqlSessionFactory platformSql,
                       PlatformTransactionManager transactionManager,
                       @Qualifier("platformTransactionManager") PlatformTransactionManager platformTransactionManager,
                       @Qualifier("tenantCipher") EnvelopeCipher tenantCipher,
                       @Qualifier("platformCipher") EnvelopeCipher platformCipher,
                       AuditLogger audit, TenantDirectory directory, AppProperties appProperties,
                       AdminAuthProperties authProperties) {
        SqlSessionTemplate tenant = new SqlSessionTemplate(tenantSql);
        SqlSessionTemplate platform = new SqlSessionTemplate(platformSql);
        this.staffAccounts = new AdminAccountStore(AdminRealm.STAFF, tenant.getMapper(AdminAccountMapper.class));
        this.platformAccounts = new AdminAccountStore(AdminRealm.PLATFORM, platform.getMapper(AdminAccountMapper.class));
        this.staffSessions = new AdminSessionStore(tenant.getMapper(AdminSessionMapper.class));
        this.platformSessions = new AdminSessionStore(platform.getMapper(AdminSessionMapper.class));
        this.tenantTx = new TransactionTemplate(transactionManager);
        this.platformTx = new TransactionTemplate(platformTransactionManager);
        this.tenantCipher = tenantCipher;
        this.platformCipher = platformCipher;
        this.audit = audit;
        this.directory = directory;
        this.appProperties = appProperties;
        this.authProperties = authProperties;
    }

    public AdminAccountStore accounts(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? staffAccounts : platformAccounts;
    }

    public AdminSessionStore sessions(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? staffSessions : platformSessions;
    }

    public TransactionTemplate tx(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? tenantTx : platformTx;
    }

    public EnvelopeCipher cipher(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? tenantCipher : platformCipher;
    }

    /** TOTP sirrinin AAD-i: şifrəli sirri başqa istifadəçiyə və ya başqa tenant-a köçürmək mümkün deyil. */
    public String totpAad(AdminRealm realm, UUID userId) {
        return realm == AdminRealm.STAFF
                ? Aad.tenantField(TenantContext.require().id(), "staff_users", "totp_secret_enc", userId)
                : Aad.platformField("platform_admins", "totp_secret_enc", userId);
    }

    /** Rate limit açarlarının əhatəsi: hər tenant öz limitlərinə malikdir. */
    public String rateLimitScope(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? "t:" + TenantContext.require().id() : "platform";
    }

    public void audit(AdminRealm realm, String action, UUID actorId, String targetType, Object targetId,
                      Map<String, ?> details) {
        if (realm == AdminRealm.STAFF) {
            audit.tenant(action, AuditLogger.ActorType.STAFF, actorId, targetType, targetId, details);
        } else {
            audit.platform(action, actorId, targetType, targetId, details);
        }
    }

    /** Emaildəki linklərin bazası: tenant üçün reyestrdəki əsas domen (sorğunun Host-u yox), platforma üçün öz domeni. */
    public String baseUrl(AdminRealm realm) {
        String host = realm == AdminRealm.STAFF
                ? directory.primaryHost(TenantContext.require().id()).orElseThrow()
                : appProperties.platformDomain();
        int port = authProperties.linksHttpsPort();
        return "https://" + host + (port == 443 ? "" : ":" + port);
    }

    /** Email və sertifikat etiketində göstərilən ad (TOTP tətbiqində də görünür). */
    public String issuer(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? "TapStamp · " + TenantContext.require().slug() : "TapStamp Platform";
    }

    public String tenantIdOrNull(AdminRealm realm) {
        return realm == AdminRealm.STAFF ? TenantContext.require().id().toString() : null;
    }
}
