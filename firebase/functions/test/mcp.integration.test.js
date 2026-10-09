/* eslint-disable require-jsdoc, @typescript-eslint/no-var-requires */

const { after, before, test } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const { hashMcpToken, isRealDate, parseReminderInput } =
  require("../lib/mcp");

const PROJECT_ID = "demo-secretaria";
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST ?? "127.0.0.1:15001";
const URL = `http://${FUNCTIONS_HOST}/${PROJECT_ID}/europe-west1/mcp`;
const UID = "mcp-user";
const TOKEN = "sec_test-token";

let db;

function rpc(body, token = TOKEN) {
  return fetch(URL, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
}

function call(args, id = 1) {
  return rpc({
    jsonrpc: "2.0", id, method: "tools/call",
    params: { name: "create_reminder", arguments: args },
  });
}

before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Run with the Firestore emulator.");
  admin.initializeApp({ projectId: PROJECT_ID });
  db = admin.firestore();
  await db.collection("mcpTokens").doc(hashMcpToken(TOKEN)).set({
    uid: UID, revoked: false,
  });
});

after(async () => {
  await admin.app().delete();
});

test("isRealDate rejects impossible calendar days", () => {
  assert.equal(isRealDate("2028-02-29"), true);
  assert.equal(isRealDate("2027-02-29"), false);
  assert.equal(isRealDate("2027-13-01"), false);
  assert.equal(isRealDate("2027-1-01"), false);
});

test("parseReminderInput validates and normalises", () => {
  assert.deepEqual(parseReminderInput({ text: " a ", description: "  " }), {
    text: "a", description: null, dueDate: null, dueTime: null,
  });
  assert.throws(() => parseReminderInput({}), /text is required/);
  assert.throws(() => parseReminderInput({ text: "a", dueTime: "10:00" }),
    /requires dueDate/);
  assert.throws(() => parseReminderInput({
    text: "a", dueDate: "2027-01-01", dueTime: "24:00" }), /real time/);
});

test("rejects missing, wrong and revoked tokens", async () => {
  const body = { jsonrpc: "2.0", id: 1, method: "ping" };
  assert.equal((await rpc(body, null)).status, 401);
  assert.equal((await rpc(body, "sec_nope")).status, 401);
  await db.collection("mcpTokens").doc(hashMcpToken("sec_revoked"))
    .set({ uid: UID, revoked: true });
  assert.equal((await rpc(body, "sec_revoked")).status, 401);
  const get = await fetch(URL);
  assert.equal(get.status, 405);
});

test("initialize and tools/list", async () => {
  const init = await (await rpc({
    jsonrpc: "2.0", id: 1, method: "initialize",
    params: { protocolVersion: "2025-03-26" },
  })).json();
  assert.equal(init.result.protocolVersion, "2025-03-26");
  const list = await (await rpc({
    jsonrpc: "2.0", id: 2, method: "tools/list" })).json();
  assert.equal(list.result.tools[0].name, "create_reminder");
  const note = await rpc({
    jsonrpc: "2.0", method: "notifications/initialized" });
  assert.equal(note.status, 202);
});

test("create_reminder writes the document the client expects", async () => {
  const res = await (await call({
    text: "Comprar pan", dueDate: "2027-03-01", dueTime: "09:30" })).json();
  assert.notEqual(res.result.isError, true);
  const docs = await db.collection(`users/${UID}/reminders`).get();
  assert.equal(docs.size, 1);
  const data = docs.docs[0].data();
  assert.equal(data.text, "Comprar pan");
  assert.equal(data.description, null);
  assert.equal(data.dueDate, "2027-03-01");
  assert.equal(data.dueTime, "09:30");
  assert.equal(data.completed, false);
  assert.equal(data.order, 0);
  assert.deepEqual(data.contributors, [UID]);
});

test("validation errors come back as tool results", async () => {
  const res = await (await call({ text: "x", dueDate: "2027-02-30" })).json();
  assert.equal(res.result.isError, true);
});

async function list(args = {}) {
  const res = await (await rpc({
    jsonrpc: "2.0", id: 9, method: "tools/call",
    params: { name: "list_reminders", arguments: args },
  })).json();
  return res.result;
}

test("list_reminders returns pending in order, with shared ones", async () => {
  const other = "users/other-user/reminders";
  await db.collection(other).doc("sh").set({
    text: "Compartido", completed: false, order: 99, contributors: ["other-user", UID],
    date: admin.firestore.Timestamp.now(),
  });
  await db.collection(other).doc("private").set({
    text: "Ajeno", completed: false, order: 0, contributors: ["other-user"],
    date: admin.firestore.Timestamp.now(),
  });
  await db.collection(`users/${UID}/reminders`).doc("done").set({
    text: "Hecho", completed: true, order: 0, contributors: [UID],
    date: admin.firestore.Timestamp.now(),
    completedAt: admin.firestore.Timestamp.now(),
  });
  const result = await list();
  assert.notEqual(result.isError, true);
  const rows = JSON.parse(result.content[0].text);
  const texts = rows.map((r) => r.text);
  assert.equal(texts[0], "Comprar pan");
  assert.equal(texts.at(-1), "Compartido");
  assert.ok(!texts.includes("Ajeno"));
  assert.ok(!texts.includes("Hecho"));
  assert.equal(rows.at(-1).shared, true);
  assert.equal(rows[0].shared, false);
});

test("list_reminders filters by status and limit", async () => {
  const completed = JSON.parse((await list({ status: "completed" }))
    .content[0].text);
  assert.deepEqual(completed.map((r) => r.text), ["Hecho"]);
  const all = JSON.parse((await list({ status: "all", limit: 2 }))
    .content[0].text);
  assert.equal(all.length, 2);
  assert.equal((await list({ status: "nope" })).isError, true);
  assert.equal((await list({ limit: 0 })).isError, true);
});

test("concurrent creations get distinct orders", async () => {
  await Promise.all([1, 2, 3, 4].map((n) => call({ text: `p${n}` }, n)));
  const docs = await db.collection(`users/${UID}/reminders`)
    .where("completed", "==", false).get();
  const orders = docs.docs.map((d) => d.get("order")).sort();
  assert.deepEqual(orders, [0, 1, 2, 3, 4]);
});
