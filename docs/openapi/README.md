# Frontend OpenAPI contract

`api-v1.json` is generated from the current Spring controllers by springdoc, using the local/test context and fake Liner adapter. It contains API schemas, not proof that authentication, payment, or person adapters are ready.

Regenerate from `backend/`:

```powershell
.\gradlew.bat --no-daemon exportOpenApi
```

The export task does not start a public HTTP server or call external providers. The normal OpenAPI integration test checks funded-quote schema separation and nullable funding fields, so these checks also run in Backend CI.

FE Q-34 uses `charged` for both general and suneung quotes. Quote reload is `GET /api/v1/quotes/{quoteId}`. Q-35 basic-saju input can use `personId` exclusively instead of raw birth information. See `backend/docs/FE_REQUESTS_20261009.md` for readiness and remaining dependencies.
