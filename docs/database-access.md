# Bazaya giriş (prod)

Platforma admininin bazaya birbaşa baxması və lazım olanda dəyişiklik etməsi üçün. Gündəlik işlərin çoxunu admin panelindən edin — birbaşa SQL yalnız panelin imkan vermədiyi hallar üçündür.

Sənəddə heç bir şifrə yoxdur: şifrələr yalnız serverdəki `.env` faylındadır.

---

## Bilməli olduğunuz

- **Hər şirkətin ayrıca bazası var** (database-per-tenant):

  | Baza | Nə var |
  |---|---|
  | `loyalty_platform` | şirkətlər (`tenants`), domenlər (`tenant_domains`), platforma adminləri, platformanın audit jurnalı |
  | `loyalty_t_<slug>` (məs. `loyalty_t_hezzpoint`) | həmin şirkətin müştəriləri, möhürləri, hədiyyə kartları, işçiləri, NFC çipləri, audit jurnalı |

- **Bəzi sütunlar şifrələnib** — telefon nömrələri (`phone_enc`), NFC açarları, 2FA sirləri. Bazada oxunmaz bayt kimi görünür. **Onları əllə dəyişməyin**: şifrə pozular (məs. müştəri daxil ola bilməz). Tam telefon nömrələri admin panelində görünür; axtarış üçün `phone_hash` (HMAC) istifadə olunur, `phone_masked` isə yalnız göstəriş üçündür.
- **Birbaşa SQL audit jurnalına düşmür və biznes qaydalarından keçmir.** Məsələn, möhürü əllə silmək hədiyyə kartını ləğv etmir, sayğacları (`loyalty_accounts`) yeniləmir. Mümkündürsə — admin paneli.
- **Dəyişiklikdən əvvəl ehtiyat nüsxə:**
  ```bash
  cd /opt/tapstamp && ./scripts/backup.sh
  ```
- `postgres` istifadəçisi superuser-dir — hər şeyi silə bilər. Əvvəlcə `SELECT` ilə baxın, dəyişikliyi `BEGIN … COMMIT` içində edin.

---

## Yol 1 — serverdə `psql` (heç nə quraşdırmaq lazım deyil)

```bash
cd /opt/tapstamp && docker compose -f docker-compose.yml -f docker-compose.prod.yml exec postgres psql -U postgres
```

| Əmr | Nə edir |
|---|---|
| `\l` | bazaların siyahısı |
| `\c loyalty_t_hezzpoint` | şirkətin bazasına keçid |
| `\dt` | cədvəllər |
| `\d customers` | cədvəlin strukturu |
| `\x` | geniş sətirləri şaquli göstərmək (açıb-bağlamaq) |
| `\q` | çıxış |

Nümunə sorğular:

```sql
-- Platforma: şirkətlər və domenlər
\c loyalty_platform
SELECT slug, status, db_name FROM tenants ORDER BY created_at;
SELECT hostname, is_primary, tls_status FROM tenant_domains ORDER BY hostname;

-- Şirkət: son müştərilər və kartları
\c loyalty_t_hezzpoint
SELECT c.name, c.phone_masked, c.status, a.current_stamps, a.total_stamps, c.registered_at
FROM customers c LEFT JOIN loyalty_accounts a ON a.customer_id = c.id
ORDER BY c.registered_at DESC LIMIT 20;
```

**Dəyişiklikləri həmişə tranzaksiyada edin** — səhv olsa geri qaytarılır:

```sql
BEGIN;
UPDATE customers SET name = 'Aysel' WHERE id = '...';
-- nəticəyə baxın (SELECT); düzdürsə:
COMMIT;
-- səhvdirsə:
ROLLBACK;
```

---

## Yol 2 — kompüterdən qrafik proqramla (DBeaver, DataGrip, IntelliJ)

Baza internetə tamamilə bağlıdır (heç bir port açılmayıb). Müvəqqəti "körpü" açılır və ona **SSH tuneli** ilə qoşulursunuz — baza yenə internetə açılmır, körpü yalnız serverin daxilində (`127.0.0.1`) dinləyir.

### 1. Serverdə körpünü açın
```bash
docker run -d --rm --name pg-bridge -p 127.0.0.1:15432:5432 alpine/socat \
  tcp-listen:5432,fork,reuseaddr tcp-connect:postgres:5432 \
  && docker network connect tapstamp_data pg-bridge
```

### 2. Postgres şifrəsi (serverdə; heç yerə yazmayın)
```bash
grep '^POSTGRES_PASSWORD=' /opt/tapstamp/.env
```

### 3. DBeaver-də bağlantı (pulsuz)
**New Connection → PostgreSQL**

| Tab | Sahə | Dəyər |
|---|---|---|
| Main | Host | `localhost` |
| | Port | `15432` |
| | Database | `postgres` (+ "Show all databases") |
| | Username | `postgres` |
| | Password | 2-ci addımdakı şifrə |
| SSH | Use SSH Tunnel | ✔ |
| | Host / Port | `169.58.189.40` / `22` |
| | User | `root` |
| | Authentication | Public Key — `C:\Users\<siz>\.ssh\id_ed25519` |

Bütün bazalar və cədvəllər görünür; sorğuları işlətmək və cədvəldə redaktə etmək olar. DBeaver-də **Auto-commit**-i söndürün (alət panelindəki düymə) — onda dəyişikliklər yalnız "Commit" basanda yazılır.

### 4. İş bitəndə körpünü bağlayın
```bash
docker stop pg-bridge
```

SSH 22-ci portdan keçir: SSH-ı bloklayan şəbəkədən (məs. VPN) işləmir.

---

## Problemlər

| Simptom | Səbəb / həll |
|---|---|
| `pg-bridge` "name already in use" | körpü artıq açıqdır — istifadə edin və ya `docker stop pg-bridge` |
| `network tapstamp_data not found` | `docker network ls` ilə adını yoxlayın (layihə qovluğunun adından asılıdır) |
| DBeaver "Connection refused" | körpü açılmayıb və ya SSH tunel söndürülüb |
| `password authentication failed` | `.env`-dəki `POSTGRES_PASSWORD` ilə yoxlayın |
