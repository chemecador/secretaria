/**
 * Constantes compartidas por el servidor MCP (`mcp.ts`) y su servidor de
 * autorizacion OAuth (`oauth.ts`).
 */

export const REGION = "europe-west1";

/**
 * URL canonica publica. Es FIJA a proposito: detras de un rewrite de Hosting
 * la cabecera `Host` es la de la funcion, y los clientes comprueban que el
 * `resource` de los metadatos sea exactamente la URL que se les dio.
 */
export const BASE_URL = "https://kotlin-secretaria.web.app";
export const MCP_URL = `${BASE_URL}/mcp`;
export const PROTECTED_RESOURCE_METADATA_URL =
  `${BASE_URL}/.well-known/oauth-protected-resource`;

/** Tokens de acceso: personales (fase 1) y OAuth (fase 2). El id es el hash. */
export const MCP_TOKENS_COLLECTION = "mcpTokens";
export const MCP_USAGE_COLLECTION = "mcpUsage";
export const OAUTH_CLIENTS_COLLECTION = "oauthClients";
export const OAUTH_CODES_COLLECTION = "oauthCodes";
export const OAUTH_REFRESH_COLLECTION = "oauthRefresh";
export const OAUTH_STATE_COLLECTION = "oauthState";
