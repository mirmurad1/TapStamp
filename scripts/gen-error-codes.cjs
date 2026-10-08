// docs/error-codes.md faylını ErrorCode.java və backend mesajlarından (az) yaradır.
// İstifadə: node scripts/gen-error-codes.cjs   (yeni xəta kodu əlavə ediləndən sonra)
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const java = fs.readFileSync(path.join(root, 'be-tapstamp/src/main/java/az/cafeloyalty/shared/error/ErrorCode.java'), 'utf8');
const props = fs.readFileSync(path.join(root, 'be-tapstamp/src/main/resources/i18n/messages_az.properties'), 'utf8');

const messages = {};
for (const line of props.split(/\r?\n/)) {
  const match = /^error\.([A-Z0-9_]+)\.(title|detail)=(.*)$/.exec(line);
  if (match) {
    messages[match[1]] = messages[match[1]] ?? {};
    messages[match[1]][match[2]] = match[3].replace(/\{\d+\}/g, '…').replace(/\|/g, '\\|');
  }
}

const STATUS = {
  BAD_REQUEST: 400, UNAUTHORIZED: 401, FORBIDDEN: 403, NOT_FOUND: 404, METHOD_NOT_ALLOWED: 405, CONFLICT: 409,
  GONE: 410, CONTENT_TOO_LARGE: 413, UNSUPPORTED_MEDIA_TYPE: 415, TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500, SERVICE_UNAVAILABLE: 503,
};
const rows = [...java.matchAll(/^ {4}([A-Z0-9_]+)\(HttpStatus\.([A-Z_]+)\)/gm)].map(([, code, status]) => {
  const message = messages[code] ?? {};
  return `| \`${code}\` | ${STATUS[status] ?? status} | ${message.title ?? ''} | ${message.detail ?? ''} |`;
});

const out = `# Xəta kodları

> Bu fayl avtomatik yaradılır: \`node scripts/gen-error-codes.cjs\`. Əl ilə dəyişməyin.

API xətaları **Problem Details** (RFC 9457) formatında qaytarılır:

\`\`\`json
{
  "type": "urn:cafe-loyalty:problem:stamp-cooldown-active",
  "title": "Bir az gözləyin",
  "status": 409,
  "detail": "Növbəti möhürü 14 dəqiqədən sonra ala bilərsiniz.",
  "code": "STAMP_COOLDOWN_ACTIVE",
  "retryAfterSeconds": 840,
  "traceId": "4f1c2a…"
}
\`\`\`

- \`code\` sabitdir — frontend mətni bu koda görə öz tərcüməsindən göstərir (\`src/locales/*/errors.json\`).
- \`title\` və \`detail\` sorğunun dilindədir (\`Accept-Language\`, istifadəçinin profili və ya şirkətin standart dili).
- Daxili xətalarda (500) stack trace və ya texniki detal heç vaxt qaytarılmır, yalnız \`traceId\` (loglarda axtarış üçün).
- Validasiya xətalarında əlavə olaraq \`errors: [{field, code, message}]\` massivi gəlir.

Aşağıdakı cədvəldə mətnlər Azərbaycan dilindədir (\`messages_az.properties\`); \`…\` — dinamik dəyər.

| Kod | HTTP | Başlıq | İzah |
|---|---|---|---|
${rows.join('\n')}
`;
fs.writeFileSync(path.join(root, 'docs/error-codes.md'), out);
console.log(`docs/error-codes.md: ${rows.length} kod`);
