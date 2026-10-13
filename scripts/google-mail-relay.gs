/**
 * PulseOS Google Apps Script HTTPS email relay.
 * Deploy as Web App: Execute as me; Who has access: Anyone.
 * Keep PULSEOS_MAIL_RELAY_KEY in Script Properties and Render only.
 * Change in Apps Script requires a NEW DEPLOYMENT VERSION.
 */
function doPost(e) {
  try {
    const body = e && e.postData && e.postData.contents;
    if (!body || body.length > 4096) return text_("INVALID");
    const data = JSON.parse(body);
    const key = PropertiesService.getScriptProperties()
      .getProperty("PULSEOS_MAIL_RELAY_KEY");
    if (!key || key.length < 48 || data.relayKey !== key) return text_("DENIED");

    const email = String(data.email || "").trim().toLowerCase();
    const code = String(data.code || "");
    const kind = data.kind === undefined ? "signup" : data.kind;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254 ||
        !/^[0-9]{6}$/.test(code) || (kind !== "signup" && kind !== "reset")) {
      return text_("INVALID");
    }
    const reset = kind === "reset";
    const subject = reset ? "Reset your PulseOS password" : "Verify your PulseOS email";
    const message = reset ? "Your PulseOS password reset code is: " :
      "Your PulseOS verification code is: ";
    const plain = message + code +
      "\n\nThis code expires in 10 minutes. Never share it." +
      "\nIf you did not request this, you can ignore the message." +
      "\n\nPulseOS is a synthetic-data portfolio application.";
    MailApp.sendEmail({
      to: email,
      subject: subject,
      body: plain,
      htmlBody: html_(code, reset),
      name: "PulseOS"
    });
    return text_("OK");
  } catch (error) {
    // Do not log the request body, recipient, key, or code.
    return text_("ERROR");
  }
}

function html_(code, reset) {
  const title = reset ? "Reset your password" : "Verify your email";
  const description = reset ? "Use this code to choose a new password for PulseOS." :
    "Use this code to complete your PulseOS account registration.";
  // All variable text is fixed except code, which is restricted to six digits.
  return '<!doctype html><html><head><meta charset="UTF-8"></head>' +
    '<body style="margin:0;padding:32px 12px;background:#f7f7f4;font-family:Arial,Helvetica,sans-serif;color:#272933">' +
    '<table role="presentation" cellspacing="0" cellpadding="0" style="max-width:520px;width:100%;margin:auto;background:#ffffff;border:1px solid #e8e4d9;border-radius:16px">' +
    '<tr><td style="padding:34px 34px 14px">' +
    '<div style="font-size:28px;font-weight:800;letter-spacing:-1px">Pulse<span style="color:#bb8a16">OS</span></div>' +
    '<p style="color:#785409;font-size:11px;letter-spacing:2px;font-weight:700;margin:30px 0 8px">SECURE ACCOUNT ACCESS</p>' +
    '<h1 style="font-size:26px;line-height:1.3;margin:0 0 12px">' + title + '</h1>' +
    '<p style="font-size:15px;line-height:1.6;color:#555861;margin:0 0 24px">' + description + '</p>' +
    '<div style="text-align:center;background:#fff5cf;border:1px solid #e5b52b;border-radius:12px;padding:21px 10px;margin-bottom:20px">' +
    '<span style="font-family:Arial,Helvetica,sans-serif;font-size:34px;letter-spacing:9px;font-weight:800;color:#171b24">' + code + '</span></div>' +
    '<p style="font-size:13px;line-height:1.7;color:#555861">This code expires in <b>10 minutes</b>. Never share it. If you did not request this, ignore this email.</p>' +
    '</td></tr><tr><td style="padding:18px 34px 28px;border-top:1px solid #e8e4d9;color:#777983;font-size:11px;line-height:1.6">' +
    'PulseOS is a clinical operations portfolio using fictional data only. No real patient records.' +
    '</td></tr></table></body></html>';
}

function text_(value) {
  return ContentService.createTextOutput(value)
    .setMimeType(ContentService.MimeType.TEXT);
}
