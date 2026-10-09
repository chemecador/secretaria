import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";
import { Timestamp } from "firebase-admin/firestore";
import { createHash } from "crypto";

const REGION = "europe-west1";
const MCP_TOKENS_COLLECTION = "mcpTokens";
const USERS_COLLECTION = "users";
const REMINDERS_COLLECTION = "reminders";

const SERVER_INFO = { name: "secretaria", version: "1.0.0" };
const SUPPORTED_PROTOCOL_VERSIONS = ["2025-06-18", "2025-03-26", "2024-11-05"];

const MAX_TEXT_LENGTH = 500;
const MAX_DESCRIPTION_LENGTH = 2000;
/** Tope de recordatorios creados por token y dia (UTC): frena a una IA en bucle. */
const DAILY_CREATE_LIMIT = 100;

const DEFAULT_LIST_LIMIT = 50;
const MAX_LIST_LIMIT = 100;

const CREATE_REMINDER_TOOL = {
  name: "create_reminder",
  description:
    "Creates a reminder in the user's Secretaria app. The reminder is " +
    "appended at the end of their pending list. A due date is optional; " +
    "when given, the user is notified at that local time on their own " +
    "device, wherever they are. Resolve relative dates such as " +
    "\"tomorrow\" to an absolute date before calling.",
  annotations: {
    readOnlyHint: false,
    destructiveHint: false,
    idempotentHint: false,
    openWorldHint: false,
  },
  inputSchema: {
    type: "object",
    properties: {
      text: {
        type: "string",
        description: "Short reminder text, one line.",
        maxLength: MAX_TEXT_LENGTH,
      },
      description: {
        type: "string",
        description: "Optional longer detail shown under the text.",
        maxLength: MAX_DESCRIPTION_LENGTH,
      },
      dueDate: {
        type: "string",
        description: "Optional due date as yyyy-MM-dd, in the user's " +
          "local calendar.",
        pattern: "^\\d{4}-\\d{2}-\\d{2}$",
      },
      dueTime: {
        type: "string",
        description: "Optional due time as HH:mm (24h). Requires dueDate. " +
          "Omit it for an all-day reminder.",
        pattern: "^\\d{2}:\\d{2}$",
      },
    },
    required: ["text"],
    additionalProperties: false,
  },
};

const LIST_REMINDERS_TOOL = {
  name: "list_reminders",
  description:
    "Lists the user's reminders in their Secretaria app, including the " +
    "ones other people shared with them. Pending reminders come in the " +
    "order the user arranged them; completed ones, most recent first. " +
    "dueDate and dueTime are the user's local calendar and clock.",
  annotations: {
    readOnlyHint: true,
    destructiveHint: false,
    idempotentHint: true,
    openWorldHint: false,
  },
  inputSchema: {
    type: "object",
    properties: {
      status: {
        type: "string",
        enum: ["pending", "completed", "all"],
        description: "Which reminders to return. Defaults to pending.",
      },
      limit: {
        type: "integer",
        minimum: 1,
        maximum: MAX_LIST_LIMIT,
        description: `Maximum number of reminders. Defaults to ${
          DEFAULT_LIST_LIMIT}.`,
      },
    },
    additionalProperties: false,
  },
};

type JsonRpcId = string | number | null;

interface JsonRpcRequest {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: Record<string, unknown>;
}

/** Error de validacion que se devuelve al modelo como resultado, no como fallo. */
export class ToolInputError extends Error {}

/** Fallo de protocolo JSON-RPC (metodo desconocido, parametros invalidos...). */
class RpcError extends Error {
  /**
   * @param {number} code Codigo JSON-RPC.
   * @param {string} message Descripcion del fallo.
   */
  constructor(readonly code: number, message: string) {
    super(message);
  }
}

/**
 * @param {string} token Token en claro.
 * @return {string} Hash con el que se guarda en `mcpTokens`.
 */
export function hashMcpToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

/**
 * @param {Date} now Instante.
 * @return {string} Dia UTC como yyyy-MM-dd.
 */
function utcDay(now: Date): string {
  return now.toISOString().slice(0, 10);
}

/**
 * @param {string} value Fecha candidata.
 * @return {boolean} Si existe en el calendario.
 */
export function isRealDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const [year, month, day] = [match[1], match[2], match[3]].map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day;
}

/**
 * @param {string} value Hora candidata.
 * @return {boolean} Si es una hora HH:mm valida.
 */
function isRealTime(value: string): boolean {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  return match !== null && Number(match[1]) < 24 && Number(match[2]) < 60;
}

/**
 * @param {Record<string, unknown>} args Argumentos de la llamada.
 * @param {string} key Nombre del argumento.
 * @param {number} maxLength Longitud maxima tras recortar.
 * @return {string | null} Texto recortado, o null si falta o esta en blanco.
 */
function optionalString(args: Record<string, unknown>, key: string,
  maxLength: number): string | null {
  const value = args[key];
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") {
    throw new ToolInputError(`${key} must be a string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length > maxLength) {
    throw new ToolInputError(`${key} is longer than ${maxLength} characters.`);
  }
  // Igual que RemindersViewModel: en blanco se persiste como null.
  return trimmed.length === 0 ? null : trimmed;
}

interface ReminderInput {
  text: string;
  description: string | null;
  dueDate: string | null;
  dueTime: string | null;
}

/**
 * @param {Record<string, unknown>} args Argumentos de la llamada.
 * @return {ReminderInput} Datos validados.
 */
export function parseReminderInput(args: Record<string, unknown>): ReminderInput {
  const text = optionalString(args, "text", MAX_TEXT_LENGTH);
  if (text === null) throw new ToolInputError("text is required.");
  const description = optionalString(args, "description",
    MAX_DESCRIPTION_LENGTH);
  const dueDate = optionalString(args, "dueDate", 10);
  const dueTime = optionalString(args, "dueTime", 5);
  if (dueDate !== null && !isRealDate(dueDate)) {
    throw new ToolInputError("dueDate must be a real date as yyyy-MM-dd.");
  }
  if (dueTime !== null && !isRealTime(dueTime)) {
    throw new ToolInputError("dueTime must be a real time as HH:mm.");
  }
  if (dueTime !== null && dueDate === null) {
    throw new ToolInputError("dueTime requires dueDate.");
  }
  return { text, description, dueDate, dueTime };
}

/**
 * Cuenta una creacion contra el tope diario del token. Va en transaccion para
 * que dos llamadas simultaneas no se salten el limite.
 * @param {string} tokenHash Hash del token que hace la llamada.
 * @return {Promise<boolean>} False si el token ya agoto el dia.
 */
async function consumeDailyBudget(tokenHash: string): Promise<boolean> {
  const db = admin.firestore();
  const ref = db.collection(MCP_TOKENS_COLLECTION).doc(tokenHash);
  const today = utcDay(new Date());
  return db.runTransaction(async (transaction) => {
    const data = (await transaction.get(ref)).data() ?? {};
    const sameDay = data.usageDay === today;
    const used = sameDay && typeof data.usageCount === "number" ?
      data.usageCount : 0;
    if (used >= DAILY_CREATE_LIMIT) return false;
    transaction.update(ref, { usageDay: today, usageCount: used + 1 });
    return true;
  });
}

/**
 * Espejo de `createReminder` en los repositorios del cliente.
 * @param {string} uid Dueño del recordatorio.
 * @param {ReminderInput} input Datos ya validados.
 * @return {Promise<string>} Id del documento creado.
 */
async function createReminder(uid: string, input: ReminderInput):
  Promise<string> {
  const collection = admin.firestore()
    .collection(USERS_COLLECTION).doc(uid)
    .collection(REMINDERS_COLLECTION);
  const ref = collection.doc();
  // En transaccion para que dos creaciones simultaneas no reciban el mismo
  // `order`: la transaccion reintenta si alguien escribe entre lectura y set.
  await admin.firestore().runTransaction(async (transaction) => {
    const pending = await transaction.get(
      collection.where("completed", "==", false));
    let maxOrder = -1;
    for (const doc of pending.docs) {
      const order = doc.get("order");
      if (typeof order === "number" && order > maxOrder) maxOrder = order;
    }
    transaction.set(ref, {
      text: input.text,
      description: input.description,
      dueDate: input.dueDate,
      dueTime: input.dueTime,
      completed: false,
      completedAt: null,
      order: maxOrder + 1,
      date: Timestamp.now(),
      contributors: [uid],
    });
  });
  return ref.id;
}

type ReminderStatus = "pending" | "completed" | "all";

export interface ListInput {
  status: ReminderStatus;
  limit: number;
}

/**
 * @param {Record<string, unknown>} args Argumentos de la llamada.
 * @return {ListInput} Filtros validados.
 */
export function parseListInput(args: Record<string, unknown>): ListInput {
  const status = args.status ?? "pending";
  if (status !== "pending" && status !== "completed" && status !== "all") {
    throw new ToolInputError("status must be pending, completed or all.");
  }
  const limit = args.limit ?? DEFAULT_LIST_LIMIT;
  if (typeof limit !== "number" || !Number.isInteger(limit) ||
    limit < 1 || limit > MAX_LIST_LIMIT) {
    throw new ToolInputError(
      `limit must be an integer between 1 and ${MAX_LIST_LIMIT}.`);
  }
  return { status, limit };
}

interface ListedReminder {
  id: string;
  ownerId: string;
  text: string;
  description: string | null;
  dueDate: string | null;
  dueTime: string | null;
  completed: boolean;
  order: number;
  createdAtMs: number;
  completedAtMs: number;
}

/**
 * @param {FirebaseFirestore.DocumentSnapshot} doc Recordatorio.
 * @return {ListedReminder} Campos que se muestran, con valores por defecto.
 */
function toListed(doc: FirebaseFirestore.DocumentSnapshot): ListedReminder {
  const text = doc.get("text");
  const description = doc.get("description");
  const dueDate = doc.get("dueDate");
  const dueTime = doc.get("dueTime");
  const order = doc.get("order");
  const date = doc.get("date");
  const completedAt = doc.get("completedAt");
  return {
    id: doc.id,
    ownerId: doc.ref.parent.parent?.id ?? "",
    text: typeof text === "string" ? text : "",
    description: typeof description === "string" ? description : null,
    dueDate: typeof dueDate === "string" ? dueDate : null,
    dueTime: typeof dueTime === "string" ? dueTime : null,
    completed: doc.get("completed") === true,
    order: typeof order === "number" ? order : 0,
    createdAtMs: date instanceof Timestamp ? date.toMillis() : 0,
    completedAtMs: completedAt instanceof Timestamp ?
      completedAt.toMillis() : 0,
  };
}

/**
 * Espejo de `getReminders` del cliente: los propios por ruta (los anteriores
 * al reparto no tienen `contributors`) mas los compartidos por collection
 * group, sin duplicados.
 * @param {string} uid Dueño del token.
 * @param {ListInput} input Filtros ya validados.
 * @return {Promise<ListedReminder[]>} Recordatorios ordenados y recortados.
 */
async function listReminders(uid: string, input: ListInput):
  Promise<ListedReminder[]> {
  const db = admin.firestore();
  const [own, shared] = await Promise.all([
    db.collection(USERS_COLLECTION).doc(uid)
      .collection(REMINDERS_COLLECTION).get(),
    db.collectionGroup(REMINDERS_COLLECTION)
      .where("contributors", "array-contains", uid).get(),
  ]);
  const byKey = new Map<string, ListedReminder>();
  for (const doc of [...own.docs, ...shared.docs]) {
    const reminder = toListed(doc);
    byKey.set(`${reminder.ownerId}/${reminder.id}`, reminder);
  }
  const wanted = [...byKey.values()].filter((r) =>
    input.status === "all" || r.completed === (input.status === "completed"));
  // Mismo criterio que `pendingReminders`: los `order` pueden coincidir entre
  // dueños, asi que se desempata por fecha de creacion y por id.
  wanted.sort((a, b) => {
    if (a.completed !== b.completed) return a.completed ? 1 : -1;
    if (a.completed) return b.completedAtMs - a.completedAtMs;
    return a.order - b.order || a.createdAtMs - b.createdAtMs ||
      a.id.localeCompare(b.id);
  });
  return wanted.slice(0, input.limit);
}

/**
 * @param {string} uid Dueño del token.
 * @param {Record<string, unknown>} args Argumentos de la llamada.
 * @return {Promise<Record<string, unknown>>} Resultado MCP de la herramienta.
 */
async function callListTool(uid: string, args: Record<string, unknown>):
  Promise<Record<string, unknown>> {
  try {
    const reminders = await listReminders(uid, parseListInput(args));
    const rows = reminders.map((r) => ({
      id: r.id,
      text: r.text,
      description: r.description,
      dueDate: r.dueDate,
      dueTime: r.dueTime,
      completed: r.completed,
      shared: r.ownerId !== uid,
    }));
    return {
      content: [{
        type: "text",
        text: rows.length === 0 ? "No reminders." :
          JSON.stringify(rows, null, 1),
      }],
    };
  } catch (error) {
    if (!(error instanceof ToolInputError)) throw error;
    return {
      isError: true,
      content: [{ type: "text", text: error.message }],
    };
  }
}

/**
 * @param {string} uid Dueño del token.
 * @param {string} tokenHash Hash del token, para el tope diario.
 * @param {Record<string, unknown> | undefined} params Parametros de tools/call.
 * @return {Promise<Record<string, unknown>>} Resultado MCP de la herramienta.
 */
async function callTool(
  uid: string,
  tokenHash: string,
  params: Record<string, unknown> | undefined,
): Promise<Record<string, unknown>> {
  const rawArgs = params?.arguments;
  const args = typeof rawArgs === "object" && rawArgs !== null ?
    rawArgs as Record<string, unknown> : {};
  if (params?.name === LIST_REMINDERS_TOOL.name) {
    return callListTool(uid, args);
  }
  if (params?.name !== CREATE_REMINDER_TOOL.name) {
    throw new RpcError(-32602, `Unknown tool: ${String(params?.name)}`);
  }
  try {
    const input = parseReminderInput(args);
    if (!await consumeDailyBudget(tokenHash)) {
      throw new ToolInputError(
        `Daily limit of ${DAILY_CREATE_LIMIT} reminders reached.`);
    }
    const id = await createReminder(uid, input);
    const due = input.dueDate === null ? "no due date" :
      `due ${input.dueDate}${input.dueTime ? ` ${input.dueTime}` : ""}`;
    return {
      content: [{
        type: "text",
        text: `Reminder created (${due}): ${input.text} [id ${id}]`,
      }],
    };
  } catch (error) {
    if (!(error instanceof ToolInputError)) throw error;
    // Un error de la herramienta va como resultado para que el modelo lo vea
    // y pueda corregir la llamada.
    return {
      isError: true,
      content: [{ type: "text", text: error.message }],
    };
  }
}

/**
 * @param {string} uid Dueño del token.
 * @param {string} tokenHash Hash del token.
 * @param {JsonRpcRequest} request Mensaje JSON-RPC.
 * @return {Promise<Record<string, unknown> | undefined>} Respuesta, o
 *   undefined si era una notificacion.
 */
async function handleRpc(
  uid: string,
  tokenHash: string,
  request: JsonRpcRequest,
): Promise<Record<string, unknown> | undefined> {
  const { id, method, params } = request;
  // Sin `id` es una notificacion: no se responde.
  const isNotification = id === undefined;
  try {
    let result: Record<string, unknown>;
    switch (method) {
    case "initialize": {
      const requested = params?.protocolVersion;
      result = {
        protocolVersion: typeof requested === "string" &&
          SUPPORTED_PROTOCOL_VERSIONS.includes(requested) ?
          requested : SUPPORTED_PROTOCOL_VERSIONS[0],
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO,
      };
      break;
    }
    case "ping":
      result = {};
      break;
    case "tools/list":
      result = { tools: [CREATE_REMINDER_TOOL, LIST_REMINDERS_TOOL] };
      break;
    case "tools/call":
      result = await callTool(uid, tokenHash, params);
      break;
    default:
      if (isNotification) return undefined;
      throw new RpcError(-32601, `Method not found: ${String(method)}`);
    }
    return isNotification ? undefined : { jsonrpc: "2.0", id, result };
  } catch (error) {
    if (isNotification) return undefined;
    if (error instanceof RpcError) {
      return {
        jsonrpc: "2.0",
        id,
        error: { code: error.code, message: error.message },
      };
    }
    logger.error("mcp request failed", {
      method,
      reason: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
    });
    return {
      jsonrpc: "2.0",
      id,
      error: { code: -32603, message: "Internal error" },
    };
  }
}

/**
 * Devuelve el uid del dueño del token, o null si no existe o esta revocado.
 * @param {string | undefined} header Cabecera Authorization.
 * @return {Promise<{uid: string, tokenHash: string} | null>} Dueño del token.
 */
async function resolveToken(header: string | undefined):
  Promise<{ uid: string; tokenHash: string } | null> {
  const match = /^Bearer (\S+)$/.exec(header ?? "");
  if (!match) return null;
  const tokenHash = hashMcpToken(match[1]);
  const snapshot = await admin.firestore()
    .collection(MCP_TOKENS_COLLECTION).doc(tokenHash).get();
  const uid = snapshot.get("uid");
  if (!snapshot.exists || snapshot.get("revoked") === true ||
    typeof uid !== "string" || uid.length === 0) {
    return null;
  }
  return { uid, tokenHash };
}

/**
 * Servidor MCP (Streamable HTTP, sin estado) con una sola herramienta. El uid
 * sale siempre del token, nunca de los argumentos de la llamada.
 */
export const mcp = onRequest(
  { region: REGION, cors: false, maxInstances: 3, invoker: "public" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.set("Allow", "POST").status(405).send("Method Not Allowed");
      return;
    }
    const auth = await resolveToken(req.get("authorization"));
    if (!auth) {
      res.set("WWW-Authenticate", "Bearer").status(401).send("Unauthorized");
      return;
    }
    const body = req.body as JsonRpcRequest | JsonRpcRequest[] | undefined;
    const requests = Array.isArray(body) ? body : [body];
    if (requests.length === 0 || requests.some((r) =>
      typeof r !== "object" || r === null || r.jsonrpc !== "2.0")) {
      res.status(400).json({
        jsonrpc: "2.0",
        id: null,
        error: { code: -32600, message: "Invalid Request" },
      });
      return;
    }
    const responses = (await Promise.all(requests.map((r) =>
      handleRpc(auth.uid, auth.tokenHash, r as JsonRpcRequest))))
      .filter((r) => r !== undefined);
    if (responses.length === 0) {
      res.status(202).send();
      return;
    }
    res.json(Array.isArray(body) ? responses : responses[0]);
  },
);
