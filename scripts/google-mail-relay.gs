/**
 * PulseOS email relay — Google Apps Script (HTTPS).
 *
 * Deploy as Web App: Execute as me; Who has access: Anyone.
 * Set PULSEOS_MAIL_RELAY_KEY (64 hex characters) in Script Properties.
 *
 * IMPORTANT: Updates to this file must be copied to Code.gs and published via
 * Deploy -> Manage deployments -> Edit -> New version -> Deploy.
 * A GitHub push or editor Save does NOT update an existing /exec deployment.
 */
function doPost(e) {
  try {
    const request = e && e.postData && e.postData.contents;
    if (!request || request.length > 45000) return text_("INVALID");

    const payload = JSON.parse(request);
    const secret = PropertiesService.getScriptProperties()
      .getProperty("PULSEOS_MAIL_RELAY_KEY");
    if (!secret || secret.length < 48 || payload.relayKey !== secret) {
      return text_("DENIED");
    }

    const recipient = String(payload.email || "").trim().toLowerCase();
    const code = String(payload.code || "");
    // Older backend versions omit kind. Retain signup compatibility.
    const kind = payload.kind === undefined ? "signup" : payload.kind;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(recipient) ||
        recipient.length > 254 ||
        (kind !== "patient" && kind !== "appointment" && !/^[0-9]{6}$/.test(code)) ||
        ((kind === "patient" || kind === "appointment") && code !== "") ||
        (kind !== "signup" && kind !== "reset" && kind !== "patient" && kind !== "appointment")) {
      return text_("INVALID");
    }

    if (kind === "appointment") {
      if (!/^[a-z0-9_.-]{1,80}$/.test(payload.doctor || "") ||
          !/^[A-Z_]{1,40}$/.test(payload.visitType || "") ||
          !/^[A-Z_]{1,24}$/.test(payload.stage || "") ||
          !/^20[0-9]{2}-[0-9T:.Z-]{10,40}$/.test(payload.scheduledAt || "") ||
          !/^https:\/\/[^\s<>"]+\/patient\/checkin\?appointment=[0-9a-f-]{36}$/.test(payload.checkinUrl || "") ||
          !/^[A-Za-z0-9+/=]{100,40000}$/.test(payload.qrBase64 || ""))return text_("INVALID");
      const png=Utilities.base64Decode(payload.qrBase64);
      if(png.length>30000)return text_("INVALID");
      const blob=Utilities.newBlob(png,"image/png","pulseos-checkin.png");
      MailApp.sendEmail({
        to: recipient, subject: "PulseOS demo appointment · "+payload.stage.replace(/_/g," "),
        body: ["Fictional PulseOS appointment", "Assigned clinician: "+payload.doctor,
          "Scheduled: "+payload.scheduledAt,"Visit: "+payload.visitType,"Status: "+payload.stage,
          "Scan the attached QR or open: "+payload.checkinUrl,
          "You must sign in with your verified patient account to confirm."].join("\n"),
        htmlBody: '<div style="font-family:Arial;padding:24px;background:#f7f6ef">' +
          '<h1>PulseOS appointment</h1><p>Assigned clinician: '+payload.doctor+'</p>'+
          '<p>Scheduled: '+payload.scheduledAt+'</p><p>Status: '+payload.stage+'</p>'+
          '<p><a href="'+payload.checkinUrl+'">Open patient check-in</a></p>'+
          '<img src="cid:pulseos-qr" alt="Appointment QR" width="240" height="240">' +
          '<p>Sign in as the verified patient. Fictional records only.</p></div>',
        inlineImages: {"pulseos-qr":blob},name:"PulseOS"
      });
      return text_("OK");
    }
    const isReset = kind === "reset";
    const isPatient = kind === "patient";
    // Each request gets a non-sensitive reference so Gmail does not thread
    // newly generated codes under an older message with the same subject.
    const reference = Utilities.getUuid().slice(0, 8).toUpperCase();
    MailApp.sendEmail({
      to: recipient,
      subject: (isPatient ? "PulseOS demo notification" :
        isReset ? "Reset your PulseOS password" : "Verify your PulseOS email") +
        " · #" + reference,
      body: isPatient ? patientText_() : plainText_(code, isReset),
      htmlBody: isPatient ? patientHtml_() : html_(code, isReset),
      name: "PulseOS"
    });
    return text_("OK");
  } catch (error) {
    // Do not log request content, code, recipient or shared secret.
    return text_("ERROR");
  }
}

function patientText_() {
  return "PulseOS demo notification\\nYou have a new notification from the fictional-data demonstration. No patient information is included.";
}
function patientHtml_() {
  return '<html><body style="background:#f4f5f3;padding:30px;font-family:Arial,sans-serif">' +
    '<div style="max-width:520px;margin:auto;background:#fff;padding:30px;border-radius:14px;border-top:8px solid #f4c64d">' +
    '<h1 style="color:#171b24">Pulse<span style="color:#b48819">OS</span></h1>' +
    '<h2>New demo notification</h2><p>You have a new notification from the PulseOS fictional-data demonstration.</p>' +
    '<p style="font-size:12px;color:#777">No clinical information is included. This is an automated test message.</p></div></body></html>';
}

/** Readable fallback for text-only email clients. */
function plainText_(code, isReset) {
  const heading = isReset ? "Reset your PulseOS password" : "Verify your PulseOS email";
  return [
    "PULSEOS  |  SECURE ACCOUNT ACCESS",
    "",
    heading,
    "",
    isReset ? "Enter this code to reset your password:" :
      "Enter this code to verify your new account:",
    "",
    "    " + code,
    "",
    "Your code expires in 10 minutes. Do not share it with anyone.",
    "If you did not request this, you can safely ignore this email.",
    "",
    "-------------------------------",
    "PulseOS · Clinical operations demo",
    "Synthetic records only. Not for real patient data.",
    "This is an automated message; do not reply."
  ].join("\n");
}

/**
 * Table-based, fully inline email HTML for Gmail, Outlook, and mobile clients.
 * No remote images, external fonts, tracking pixels or user-supplied HTML.
 * Variable content is a strictly validated six-digit code.
 */
function html_(code, isReset) {
  const title = isReset ? "Reset your password" : "Verify your email";
  const subtitle = isReset
    ? "We received a request to reset the password for your PulseOS account."
    : "Welcome to PulseOS. You're one step away from activating your account.";
  const instruction = isReset
    ? "Enter the verification code below on the password recovery screen."
    : "Enter the verification code below to complete your registration.";
  const eyebrow = isReset ? "PASSWORD RECOVERY" : "ACCOUNT VERIFICATION";
  const preview = isReset
    ? "Your PulseOS password reset code is ready. Expires in 10 minutes."
    : "Your PulseOS email verification code is ready. Expires in 10 minutes.";

  return [
    '<!doctype html><html lang="en"><head>',
    '<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
    '<meta name="color-scheme" content="light">',
    '<title>PulseOS · ', title, '</title></head>',
    '<body style="margin:0;padding:0;background-color:#f4f5f3;color:#22262f;',
    'font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%;">',

    // Text shown beside the subject in most inboxes.
    '<div style="display:none!important;font-size:1px;line-height:1px;color:#f4f5f3;',
    'max-height:0;max-width:0;opacity:0;overflow:hidden;">', preview, '</div>',

    '<table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" ',
    'style="width:100%;border-collapse:collapse;background-color:#f4f5f3;">',
    '<tr><td align="center" style="padding:36px 16px 40px;">',

    '<table role="presentation" cellspacing="0" cellpadding="0" border="0" width="560" ',
    'style="width:100%;max-width:560px;border-collapse:separate;border-spacing:0;">',

    // Distinctive, logo-free header keeps email fast and reliably rendered.
    '<tr><td style="background-color:#171b24;padding:27px 34px;',
    'border-radius:16px 16px 0 0;">',
    '<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">',
    '<tr><td style="font-family:Arial,Helvetica,sans-serif;',
    'font-size:26px;font-weight:800;letter-spacing:-1.2px;color:#ffffff;">',
    'Pulse<span style="color:#f4c64d;">OS</span></td>',
    '<td align="right" style="font-family:Arial,Helvetica,sans-serif;',
    'font-size:10px;letter-spacing:1.6px;font-weight:bold;color:#d3d7de;">',
    'SECURE ACCESS</td></tr></table>',
    '<div style="height:4px;width:64px;background-color:#f4c64d;',
    'border-radius:4px;margin-top:19px;"></div>',
    '</td></tr>',

    '<tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;',
    'border-right:1px solid #e5e8e5;padding:36px 34px 12px;">',
    '<p style="margin:0 0 12px;font-size:11px;letter-spacing:2px;',
    'font-weight:700;color:#987015;">', eyebrow, '</p>',
    '<h1 style="font-size:29px;line-height:1.25;font-weight:800;',
    'letter-spacing:-0.6px;color:#20242c;margin:0 0 14px;">', title, '</h1>',
    '<p style="font-size:15px;line-height:1.7;color:#606773;margin:0 0 8px;">',
    subtitle, ' ', instruction, '</p>',
    '</td></tr>',

    // Large, centered, copyable six-digit token.
    '<tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;',
    'border-right:1px solid #e5e8e5;padding:14px 34px 22px;">',
    '<table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" ',
    'style="background-color:#fff6dc;border:1px solid #f0d18b;',
    'border-radius:12px;width:100%;"><tr><td align="center" ',
    'style="padding:24px 10px 21px;">',
    '<div style="font-size:10px;font-weight:800;letter-spacing:1.8px;',
    'color:#886313;margin-bottom:12px;">YOUR ONE-TIME CODE</div>',
    '<div style="font-size:38px;line-height:1.2;letter-spacing:9px;',
    'font-weight:800;color:#242832;font-family:Arial,Helvetica,sans-serif;">',
    code, '</div>',
    '<div style="font-size:12px;line-height:1.5;color:#876c32;',
    'margin-top:11px;">Valid for 10 minutes</div>',
    '</td></tr></table>',
    '</td></tr>',

    '<tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;',
    'border-right:1px solid #e5e8e5;padding:0 34px 36px;">',
    '<table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">',
    '<tr><td style="border-left:3px solid #e6b642;background-color:#faf9f5;',
    'padding:14px 16px;font-size:12px;line-height:1.7;color:#606773;">',
    '<strong style="color:#333743;">Keep your account secure.</strong> ',
    'Never share this code. PulseOS will not ask you to send it by email or chat.',
    '<br>If you did not request this, simply ignore the message.',
    '</td></tr></table>',
    '</td></tr>',

    '<tr><td style="background-color:#f9faf9;border:1px solid #e5e8e5;',
    'border-top:1px solid #eceeea;border-radius:0 0 16px 16px;',
    'padding:22px 34px 26px;">',
    '<p style="font-size:12px;font-weight:800;color:#464c56;',
    'margin:0 0 6px;">PulseOS &nbsp;·&nbsp; Clinical operations demo</p>',
    '<p style="font-size:11px;line-height:1.7;color:#818794;margin:0;">',
    'Fictional data only. Not approved for real patient information.',
    '<br>This message was sent automatically. Please do not reply.</p>',
    '</td></tr></table>',
    '</td></tr></table>',
    '</body></html>'
  ].join("");
}

// Safe, public status string for checking whether a new web-app deployment
// is running. Do not return secrets, users or private mailer configuration.
function doGet() {
  return text_("PulseOS email relay: styled HTML v4");
}

function text_(message) {
  return ContentService.createTextOutput(message)
    .setMimeType(ContentService.MimeType.TEXT);
}
