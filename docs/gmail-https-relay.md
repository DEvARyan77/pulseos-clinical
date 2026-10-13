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

## Why you are currently getting plain-text emails

The **deployed Google Apps Script version**, not the PulseOS website, controls the body sent from your Gmail account. The Java backend sends a six-digit code and its purpose; an old `Code.gs` deployment always sends the original plain text. **A GitHub commit, Render redeploy, or saving the Apps Script editor cannot replace the running web-app version.**

### Activate the designed version (existing working Gmail relay)

1. Open the **same Google Apps Script project** that currently sends your PulseOS verification emails.
2. Open [`scripts/google-mail-relay.gs`](../scripts/google-mail-relay.gs) on GitHub, select **Raw**, copy the complete file, and replace all content in `Code.gs`. Click **Save**.
3. In Apps Script, choose **Deploy → Manage deployments**. Find the existing Web app, choose **Edit (pencil) → Version: New version → Deploy**. Keep **Execute as: Me** and **Who has access: Anyone**.
4. Keep the **same deployment** and existing `/exec` URL. Do not create a second web app or change the Render relay URL, sending account, or shared secret.
5. Trigger a fresh signup verification (using a fictional-data test account) or request a password reset and open the received message in Gmail. It should have the charcoal PulseOS header, amber six-digit code panel, 10-minute countdown text, security warning, and subtle footer.

The script sends `htmlBody` **and** `body`, allowing a normal styled HTML view plus a plain-text fallback for text-only email clients. If Gmail still shows plain text, confirm **Manage deployments** points to the **new version**; just saving `Code.gs` is not enough.

**No new password, API key, Gmail permission, or Render environment variable is required.** Keep the existing `PULSEOS_MAIL_RELAY_KEY` property and its corresponding Render key private.

For regression checks, run `node --test scripts/tests/google-mail-relay.test.cjs` from the repository root. Tests verify both email types, inline HTML styling, plain-text fallback, and invalid-request rejection without sending real messages.

## Update an existing Apps Script deployment

If the email relay is **already sending signup codes**, replace the code in the Apps Script editor with the latest [PulseOS email relay](../scripts/google-mail-relay.gs), then choose **Deploy → Manage deployments → Edit (pencil) → Version: New version → Deploy**. Simply saving `Code.gs` does not update the running `/exec` URL.

The updated script sends **branded amber/charcoal HTML emails** for both account verification and password recovery, with a plain-text fallback. The existing Render relay URL and secret remain unchanged. Older versions will still accept signup codes, but will not show the new HTML design or the distinct password-reset subject.

## Check which email template is actually running

After you **update the existing Google Apps Script deployment** (Deploy → Manage deployments → Edit → New version → Deploy), open your deployed `https://script.google.com/macros/s/.../exec` URL in a browser. The new `GET` endpoint displays only:

```text
PulseOS email relay: styled HTML v4
```

If it instead shows a Google error or something else, the **published Web app** has not been updated. The GitHub repository and Render cannot update a Google Apps Script deployment owned by your Google account. No Render secret or Gmail app password needs changing.

This release adds a short **non-secret request reference** to each email subject so Gmail is less likely to hide a new verification email inside an older conversation. Never put the actual verification code in the email subject. Each email has both a designed HTML body (`htmlBody`) and a plaintext fallback (`body`). Google may still delay delivery after its email-sending API accepts a message; inbox delivery cannot be guaranteed by PulseOS.

Backend logs now distinguish **initial signup accepted by email provider** from **signup resend accepted by email provider**, without exposing recipient emails or OTPs. If only a resend arrives, check the original message's Spam/Promotions and Gmail threading, as well as Apps Script → Executions and Render API logs around both requests.

## Troubleshooting

- `503`: the sender isn't configured, Apps Script hasn't been authorized/deployed, the shared key differs, the Gmail account is over quota, or Google is unavailable. Recheck the Script's **Executions** page (it does not log codes) and Render application logs.
- Update the Apps Script web app **deployment version** after modifying `Code.gs`; saving the editor alone does not update the deployed `/exec`.
- Run the Google Script under the account you want to send from. Only the owner can approve MailApp permissions.
- Google Apps Script's ContentService returns a redirect to `script.googleusercontent.com`; the backend follows it. In some regions this redirect is intermittently unreliable; if you see intermittent failures, switch to a transactional HTTPS provider such as Resend.
- This relay has quota limitations, and an internet-accessible endpoint protected by a shared key. Rotate the key if exposed and use proper anti-abuse controls before opening signup widely.

The project also supports `resend` and `smtp` email-provider modes. Free Render instances cannot use Gmail SMTP ports 465 or 587.
