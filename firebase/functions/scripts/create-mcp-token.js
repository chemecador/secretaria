/*
 * Crea un token personal para el servidor MCP y lo imprime UNA vez.
 * Solo se guarda su hash en `mcpTokens/{sha256}`; no se puede recuperar.
 *
 * Uso:
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> \
 *     node scripts/create-mcp-token.js <uid> <projectId>
 */
const { randomBytes, createHash } = require("crypto");
const admin = require("firebase-admin");

const [uid, projectId] = process.argv.slice(2);
if (!uid || !projectId) {
  console.error("Uso: node scripts/create-mcp-token.js <uid> <projectId>");
  process.exit(1);
}

admin.initializeApp({ projectId });

(async () => {
  const token = `sec_${randomBytes(32).toString("base64url")}`;
  const hash = createHash("sha256").update(token).digest("hex");
  await admin.firestore().collection("mcpTokens").doc(hash).set({
    uid,
    revoked: false,
    createdAt: admin.firestore.Timestamp.now(),
  });
  console.log(token);
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
