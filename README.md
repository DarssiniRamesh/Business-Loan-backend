# Business-Loan-backend

## Run locally (one command)

From this folder:

```bash
./script.sh
```

Swagger/OpenAPI docs (configured in `application.properties`):
- Swagger UI: `http://localhost:3010/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:3010/v3/api-docs`

## Verify MFA OTP email delivery via Mailjet (end-to-end)

### Required env vars (Mailjet)

Set these in the backend environment (or copy `.env.example` -> your real `.env` values):

- `APP_NOTIFICATIONS_EMAIL_PROVIDER=mailjet`
- `APP_NOTIFICATIONS_EMAIL_FROM=<a Mailjet-verified sender email>`
- `MAILJET_API_KEY=<mailjet api key>`
- `MAILJET_API_SECRET=<mailjet api secret>`

Recommended for realistic testing:
- `APP_MFA_DEV_RETURN_OTP=false` (so you must use the email)
- `APP_MFA_LOG_OTP_ON_FAILURE=false` (avoid plaintext OTP logs)

Notes:
- The backend will fall back to a stub email provider if no real provider is configured. If you see logs like
  `[STUB EMAIL] ...`, you are not sending real emails.
- If you forget to set `APP_NOTIFICATIONS_EMAIL_PROVIDER` but *do* set `MAILJET_API_KEY/MAILJET_API_SECRET`,
  the backend will auto-detect the credentials and use Mailjet.

### How to validate delivery

1) Restart the backend after setting env vars.
2) Register a user: `POST /api/auth/register`
3) Login step 1: `POST /api/auth/login`
   - This triggers OTP generation and attempts to email the OTP to the user email.
4) Check the inbox for the OTP email.
5) Complete login step 2 using the OTP:
   - `POST /api/auth/login/{userId}/mfa/verify`

If delivery fails, backend logs will include a warning like:
`MFA OTP email delivery failed (ignored)...`
and (if misconfigured) a message like:
`Mailjet credentials missing...` or `Email 'from' missing...`.