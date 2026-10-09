import { onRequest, Request } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";
import { Timestamp } from "firebase-admin/firestore";
import { createHash, randomBytes, timingSafeEqual } from "crypto";
import type { Response } from "express";
import { hashMcpToken } from "./mcp";
import {
  BASE_URL,
  MCP_TOKENS_COLLECTION,
  MCP_URL,
  OAUTH_CLIENTS_COLLECTION,
  OAUTH_CODES_COLLECTION,
  OAUTH_REFRESH_COLLECTION,
  OAUTH_STATE_COLLECTION,
  REGION,
} from "./mcpConfig";

/*
 * Servidor de autorizacion OAuth 2.1 minimo para el servidor MCP: registro
 * dinamico de clientes (RFC 7591), authorization code + PKCE S256 y refresh
 * tokens rotatorios. El usuario se identifica con Firebase Auth en la pagina
 * de consentimiento; los tokens que se emiten son opacos y solo se guarda su
 * hash. Sin dependencias: todo es `crypto`, Firestore y HTTP.
 */

const CODE_TTL_MS = 5 * 60 * 1000;
const ACCESS_TOKEN_TTL_MS = 60 * 60 * 1000;
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;
/** El registro es abierto por diseno, asi que se acota por dia (UTC). */
const DAILY_REGISTRATION_LIMIT = 200;
const MAX_REDIRECT_URIS = 5;
const MAX_CLIENT_NAME_LENGTH = 100;
const AUTH_METHODS = ["none", "client_secret_post", "client_secret_basic"];
const LOOPBACK_HOSTS = ["localhost", "127.0.0.1", "[::1]"];
const PKCE_VERIFIER = /^[A-Za-z0-9\-._~]{43,128}$/;
const PKCE_CHALLENGE = /^[A-Za-z0-9\-_]{43}$/;

interface ClientRecord {
  clientId: string;
  clientName: string;
  redirectUris: string[];
  authMethod: string;
  secretHash: string | null;
}

interface AuthorizeParams {
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  state: string | null;
  resource: string | null;
}

/** Fallo que se devuelve al cliente como error OAuth JSON. */
class OAuthError extends Error {
  /**
   * @param {number} status Codigo HTTP.
   * @param {string} code Codigo de error OAuth.
   * @param {string} message Descripcion para el cliente.
   */
  constructor(readonly status: number, readonly code: string,
    message: string) {
    super(message);
  }
}

/**
 * @param {string} prefix Prefijo legible del tipo de secreto.
 * @return {string} Secreto aleatorio de 256 bits.
 */
function randomSecret(prefix: string): string {
  return `${prefix}${randomBytes(32).toString("base64url")}`;
}

/**
 * @param {unknown} value Valor candidato.
 * @return {string | null} La cadena, o null si no es una cadena no vacia.
 */
function str(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

/**
 * @param {string} a Hash hexadecimal.
 * @param {string} b Hash hexadecimal.
 * @return {boolean} Si son iguales, en tiempo constante.
 */
function sameHash(a: string, b: string): boolean {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

/**
 * Solo `https` o `http` hacia loopback (clientes de escritorio como Claude
 * Code), y sin fragmento: lo pide el RFC 6749.
 * @param {string} value URI de redireccion candidata.
 * @return {boolean} Si se acepta.
 */
export function isAllowedRedirectUri(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  if (url.hash !== "" || url.username !== "" || url.password !== "") {
    return false;
  }
  if (url.protocol === "https:") return true;
  return url.protocol === "http:" && LOOPBACK_HOSTS.includes(url.hostname);
}

/**
 * @param {Response} res Respuesta.
 * @return {void}
 */
function noStore(res: Response): void {
  res.set("Cache-Control", "no-store").set("Pragma", "no-cache");
}

/**
 * @param {Response} res Respuesta.
 * @return {void}
 */
function allowCors(res: Response): void {
  res.set("Access-Control-Allow-Origin", "*")
    .set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    .set("Access-Control-Allow-Headers", "Content-Type, Authorization, " +
      "MCP-Protocol-Version");
}

/**
 * @param {Response} res Respuesta.
 * @param {OAuthError} error Fallo a devolver.
 * @return {void}
 */
function sendOAuthError(res: Response, error: OAuthError): void {
  noStore(res);
  if (error.code === "invalid_client") {
    res.set("WWW-Authenticate", "Basic realm=\"secretaria\"");
  }
  res.status(error.status).json({
    error: error.code,
    error_description: error.message,
  });
}

/**
 * @param {string} redirectUri URI ya validada contra el cliente.
 * @param {Record<string, string | null>} params Parametros a anadir.
 * @return {string} URI de redireccion completa.
 */
function buildRedirect(redirectUri: string,
  params: Record<string, string | null>): string {
  const url = new URL(redirectUri);
  for (const [key, value] of Object.entries(params)) {
    if (value !== null) url.searchParams.set(key, value);
  }
  return url.toString();
}

/**
 * @param {string} clientId Identificador del cliente.
 * @return {Promise<ClientRecord | null>} Cliente registrado, si existe.
 */
async function loadClient(clientId: string): Promise<ClientRecord | null> {
  const snapshot = await admin.firestore()
    .collection(OAUTH_CLIENTS_COLLECTION).doc(clientId).get();
  if (!snapshot.exists) return null;
  const redirectUris = snapshot.get("redirectUris");
  return {
    clientId,
    clientName: str(snapshot.get("clientName")) ?? "MCP client",
    redirectUris: Array.isArray(redirectUris) ?
      redirectUris.filter((u): u is string => typeof u === "string") : [],
    authMethod: str(snapshot.get("authMethod")) ?? "none",
    secretHash: str(snapshot.get("secretHash")),
  };
}

/**
 * Cuenta un registro contra el tope diario. Va en transaccion para que el
 * limite aguante registros simultaneos.
 * @return {Promise<boolean>} False si el dia ya esta agotado.
 */
async function consumeRegistrationBudget(): Promise<boolean> {
  const db = admin.firestore();
  const day = new Date().toISOString().slice(0, 10);
  const ref = db.collection(OAUTH_STATE_COLLECTION).doc(`registrations-${day}`);
  return db.runTransaction(async (transaction) => {
    const count = (await transaction.get(ref)).get("count");
    const used = typeof count === "number" ? count : 0;
    if (used >= DAILY_REGISTRATION_LIMIT) return false;
    transaction.set(ref, { count: used + 1 });
    return true;
  });
}

/**
 * @return {Record<string, unknown>} Metadatos del recurso protegido (RFC 9728).
 */
function protectedResourceMetadata(): Record<string, unknown> {
  return {
    resource: MCP_URL,
    authorization_servers: [BASE_URL],
    bearer_methods_supported: ["header"],
    resource_name: "Secretaria",
  };
}

/**
 * @return {Record<string, unknown>} Metadatos del servidor (RFC 8414).
 */
function authorizationServerMetadata(): Record<string, unknown> {
  return {
    issuer: BASE_URL,
    authorization_endpoint: `${BASE_URL}/oauth/authorize`,
    token_endpoint: `${BASE_URL}/oauth/token`,
    registration_endpoint: `${BASE_URL}/oauth/register`,
    response_types_supported: ["code"],
    grant_types_supported: ["authorization_code", "refresh_token"],
    code_challenge_methods_supported: ["S256"],
    token_endpoint_auth_methods_supported: AUTH_METHODS,
  };
}

/**
 * Registro dinamico de clientes (RFC 7591).
 * @param {Request} req Peticion.
 * @param {Response} res Respuesta.
 * @return {Promise<void>}
 */
async function handleRegister(req: Request, res: Response): Promise<void> {
  const body = typeof req.body === "object" && req.body !== null ?
    req.body as Record<string, unknown> : {};
  const uris = body.redirect_uris;
  if (!Array.isArray(uris) || uris.length === 0 ||
    uris.length > MAX_REDIRECT_URIS ||
    !uris.every((u) => typeof u === "string" && isAllowedRedirectUri(u))) {
    throw new OAuthError(400, "invalid_redirect_uri",
      "redirect_uris must be 1 to 5 https URLs (or http loopback).");
  }
  const method = body.token_endpoint_auth_method ?? "client_secret_basic";
  if (typeof method !== "string" || !AUTH_METHODS.includes(method)) {
    throw new OAuthError(400, "invalid_client_metadata",
      `token_endpoint_auth_method must be one of ${AUTH_METHODS.join(", ")}.`);
  }
  const rawName = typeof body.client_name === "string" ?
    body.client_name.trim() : "";
  const clientName = (rawName || "MCP client")
    .slice(0, MAX_CLIENT_NAME_LENGTH);
  if (!await consumeRegistrationBudget()) {
    throw new OAuthError(429, "temporarily_unavailable",
      "Too many client registrations today.");
  }
  const clientId = randomBytes(16).toString("hex");
  const secret = method === "none" ? null : randomSecret("sec_cs_");
  await admin.firestore().collection(OAUTH_CLIENTS_COLLECTION)
    .doc(clientId).set({
      clientName,
      redirectUris: uris,
      authMethod: method,
      secretHash: secret === null ? null : hashMcpToken(secret),
      createdAt: Timestamp.now(),
    });
  noStore(res);
  res.status(201).json({
    client_id: clientId,
    ...(secret === null ? {} : { client_secret: secret }),
    client_id_issued_at: Math.floor(Date.now() / 1000),
    client_name: clientName,
    redirect_uris: uris,
    token_endpoint_auth_method: method,
    grant_types: ["authorization_code", "refresh_token"],
    response_types: ["code"],
  });
}

type AuthorizeCheck =
  | { kind: "fatal"; message: string }
  | { kind: "redirect-error"; client: ClientRecord; redirectUri: string;
    state: string | null; error: string; description: string }
  | { kind: "ok"; client: ClientRecord; params: AuthorizeParams };

/**
 * Valida una peticion de autorizacion. Mientras no se haya comprobado el
 * `client_id` y la `redirect_uri` EXACTA, un error nunca se redirige: seria un
 * redirector abierto.
 * @param {Record<string, unknown>} input Parametros (query o cuerpo).
 * @return {Promise<AuthorizeCheck>} Resultado de la validacion.
 */
async function checkAuthorizeRequest(input: Record<string, unknown>):
  Promise<AuthorizeCheck> {
  const clientId = str(input.client_id);
  const redirectUri = str(input.redirect_uri);
  const client = clientId === null ? null : await loadClient(clientId);
  if (client === null || redirectUri === null ||
    !client.redirectUris.includes(redirectUri)) {
    return { kind: "fatal", message: "Unknown client or redirect URI." };
  }
  const state = str(input.state);
  const fail = (error: string, description: string): AuthorizeCheck => ({
    kind: "redirect-error", client, redirectUri, state, error, description,
  });
  if (input.response_type !== undefined && input.response_type !== "code") {
    return fail("unsupported_response_type", "Only response_type=code.");
  }
  const codeChallenge = str(input.code_challenge);
  if (codeChallenge === null || !PKCE_CHALLENGE.test(codeChallenge) ||
    (input.code_challenge_method !== undefined &&
      input.code_challenge_method !== "S256")) {
    return fail("invalid_request", "PKCE with code_challenge_method=S256 " +
      "is required.");
  }
  const resource = str(input.resource);
  if (resource !== null && resource !== MCP_URL) {
    return fail("invalid_target", "Unknown resource.");
  }
  return {
    kind: "ok",
    client,
    params: { clientId: client.clientId, redirectUri, codeChallenge,
      state, resource },
  };
}

/**
 * @param {unknown} value Valor a incrustar en HTML dentro de un `<script>`.
 * @return {string} JSON seguro: `<` escapado para que no cierre la etiqueta.
 */
function jsonForScript(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c")
    .replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
}

/**
 * Pagina de consentimiento. Todo dato dinamico viaja como JSON y se pinta con
 * `textContent`: `client_name` lo elige quien registra el cliente, no es de
 * fiar. Se muestra tambien el host de redireccion, que si lo es.
 * @param {ClientRecord} client Cliente que pide el acceso.
 * @param {AuthorizeParams} params Parametros ya validados.
 * @param {string} nonce Nonce de CSP.
 * @return {string} HTML.
 */
function consentPage(client: ClientRecord, params: AuthorizeParams,
  nonce: string): string {
  const projectId = process.env.GCLOUD_PROJECT ?? "";
  const data = {
    clientName: client.clientName,
    redirectHost: new URL(params.redirectUri).host,
    firebase: {
      apiKey: process.env.SECRETARIA_WEB_API_KEY ?? "",
      authDomain: `${projectId}.firebaseapp.com`,
      projectId,
    },
    params: {
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      code_challenge: params.codeChallenge,
      state: params.state,
      resource: params.resource,
    },
  };
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Authorize access</title>
<style nonce="${nonce}">
  body { font-family: system-ui, sans-serif; margin: 0; background: #f6f4ef;
    color: #1c1b18; display: flex; min-height: 100vh; align-items: center;
    justify-content: center; }
  main { background: #fff; border-radius: 16px; padding: 28px; width: 100%;
    max-width: 380px; margin: 16px; box-shadow: 0 2px 12px #0001; }
  h1 { font-size: 20px; margin: 0 0 12px; }
  p { line-height: 1.45; margin: 8px 0; }
  .host { font-family: ui-monospace, monospace; word-break: break-all; }
  button { width: 100%; font: inherit; padding: 12px; border-radius: 10px;
    border: 1px solid #c9c4b8; background: #fff; margin-top: 10px;
    cursor: pointer; }
  button.primary { background: #1c1b18; color: #fff; border-color: #1c1b18; }
  button:disabled { opacity: .5; cursor: default; }
  #error { color: #b3261e; min-height: 1.4em; }
</style>
</head>
<body>
<main>
  <h1>Allow access to Secretaria?</h1>
  <p><strong id="client"></strong> wants to create and read your reminders.</p>
  <p>After you accept, it returns to <span class="host" id="host"></span>.
  Only continue if you recognise it.</p>
  <button class="primary" id="allow">Sign in with Google and allow</button>
  <button id="deny">Cancel</button>
  <p id="error" role="alert"></p>
</main>
<script type="application/json" id="data">${jsonForScript(data)}</script>
<script nonce="${nonce}" src="https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js"></script>
<script nonce="${nonce}" src="https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js"></script>
<script nonce="${nonce}">
  const data = JSON.parse(document.getElementById("data").textContent);
  document.getElementById("client").textContent = data.clientName;
  document.getElementById("host").textContent = data.redirectHost;
  const errorBox = document.getElementById("error");
  const buttons = [document.getElementById("allow"),
    document.getElementById("deny")];
  firebase.initializeApp(data.firebase);

  async function decide(decision) {
    errorBox.textContent = "";
    buttons.forEach((b) => { b.disabled = true; });
    try {
      const body = Object.assign({ decision }, data.params);
      if (decision === "allow") {
        const provider = new firebase.auth.GoogleAuthProvider();
        provider.setCustomParameters({ prompt: "select_account" });
        const result = await firebase.auth().signInWithPopup(provider);
        body.idToken = await result.user.getIdToken(true);
      }
      const response = await fetch(location.pathname, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const json = await response.json();
      if (json.redirect) {
        location.assign(json.redirect);
        return;
      }
      errorBox.textContent = json.error_description || json.error || "Error";
    } catch (error) {
      errorBox.textContent = (error && error.message) || String(error);
    }
    buttons.forEach((b) => { b.disabled = false; });
  }
  document.getElementById("allow").addEventListener("click",
    () => decide("allow"));
  document.getElementById("deny").addEventListener("click",
    () => decide("deny"));
</script>
</body>
</html>`;
}

/**
 * @param {Response} res Respuesta.
 * @param {number} status Codigo HTTP.
 * @param {string} message Mensaje para la persona.
 * @return {void}
 */
function sendHtmlError(res: Response, status: number, message: string): void {
  res.status(status).set("Content-Type", "text/html; charset=utf-8")
    .set("Content-Security-Policy", "default-src 'none'; " +
      "frame-ancestors 'none'")
    .send("<!doctype html><meta charset=\"utf-8\"><title>Error</title>" +
      `<p>${message}</p>`);
}

/**
 * GET muestra el consentimiento; POST lo resuelve y devuelve a donde redirigir.
 * @param {Request} req Peticion.
 * @param {Response} res Respuesta.
 * @return {Promise<void>}
 */
async function handleAuthorize(req: Request, res: Response): Promise<void> {
  if (req.method === "GET") {
    const check = await checkAuthorizeRequest(
      req.query as Record<string, unknown>);
    if (check.kind === "fatal") {
      sendHtmlError(res, 400, check.message);
      return;
    }
    if (check.kind === "redirect-error") {
      res.redirect(302, buildRedirect(check.redirectUri, {
        error: check.error,
        error_description: check.description,
        state: check.state,
      }));
      return;
    }
    const nonce = randomBytes(16).toString("base64");
    const projectId = process.env.GCLOUD_PROJECT ?? "";
    res.status(200).set({
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": [
        "default-src 'none'",
        `script-src 'nonce-${nonce}' https://www.gstatic.com ` +
          "https://apis.google.com",
        `style-src 'nonce-${nonce}'`,
        "connect-src 'self' https://identitytoolkit.googleapis.com " +
          "https://securetoken.googleapis.com https://www.googleapis.com",
        `frame-src https://${projectId}.firebaseapp.com ` +
          "https://accounts.google.com https://content.googleapis.com",
        "img-src 'self' data:",
        "base-uri 'none'",
        "form-action 'none'",
        "frame-ancestors 'none'",
      ].join("; "),
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
    }).send(consentPage(check.client, check.params, nonce));
    return;
  }
  if (req.method !== "POST") {
    res.set("Allow", "GET, POST").status(405).send("Method Not Allowed");
    return;
  }
  const body = typeof req.body === "object" && req.body !== null ?
    req.body as Record<string, unknown> : {};
  const check = await checkAuthorizeRequest(body);
  noStore(res);
  if (check.kind === "fatal") {
    throw new OAuthError(400, "invalid_request", check.message);
  }
  if (check.kind === "redirect-error") {
    res.json({ redirect: buildRedirect(check.redirectUri, {
      error: check.error,
      error_description: check.description,
      state: check.state,
    }) });
    return;
  }
  const { client, params } = check;
  if (body.decision === "deny") {
    res.json({ redirect: buildRedirect(params.redirectUri, {
      error: "access_denied", state: params.state }) });
    return;
  }
  const idToken = str(body.idToken);
  if (body.decision !== "allow" || idToken === null) {
    throw new OAuthError(400, "invalid_request", "Missing decision or " +
      "sign-in.");
  }
  let decoded: admin.auth.DecodedIdToken;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch {
    throw new OAuthError(401, "access_denied", "Invalid sign-in.");
  }
  // Mismo criterio que las fotos: una cuenta anonima no tiene datos que
  // merezca la pena exponer a una IA, y no hay forma de recuperarla.
  if (decoded.firebase.sign_in_provider === "anonymous") {
    throw new OAuthError(403, "access_denied",
      "Sign in with a real account, not as a guest.");
  }
  const code = randomSecret("sec_code_");
  await admin.firestore().collection(OAUTH_CODES_COLLECTION)
    .doc(hashMcpToken(code)).set({
      uid: decoded.uid,
      clientId: client.clientId,
      redirectUri: params.redirectUri,
      codeChallenge: params.codeChallenge,
      resource: params.resource ?? MCP_URL,
      expiresAt: Timestamp.fromMillis(Date.now() + CODE_TTL_MS),
    });
  res.json({ redirect: buildRedirect(params.redirectUri, {
    code, state: params.state }) });
}

/**
 * @param {Request} req Peticion al endpoint de tokens.
 * @return {Promise<ClientRecord>} Cliente autenticado.
 */
async function authenticateClient(req: Request): Promise<ClientRecord> {
  const body = req.body as Record<string, unknown>;
  let clientId = str(body.client_id);
  let secret = str(body.client_secret);
  const basic = /^Basic (\S+)$/.exec(req.get("authorization") ?? "");
  if (basic) {
    const decoded = Buffer.from(basic[1], "base64").toString("utf8");
    const split = decoded.indexOf(":");
    if (split > 0) {
      try {
        clientId = decodeURIComponent(decoded.slice(0, split));
        secret = decodeURIComponent(decoded.slice(split + 1));
      } catch {
        throw new OAuthError(401, "invalid_client", "Bad credentials.");
      }
    }
  }
  const client = clientId === null ? null : await loadClient(clientId);
  if (client === null) {
    throw new OAuthError(401, "invalid_client", "Unknown client.");
  }
  if (client.authMethod !== "none") {
    if (secret === null || client.secretHash === null ||
      !sameHash(hashMcpToken(secret), client.secretHash)) {
      throw new OAuthError(401, "invalid_client", "Bad client secret.");
    }
  }
  return client;
}

/**
 * Emite un par access/refresh. Los tokens de acceso viven en `mcpTokens`, la
 * misma coleccion que los personales, para que `resolveToken` no distinga.
 * @param {string} uid Usuario.
 * @param {string} clientId Cliente al que se emiten.
 * @return {Promise<Record<string, unknown>>} Cuerpo de la respuesta de token.
 */
async function issueTokens(uid: string, clientId: string):
  Promise<Record<string, unknown>> {
  const db = admin.firestore();
  const access = randomSecret("sec_at_");
  const refresh = randomSecret("sec_rt_");
  const now = Date.now();
  const batch = db.batch();
  batch.set(db.collection(MCP_TOKENS_COLLECTION).doc(hashMcpToken(access)), {
    uid,
    kind: "oauth",
    clientId,
    revoked: false,
    createdAt: Timestamp.fromMillis(now),
    expiresAt: Timestamp.fromMillis(now + ACCESS_TOKEN_TTL_MS),
  });
  batch.set(db.collection(OAUTH_REFRESH_COLLECTION)
    .doc(hashMcpToken(refresh)), {
    uid,
    clientId,
    createdAt: Timestamp.fromMillis(now),
    expiresAt: Timestamp.fromMillis(now + REFRESH_TOKEN_TTL_MS),
  });
  await batch.commit();
  return {
    access_token: access,
    token_type: "Bearer",
    expires_in: ACCESS_TOKEN_TTL_MS / 1000,
    refresh_token: refresh,
  };
}

/**
 * Consume un documento de un solo uso: lo lee y lo borra en una transaccion,
 * de modo que dos canjes simultaneos no pueden ganar los dos.
 * @param {string} collection Coleccion.
 * @param {string} secret Codigo o token en claro.
 * @param {string} clientId Cliente que lo presenta; si no es el del documento,
 *   se destruye igualmente y no sirve.
 * @return {Promise<Record<string, unknown> | null>} Datos, o null si no sirve.
 */
async function consumeOnce(collection: string, secret: string,
  clientId: string): Promise<Record<string, unknown> | null> {
  const db = admin.firestore();
  const ref = db.collection(collection).doc(hashMcpToken(secret));
  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) return null;
    // Se destruye tambien si lo presenta otro cliente: un codigo o refresh
    // token que alguien intenta usar a nombre de un tercero ya esta comprometido.
    transaction.delete(ref);
    if (snapshot.get("clientId") !== clientId) return null;
    const expiresAt = snapshot.get("expiresAt");
    if (!(expiresAt instanceof Timestamp) ||
      expiresAt.toMillis() <= Date.now()) {
      return null;
    }
    return snapshot.data() ?? null;
  });
}

/**
 * @param {Request} req Peticion al endpoint de tokens.
 * @param {Response} res Respuesta.
 * @return {Promise<void>}
 */
async function handleToken(req: Request, res: Response): Promise<void> {
  if (typeof req.body !== "object" || req.body === null) {
    throw new OAuthError(400, "invalid_request", "Expected a form body.");
  }
  const body = req.body as Record<string, unknown>;
  const client = await authenticateClient(req);
  const grant = body.grant_type;
  const resource = str(body.resource);
  if (resource !== null && resource !== MCP_URL) {
    throw new OAuthError(400, "invalid_target", "Unknown resource.");
  }
  let uid: string;
  if (grant === "authorization_code") {
    const code = str(body.code);
    const verifier = str(body.code_verifier);
    if (code === null || verifier === null || !PKCE_VERIFIER.test(verifier)) {
      throw new OAuthError(400, "invalid_request",
        "code and a valid code_verifier are required.");
    }
    const record = await consumeOnce(OAUTH_CODES_COLLECTION, code,
      client.clientId);
    const challenge = createHash("sha256").update(verifier)
      .digest("base64url");
    if (record === null ||
      record.redirectUri !== str(body.redirect_uri) ||
      typeof record.codeChallenge !== "string" ||
      !sameHash(challenge, record.codeChallenge) ||
      typeof record.uid !== "string") {
      throw new OAuthError(400, "invalid_grant", "Invalid or expired code.");
    }
    uid = record.uid;
  } else if (grant === "refresh_token") {
    const refresh = str(body.refresh_token);
    const record = refresh === null ? null :
      await consumeOnce(OAUTH_REFRESH_COLLECTION, refresh, client.clientId);
    if (record === null || typeof record.uid !== "string") {
      throw new OAuthError(400, "invalid_grant",
        "Invalid or expired refresh token.");
    }
    uid = record.uid;
  } else {
    throw new OAuthError(400, "unsupported_grant_type",
      "Use authorization_code or refresh_token.");
  }
  noStore(res);
  res.json(await issueTokens(uid, client.clientId));
}

/**
 * Servidor de autorizacion del MCP. Un solo punto de entrada: Hosting enruta
 * `/oauth/**` y `/.well-known/**` aqui. Se enruta por el FINAL de la ruta para
 * que tambien funcione bajo el prefijo que le pone el emulador.
 */
export const oauth = onRequest(
  { region: REGION, cors: false, maxInstances: 3, invoker: "public" },
  async (req, res) => {
    const path = req.path.replace(/\/+$/, "");
    try {
      if (req.method === "OPTIONS") {
        allowCors(res);
        res.status(204).send();
        return;
      }
      if (/\/\.well-known\/oauth-protected-resource(\/mcp)?$/.test(path)) {
        allowCors(res);
        res.json(protectedResourceMetadata());
      } else if (/\/\.well-known\/oauth-authorization-server$/.test(path)) {
        allowCors(res);
        res.json(authorizationServerMetadata());
      } else if (path.endsWith("/oauth/register") ||
        path.endsWith("/oauth/token")) {
        allowCors(res);
        if (req.method !== "POST") {
          res.set("Allow", "POST, OPTIONS").status(405)
            .send("Method Not Allowed");
          return;
        }
        if (path.endsWith("/oauth/register")) {
          await handleRegister(req, res);
        } else {
          await handleToken(req, res);
        }
      } else if (path.endsWith("/oauth/authorize")) {
        await handleAuthorize(req, res);
      } else {
        res.status(404).send("Not Found");
      }
    } catch (error) {
      if (error instanceof OAuthError) {
        sendOAuthError(res, error);
        return;
      }
      logger.error("oauth request failed", {
        path,
        reason: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
      });
      sendOAuthError(res, new OAuthError(500, "server_error",
        "Internal error."));
    }
  },
);
