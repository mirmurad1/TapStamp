package az.tapstamp.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Bütün inteqrasiya testləri üçün bir PostgreSQL 16 konteyneri (JVM-də bir dəfə başladılır).
 * Konteyner production-dakı eyni init skripti ilə qalxır — DB rolları və hüquqları eynidir.
 */
public final class TestDatabase {

    public static final String PLATFORM_OWNER_PASSWORD = "test-platform-owner-password-0001";
    public static final String PLATFORM_APP_PASSWORD = "test-platform-app-password-00001";
    public static final String PROVISIONER_PASSWORD = "test-provisioner-password-000001";

    private static PostgreSQLContainer container;

    private TestDatabase() {
    }

    public static synchronized PostgreSQLContainer start() {
        if (container == null) {
            container = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withEnv("DB_PLATFORM_OWNER_PASSWORD", PLATFORM_OWNER_PASSWORD)
                    .withEnv("DB_PLATFORM_APP_PASSWORD", PLATFORM_APP_PASSWORD)
                    .withEnv("DB_PROVISIONER_PASSWORD", PROVISIONER_PASSWORD)
                    .withCopyFileToContainer(MountableFile.forHostPath(initScript(), 0755),
                            "/docker-entrypoint-initdb.d/01-platform.sh");
            container.start();
        }
        return container;
    }

    public static void register(DynamicPropertyRegistry registry) {
        PostgreSQLContainer postgres = start();
        registry.add("app.tenancy.db-host", postgres::getHost);
        registry.add("app.tenancy.db-port", () -> postgres.getMappedPort(5432));
        registry.add("app.tenancy.platform.app-password", () -> PLATFORM_APP_PASSWORD);
        registry.add("app.tenancy.platform.owner-password", () -> PLATFORM_OWNER_PASSWORD);
        registry.add("app.tenancy.provisioner.password", () -> PROVISIONER_PASSWORD);
        registry.add("app.crypto.keks", () -> "test-kek:" + key(1));
        registry.add("app.crypto.active-kek-id", () -> "test-kek");
        registry.add("app.crypto.phone-hmac-key", () -> key(2));
        registry.add("app.crypto.otp-pepper", () -> key(3));
        registry.add("app.crypto.redemption-code-key", () -> key(4));
    }

    /** Superuser bağlantısı (yalnız testlərdə DB səviyyəsindəki hüquqları yoxlamaq üçün). */
    public static String jdbcUrl(String database) {
        PostgreSQLContainer postgres = start();
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + database;
    }

    private static String key(int seed) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (seed * 31 + i);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Path initScript() {
        for (Path candidate : new Path[] {
                Path.of("..", "infra", "postgres", "init", "01-platform.sh"),
                Path.of("infra", "postgres", "init", "01-platform.sh")}) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        throw new IllegalStateException("infra/postgres/init/01-platform.sh tapılmadı");
    }
}
