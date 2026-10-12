/**
 * PulseOS Gmail email relay for Render Free.
 * Google account owner deploys as a Web App: Execute as me; Who has access: Anyone.
 * Keep PULSEOS_MAIL_RELAY_KEY only in Apps Script Properties and Render server secrets.
 * Never commit the secret, and never put it in a browser, query parameter or log.
 */
function doPost(e) {
  try {
    const text = e && e.postData && e.postData.contents;
    if (!text || text.length > 4096) return response_("INVALID");
    const payload = JSON.parse(text);
    const secret = PropertiesService.getScriptProperties().getProperty("PULSEOS_MAIL_RELAY_KEY");
    if (!secret || secret.length < 48 || payload.relayKey !== secret) {
      return response_("DENIED");
    }
    const email = String(payload.email || "").trim().toLowerCase();
    const code = String(payload.code || "");
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254 ||
        !/^[0-9]{6}$/.test(code)) {
      return response_("INVALID");
    }
    // Fixed content. Caller cannot use the relay to send arbitrary messages.
    const subject = "Verify your PulseOS email";
    const body = "Your PulseOS verification code is: " + code +
      "\n\nThe code expires in 10 minutes. If you didn't register, ignore this message." +
      "\n\nPulseOS is a synthetic-data portfolio application. Do not enter real patient records.";
    MailApp.sendEmail(email, subject, body);
    return response_("OK");
  } catch (error) {
    // Do not log credentials, addresses or verification codes.
    return response_("ERROR");
  }
}

function response_(text) {
  return ContentService.createTextOutput(text)
    .setMimeType(ContentService.MimeType.TEXT);
}
