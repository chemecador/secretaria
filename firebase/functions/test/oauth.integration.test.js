/* eslint-disable require-jsdoc, @typescript-eslint/no-var-requires */

const { after, before, test } = require("node:test");
const assert = require("node:assert/strict");
const { createHash, randomBytes } = require("node:crypto");
const admin = require("firebase-admin");
const { isAllowedRedirectUri } = require("../lib/oauth");

const PROJECT_ID = "demo-secretaria";
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST ?? "127.0.0.1:15001";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const FUNCTIONS = `http://${FUNCTIONS_HOST}/${PROJECT_ID}/europe-west1`;
// El emulador antepone el nombre de la funcion; el servidor enruta por el final.
const OAUTH = `${FUNCTIONS}/oauth`;
const MCP = `${FUNCTIONS}/mcp`;
const MCP_URL = "https://kotlin-secretaria.web.app/mcp";
const REDIRECT = "https://claude.example/api/mcp/auth_callback";

let db;
let userIdToken;
let userUid;
let anonymousIdToken;

async function signUp(body) {
  const res = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/` +
    "v1/accounts:signUp?key=fake", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ returnSecureToken: true, ...body }),
  });
  return res.json();
}

function pkce() {
  const verifier = randomBytes(32).toString("base64url");
  const challenge = createHash("sha256").update(verifier).digest("base64url");
  return { verifier, challenge };
}

async function register(body) {
  return fetch(`${OAUTH}/oauth/register`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

async function publicClient(name = "Claude") {
  const res = await register({
    client_name: name,
    redirect_uris: [REDIRECT],
    token_endpoint_auth_method: "none",
  });
  assert.equal(res.status, 201);
  return res.json();
}

function authorizeQuery(clientId, challenge, extra = {}) {
  return new URLSearchParams({
    response_type: "code",
    client_id: clientId,
    redirect_uri: REDIRECT,
    code_challenge: challenge,
    code_challenge_method: "S256",
    state: "xyz",
    resource: MCP_URL,
    ...extra,
  });
}

async function approve(clientId, challenge, idToken, extra = {}) {
  return fetch(`${OAUTH}/oauth/authorize`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      decision: "allow",
      client_id: clientId,
      redirect_uri: REDIRECT,
      code_challenge: challenge,
      state: "xyz",
      resource: MCP_URL,
      idToken,
      ...extra,
    }),
  });
}

async function token(params, headers = {}) {
  return fetch(`${OAUTH}/oauth/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded", ...headers },
    body: new URLSearchParams(params),
  });
}

async function codeFor(clientId, idToken = userIdToken) {
  const { verifier, challenge } = pkce();
  const res = await approve(clientId, challenge, idToken);
  assert.equal(res.status, 200);
  const url = new URL((await res.json()).redirect);
  assert.equal(url.searchParams.get("state"), "xyz");
  return { code: url.searchParams.get("code"), verifier };
}

async function mcpCall(accessToken, method = "tools/list", params = undefined) {
  return fetch(MCP, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }),
  });
}

before(async () => {
  assert.ok(AUTH_HOST, "Run with the Auth emulator.");
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "Run with Firestore.");
  admin.initializeApp({ projectId: PROJECT_ID });
  db = admin.firestore();
  const user = await signUp({ email: "mcp@example.com", password: "secret12" });
  userIdToken = user.idToken;
  userUid = user.localId;
  anonymousIdToken = (await signUp({})).idToken;
});

after(async () => {
  await admin.app().delete();
});

test("redirect URIs: https and loopback only, no fragments", () => {
  assert.equal(isAllowedRedirectUri("https://claude.ai/cb"), true);
  assert.equal(isAllowedRedirectUri("http://localhost:3000/cb"), true);
  assert.equal(isAllowedRedirectUri("http://127.0.0.1/cb"), true);
  assert.equal(isAllowedRedirectUri("http://evil.example/cb"), false);
  assert.equal(isAllowedRedirectUri("myapp://cb"), false);
  assert.equal(isAllowedRedirectUri("https://claude.ai/cb#x"), false);
  assert.equal(isAllowedRedirectUri("not a url"), false);
});

test("discovery documents point at the canonical host", async () => {
  const resource = await (await fetch(
    `${OAUTH}/.well-known/oauth-protected-resource`)).json();
  assert.equal(resource.resource, MCP_URL);
  assert.deepEqual(resource.authorization_servers,
    ["https://kotlin-secretaria.web.app"]);
  const server = await (await fetch(
    `${OAUTH}/.well-known/oauth-authorization-server`)).json();
  assert.deepEqual(server.code_challenge_methods_supported, ["S256"]);
  assert.equal(server.registration_endpoint,
    "https://kotlin-secretaria.web.app/oauth/register");
});

test("an unauthenticated MCP call points at the resource metadata", async () => {
  const res = await mcpCall("sec_nope");
  assert.equal(res.status, 401);
  assert.match(res.headers.get("www-authenticate"),
    /resource_metadata="https:\/\/kotlin-secretaria\.web\.app\/\.well-known\/oauth-protected-resource"/);
});

test("registration rejects unsafe redirect URIs and bad auth methods", async () => {
  assert.equal((await register({ redirect_uris: ["http://evil.example/x"] }))
    .status, 400);
  assert.equal((await register({})).status, 400);
  assert.equal((await register({
    redirect_uris: [REDIRECT], token_endpoint_auth_method: "private_key_jwt",
  })).status, 400);
});

test("authorize GET renders consent, never redirects on a bad client", async () => {
  const client = await publicClient("Claude <script>alert(1)</script>");
  const { challenge } = pkce();
  const page = await fetch(`${OAUTH}/oauth/authorize?` +
    authorizeQuery(client.client_id, challenge));
  assert.equal(page.status, 200);
  assert.match(page.headers.get("content-security-policy"),
    /frame-ancestors 'none'/);
  const html = await page.text();
  assert.ok(!html.includes("<script>alert(1)"), "client name must be escaped");
  assert.ok(html.includes("claude.example"));

  const unknown = await fetch(`${OAUTH}/oauth/authorize?` +
    authorizeQuery("nope", challenge), { redirect: "manual" });
  assert.equal(unknown.status, 400);
  const wrongUri = await fetch(`${OAUTH}/oauth/authorize?` +
    authorizeQuery(client.client_id, challenge,
      { redirect_uri: "https://evil.example/cb" }), { redirect: "manual" });
  assert.equal(wrongUri.status, 400);

  const noPkce = await fetch(`${OAUTH}/oauth/authorize?` +
    authorizeQuery(client.client_id, challenge, { code_challenge: "" }),
  { redirect: "manual" });
  assert.equal(noPkce.status, 302);
  const location = new URL(noPkce.headers.get("location"));
  assert.equal(location.origin + location.pathname, REDIRECT);
  assert.equal(location.searchParams.get("error"), "invalid_request");
  assert.equal(location.searchParams.get("state"), "xyz");
});

test("full flow: authorize, token, call the MCP, refresh with rotation", async () => {
  const client = await publicClient();
  const { code, verifier } = await codeFor(client.client_id);

  const wrong = await token({
    grant_type: "authorization_code", client_id: client.client_id, code,
    redirect_uri: REDIRECT, code_verifier: randomBytes(32).toString("base64url"),
  });
  assert.equal(wrong.status, 400, "a wrong verifier fails");
  assert.equal((await wrong.json()).error, "invalid_grant");

  // El codigo se consume aunque falle: no se puede reintentar con el bueno.
  const retried = await token({
    grant_type: "authorization_code", client_id: client.client_id, code,
    redirect_uri: REDIRECT, code_verifier: verifier,
  });
  assert.equal(retried.status, 400);

  const fresh = await codeFor(client.client_id);
  const exchanged = await token({
    grant_type: "authorization_code", client_id: client.client_id,
    code: fresh.code, redirect_uri: REDIRECT, code_verifier: fresh.verifier,
    resource: MCP_URL,
  });
  assert.equal(exchanged.status, 200);
  assert.equal(exchanged.headers.get("cache-control"), "no-store");
  const tokens = await exchanged.json();
  assert.equal(tokens.token_type, "Bearer");
  assert.equal(tokens.expires_in, 3600);

  const list = await mcpCall(tokens.access_token);
  assert.equal(list.status, 200);
  const create = await mcpCall(tokens.access_token, "tools/call", {
    name: "create_reminder", arguments: { text: "Via OAuth" } });
  assert.equal((await create.json()).result.isError, undefined);
  const owned = await db.collection(`users/${userUid}/reminders`)
    .where("text", "==", "Via OAuth").get();
  assert.equal(owned.size, 1, "the reminder belongs to the signed-in user");
  const usage = await db.collection("mcpUsage").doc(userUid).get();
  assert.equal(usage.get("usageCount"), 1, "the daily cap counts per user");

  const refreshed = await token({
    grant_type: "refresh_token", client_id: client.client_id,
    refresh_token: tokens.refresh_token,
  });
  assert.equal(refreshed.status, 200);
  const rotated = await refreshed.json();
  assert.notEqual(rotated.refresh_token, tokens.refresh_token);
  assert.equal((await mcpCall(rotated.access_token)).status, 200);

  const replay = await token({
    grant_type: "refresh_token", client_id: client.client_id,
    refresh_token: tokens.refresh_token,
  });
  assert.equal(replay.status, 400, "a used refresh token is dead");
});

test("anonymous accounts and invalid sign-ins are refused", async () => {
  const client = await publicClient();
  const { challenge } = pkce();
  const anonymous = await approve(client.client_id, challenge,
    anonymousIdToken);
  assert.equal(anonymous.status, 403);
  const forged = await approve(client.client_id, challenge, "not-a-token");
  assert.equal(forged.status, 401);
  const noToken = await approve(client.client_id, challenge, undefined);
  assert.equal(noToken.status, 400);
});

test("deny and a foreign resource come back as redirect errors", async () => {
  const client = await publicClient();
  const { challenge } = pkce();
  const denied = await (await approve(client.client_id, challenge,
    undefined, { decision: "deny" })).json();
  assert.equal(new URL(denied.redirect).searchParams.get("error"),
    "access_denied");
  const foreign = await (await approve(client.client_id, challenge,
    userIdToken, { resource: "https://other.example/mcp" })).json();
  assert.equal(new URL(foreign.redirect).searchParams.get("error"),
    "invalid_target");
});

test("a code cannot be redeemed by another client or another redirect", async () => {
  const a = await publicClient();
  const b = await publicClient();
  const { code, verifier } = await codeFor(a.client_id);
  const stolen = await token({
    grant_type: "authorization_code", client_id: b.client_id, code,
    redirect_uri: REDIRECT, code_verifier: verifier,
  });
  assert.equal(stolen.status, 400);
  const again = await token({
    grant_type: "authorization_code", client_id: a.client_id, code,
    redirect_uri: REDIRECT, code_verifier: verifier,
  });
  assert.equal(again.status, 400, "a stolen code is burned for its owner too");
});

test("confidential clients must present their secret", async () => {
  const res = await register({
    client_name: "Confidential",
    redirect_uris: [REDIRECT],
    token_endpoint_auth_method: "client_secret_post",
  });
  const client = await res.json();
  assert.ok(client.client_secret);
  const { code, verifier } = await codeFor(client.client_id);
  const base = {
    grant_type: "authorization_code", client_id: client.client_id, code,
    redirect_uri: REDIRECT, code_verifier: verifier,
  };
  const bad = await token({ ...base, client_secret: "sec_cs_wrong" });
  assert.equal(bad.status, 401);
  const none = await token(base);
  assert.equal(none.status, 401);
  // Los intentos fallidos por credenciales no consumen el codigo.
  const good = await token({ ...base, client_secret: client.client_secret });
  assert.equal(good.status, 200);
});

test("expired and revoked OAuth access tokens stop working", async () => {
  const client = await publicClient();
  const { code, verifier } = await codeFor(client.client_id);
  const tokens = await (await token({
    grant_type: "authorization_code", client_id: client.client_id, code,
    redirect_uri: REDIRECT, code_verifier: verifier,
  })).json();
  const hash = createHash("sha256").update(tokens.access_token).digest("hex");
  const ref = db.collection("mcpTokens").doc(hash);
  assert.equal((await mcpCall(tokens.access_token)).status, 200);
  await ref.update({
    expiresAt: admin.firestore.Timestamp.fromMillis(Date.now() - 1000) });
  assert.equal((await mcpCall(tokens.access_token)).status, 401);
  await ref.update({
    expiresAt: admin.firestore.Timestamp.fromMillis(Date.now() + 60000),
    revoked: true });
  assert.equal((await mcpCall(tokens.access_token)).status, 401);
});
