# Layihə qaydaları (Kafe Loyallıq)

## Dil
- İstifadəçi ilə ünsiyyət, planlar, mərhələ hesabatları: **Azərbaycan dili**.
- Sənədlər (README, `docs/`), kod şərhləri, OpenAPI `summary`/`description`: **Azərbaycan dili**. Texniki termin ilk dəfə işlənəndə mötərizədə qısa izah verilir.
- Kod identifikatorları, cədvəl/sütun/endpoint adları, commit və log mesajları: **ingiliscə**.
- UI mətnləri heç vaxt kodda sərt yazılmır: frontend `src/locales/{az,en,ru}/*.json`, backend `i18n/messages_{az,en,ru}.properties`. Hər yeni açar 3 dilə birdən əlavə olunur (paritet testləri yoxlayır).

## Arxitektura
- Əsas sənəd: `docs/architecture-plan.md` (qərarlar jurnalı §18). Plandan kənarlaşma olarsa, sənəd yenilənir.
- Hər şirkətin ayrıca bazası var (database-per-tenant); tenant yalnız `Host` başlığından müəyyən olunur.
- Backend: Spring Boot 4.1 / Java 21, package-by-feature (`az.cafeloyalty.<modul>`), controller-lər `api` və ya `web` paketində.
- `dev` paketi yalnız `@Profile("dev")`; başqa paketlər ona istinad edə bilməz (ArchUnit).
- Texniki sətirlərdə `toUpperCase/toLowerCase` yalnız `Locale.ROOT` ilə.

## Əmrlər
Bu kompüterdə standart `java` 1.8-dir; Maven üçün JDK 21 göstərilməlidir:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.9"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend; .\mvnw.cmd -B -ntp verify
```
Frontend (`frontend/`): `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, `npm run dev`.
Git Bash-də `mvnw` skripti `~/.m2`-yə yaza bilmir — Maven-i PowerShell-dən `mvnw.cmd` ilə işlədin.

## İş qaydası
- İş `docs/architecture-plan.md` §16-dakı mərhələlərlə aparılır; hər mərhələnin sonunda bütün testlər işə salınır və mərhələ ayrıca commit olunur.
- Docker olmadıqda Testcontainers testləri atlanır — bu, hesabatda açıq qeyd olunmalıdır.
