# Gmail delivery through Google Apps Script (Render Free)

PulseOS can send six-digit signup codes **from your own Gmail account**, without SMTP and without entering a Gmail app password in Render. Google Apps Script runs under your Google identity and receives delivery requests over HTTPS.

> The web app requires setup and authorization by the Gmail account owner. No emails are sent until these steps are completed. Use this only for a synthetic-data portfolio, not real clinical use.

## Set up the Gmail relay

1. Sign into the Google account you wish to send from. Open [Google Apps Script](https://script.google.com/home/projects/create) and create a blank project named `PulseOS Email Relay`.
2. Replace its `Code.gs` content with the source from [scripts/google-mail-relay.gs](../scripts/google-mail-relay.gs). Save the project.
3. Generate one new secret locally using `openssl rand -hex 32` (64 hex characters). In the script editor, go to **Project Settings → Script properties → Add script property**. Name it `PULSEOS_MAIL_RELAY_KEY`; set it to the generated secret.
4. Go to **Deploy → New deployment → Web app**. Choose **Execute as: Me** and **Who has access: Anyone**. Click **Deploy**, review Google's Gmail/Script authorization, and copy the resulting HTTPS URL ending in `/exec`. Only use a URL from `script.google.com/macros/s/...`.
5. On the Render **pulseos-api** service, in **Environment**, configure:

   ```env
   PULSEOS_EMAIL_PROVIDER=google-script
   PULSEOS_EMAIL_FROM=<gmail account running the script>
   PULSEOS_GOOGLE_SCRIPT_URL=<https://script.google.com/macros/s/.../exec>
   PULSEOS_GOOGLE_SCRIPT_KEY=<same 64-character secret from step 3>
   ```

   Keep `PULSEOS_AUTH_ENABLED=true` and `PULSEOS_REGISTRATION_ENABLED=true` on API and web. Save and deploy the API.
6. At [PulseOS signup](https://pulseos-web-5z25.onrender.com/signup), register with a **new** username and email. Check inbox and spam for a six-digit code, enter it in the app, then log in.

**Do not share the relay key or Gmail password in chat or commit them.** The Apps Script key and the backend key must match. The formerly shared Gmail app password is not used, and should be revoked.

## Troubleshooting

- `503`: the sender isn't configured, Apps Script hasn't been authorized/deployed, the shared key differs, the Gmail account is over quota, or Google is unavailable. Recheck the Script's **Executions** page (it does not log codes) and Render application logs.
- Update the Apps Script web app **deployment version** after modifying `Code.gs`; saving the editor alone does not update the deployed `/exec`.
- Run the Google Script under the account you want to send from. Only the owner can approve MailApp permissions.
- Google Apps Script's ContentService returns a redirect to `script.googleusercontent.com`; the backend follows it. In some regions this redirect is intermittently unreliable; if you see intermittent failures, switch to a transactional HTTPS provider such as Resend.
- This relay has quota limitations, and an internet-accessible endpoint protected by a shared key. Rotate the key if exposed and use proper anti-abuse controls before opening signup widely.

The project also supports `resend` and `smtp` email-provider modes. Free Render instances cannot use Gmail SMTP ports 465 or 587.
