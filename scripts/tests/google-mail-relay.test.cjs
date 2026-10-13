const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const source = fs.readFileSync(
  path.join(__dirname, "..", "google-mail-relay.gs"), "utf8"
);

function harness(secret = "a".repeat(64)) {
  const sent = [];
  const context = {
    PropertiesService: {
      getScriptProperties: () => ({ getProperty: () => secret })
    },
    MailApp: {
      sendEmail(message) { sent.push(message); }
    },
    Utilities: {
      getUuid() { return "a1b2c3d4-ffff-eeee-1234-000000000000"; }
    },
    ContentService: {
      MimeType: { TEXT: "text/plain" },
      createTextOutput(message) {
        return {
          message,
          setMimeType(value) { this.mimeType = value; return this; }
        };
      }
    }
  };
  vm.createContext(context);
  vm.runInContext(source, context, { filename: "google-mail-relay.gs" });
  const post = (data) => context.doPost({
    postData: { contents: JSON.stringify(data) }
  }).message;
  return { sent, post, context };
}

test("signup delivers a designed HTML email and plain-text fallback", () => {
  const h = harness();
  const outcome = h.post({
    relayKey: "a".repeat(64),
    email: "clinician@example.test",
    code: "012345",
    kind: "signup"
  });
  assert.equal(outcome, "OK");
  assert.equal(h.sent.length, 1);
  const message = h.sent[0];
  assert.equal(message.name, "PulseOS");
  assert.equal(message.subject, "Verify your PulseOS email · #A1B2C3D4");
  assert.match(message.body, /012345/);
  assert.match(message.body, /10 minutes/);
  assert.match(message.htmlBody, /012345/);
  assert.match(message.htmlBody, /ACCOUNT VERIFICATION/);
  assert.match(message.htmlBody, /YOUR ONE-TIME CODE/);
  assert.match(message.htmlBody, /#171b24/);
  assert.match(message.htmlBody, /#f4c64d/);
  assert.match(message.htmlBody, /Fictional data only/);
  assert.match(message.htmlBody, /role="presentation"/);
  assert.doesNotMatch(message.htmlBody, /<img|https?:\/\//i);
  assert.doesNotMatch(message.htmlBody, /a{64}/);
});

test("password reset uses a distinct branded subject and contextual instructions", () => {
  const h = harness();
  assert.equal(h.post({
    relayKey: "a".repeat(64), email: "clinician@example.test",
    code: "654321", kind: "reset"
  }), "OK");
  const message = h.sent[0];
  assert.equal(message.subject, "Reset your PulseOS password · #A1B2C3D4");
  assert.match(message.htmlBody, /PASSWORD RECOVERY/);
  assert.match(message.htmlBody, /Reset your password/);
  assert.match(message.htmlBody, /654321/);
  assert.match(message.body, /654321/);
});

test("backward compatibility: missing kind defaults to signup", () => {
  const h = harness();
  assert.equal(h.post({
    relayKey: "a".repeat(64), email: "clinician@example.test",
    code: "111222"
  }), "OK");
  assert.match(h.sent[0].htmlBody, /ACCOUNT VERIFICATION/);
});

test("bad secret is rejected without sending anything", () => {
  const h = harness();
  assert.equal(h.post({
    relayKey: "wrong", email: "clinician@example.test",
    code: "123456", kind: "signup"
  }), "DENIED");
  assert.equal(h.sent.length, 0);
});

test("malformed, invalid purpose, and HTML injection are rejected", () => {
  const h = harness();
  const payload = { relayKey: "a".repeat(64), email: "clinician@example.test",
    code: "123456", kind: "signup" };
  assert.equal(h.post({ ...payload, code: "<script>alert(1)</script>" }), "INVALID");
  assert.equal(h.post({ ...payload, email: "not-an-address" }), "INVALID");
  assert.equal(h.post({ ...payload, kind: "newsletter" }), "INVALID");
  assert.equal(h.sent.length, 0);
});

test("deployed relay provides a non-sensitive HTML version marker", () => {
  const h = harness();
  assert.equal(h.context.doGet().message, "PulseOS email relay: styled HTML v4");
});

test("patient notification uses fixed, non-sensitive HTML and no code", () => {
  const h = harness();
  const result = h.post({ relayKey: "a".repeat(64), email: "test@example.test",
    code: "", kind: "patient" });
  assert.equal(result, "OK");
  assert.match(h.sent[0].subject, /PulseOS demo notification/);
  assert.match(h.sent[0].htmlBody, /No clinical information is included/);
  assert.doesNotMatch(h.sent[0].htmlBody, /123456/);
});
