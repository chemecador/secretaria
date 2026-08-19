import {
  onDocumentCreated,
  onDocumentCreatedWithAuthContext,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();
const USERS_COLLECTION = "users";
const FCM_TOKENS_COLLECTION = "fcm_tokens";
const NOTES_LIST_COLLECTION = "noteslist";
const NOTES_COLLECTION = "notes";
const REMINDERS_COLLECTION = "reminders";
const CHANNEL_LIST_SHARED = "list_shared";
const CHANNEL_REMINDER_SHARED = "reminder_shared";
const CHANNEL_REMINDER_DUE = "reminder_due";
const CHANNEL_FRIEND_REQUESTS = "friend_requests";
const CLICK_ACTION_OPEN_LIST = "com.chemecador.secretaria.OPEN_LIST";
const CLICK_ACTION_OPEN_REMINDERS =
  "com.chemecador.secretaria.OPEN_REMINDERS";

/** Zona horaria usada cuando el dispositivo no ha registrado la suya todavia. */
const DEFAULT_TIME_ZONE = "Europe/Madrid";

/**
 * Textos de las notificaciones. El servidor compone el cuerpo del aviso, asi que
 * necesita saber el idioma del destinatario: viene del campo `language` del
 * documento de token FCM, que escribe la app. Un idioma sin traduccion cae a
 * ingles, que es tambien el locale por defecto de la app.
 */
type NotificationTexts = {
  listSharedTitle: string;
  listSharedBody: (creator: string, listName: string) => string;
  reminderSharedTitle: string;
  reminderDueTitle: string;
  friendRequestTitle: string;
  friendRequestBody: (senderName: string) => string;
  sharedListNoteTitle: (listName: string) => string;
  untitledReminder: string;
  untitledNote: string;
  unnamedList: string;
};

const NOTIFICATION_TEXTS: Record<string, NotificationTexts> = {
  en: {
    listSharedTitle: "New list available",
    listSharedBody: (creator: string, listName: string) =>
      creator ?
        `${creator} shared the list "${listName}" with you.` :
        `The list "${listName}" was shared with you.`,
    reminderSharedTitle: "New shared reminder",
    reminderDueTitle: "Reminder",
    friendRequestTitle: "New friend request",
    friendRequestBody: (senderName: string) =>
      `${senderName} sent you a friend request.`,
    sharedListNoteTitle: (listName: string) => `New note in ${listName}`,
    untitledReminder: "Reminder with no text",
    untitledNote: "Untitled note",
    unnamedList: "shared list",
  },
  es: {
    listSharedTitle: "Nueva lista disponible",
    listSharedBody: (creator: string, listName: string) =>
      creator ?
        `El usuario ${creator} te ha compartido la lista "${listName}".` :
        `Te han compartido la lista "${listName}".`,
    reminderSharedTitle: "Nuevo recordatorio compartido",
    reminderDueTitle: "Recordatorio",
    friendRequestTitle: "Nueva solicitud de amistad",
    friendRequestBody: (senderName: string) =>
      `${senderName} te ha enviado una solicitud de amistad.`,
    sharedListNoteTitle: (listName: string) => `Nueva nota en ${listName}`,
    untitledReminder: "Recordatorio sin texto",
    untitledNote: "Nota sin titulo",
    unnamedList: "lista compartida",
  },
};

/** Idioma usado cuando el del dispositivo no esta traducido. */
const DEFAULT_LANGUAGE = "en";
/**
 * Idioma de los tokens registrados ANTES de que existiera el campo `language`.
 * La app era solo en espanol, asi que esos dispositivos son de usuarios que hoy
 * reciben los avisos en espanol y no deben cambiar de idioma solos al desplegar.
 * Se puede quitar cuando ya no queden tokens sin `language`.
 */
const LEGACY_LANGUAGE = "es";
/**
 * Hora local a la que se avisa de un recordatorio sin hora ("todo el dia").
 * Punto unico de cambio si algun dia se hace configurable por usuario.
 */
const ALL_DAY_DUE_TIME = "09:00";
/**
 * Un aviso que llega con mas de una hora de retraso ya no sirve: se descarta en
 * vez de enviarse. Tambien evita una rafaga de avisos rancios en el primer
 * despliegue, cuando la ventana se llena de recordatorios ya vencidos.
 */
const MAX_LATE_MS = 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_BATCH_WRITES = 400;

type PushPayload = {
  title: string;
  body: string;
  channelId: string;
  type: string;
  tag: string;
  clickAction?: string;
  data?: Record<string, string>;
};

/** Tokens de un usuario mas la zona horaria de su dispositivo mas reciente. */
type UserTokens = {
  tokens: string[];
  docs: admin.firestore.QueryDocumentSnapshot[];
  timeZoneId: string;
  texts: NotificationTexts;
};

type DueNotificationMark = {
  ref: admin.firestore.DocumentReference;
  userId: string;
  signature: string;
};

export const onListShared = onDocumentUpdated(
  "users/{userId}/noteslist/{listId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const oldContributors: string[] = directContributorsForNotification(before);
    const newContributors: string[] = directContributorsForNotification(after);
    const ownerId = event.params.userId;
    const added = newContributors.filter(
      (uid) => !oldContributors.includes(uid) && uid !== ownerId,
    );
    if (added.length === 0) return;

    const creator: string = after.creator ?? "";
    const listName: string = after.name ?? "";

    await Promise.all(
      added.map(async (uid) => {
        await sendPushToUser(uid, (texts) => ({
          title: texts.listSharedTitle,
          body: texts.listSharedBody(creator, listName),
          channelId: CHANNEL_LIST_SHARED,
          type: "list_shared",
          tag: `list_shared_${event.params.listId}`,
          clickAction: CLICK_ACTION_OPEN_LIST,
          data: openListData(
            event.params.userId,
            event.params.listId,
            listName,
            Boolean(after.ordered),
            Boolean(after.isGroup),
          ),
        }));
      }),
    );
  },
);

/**
 * Un recordatorio compartido tiene un unico array `contributors`: no hay grupos ni
 * `directContributors` como en las listas, asi que basta comparar el antes y el despues.
 */
export const onReminderShared = onDocumentUpdated(
  "users/{userId}/reminders/{reminderId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const ownerId = event.params.userId;
    const oldContributors = asStringList(before.contributors);
    const newContributors = asStringList(after.contributors);
    const added = newContributors.filter(
      (uid) => !oldContributors.includes(uid) && uid !== ownerId,
    );
    if (added.length === 0) return;

    const reminderText = asNonBlankString(after.text);

    await Promise.all(
      added.map(async (uid) => {
        await sendPushToUser(uid, (texts) => ({
          title: texts.reminderSharedTitle,
          body: reminderText ?? texts.untitledReminder,
          channelId: CHANNEL_REMINDER_SHARED,
          type: "reminder_shared",
          tag: `reminder_shared_${ownerId}_${event.params.reminderId}`,
        }));
      }),
    );
  },
);

/**
 * Aviso de vencimiento de recordatorios.
 *
 * `dueDate`/`dueTime` son una fecha FLOTANTE sin zona horaria: el documento no
 * dice en que instante vence, asi que no se puede programar nada por
 * adelantado. Por eso esto es un barrido periodico y no un trigger: cada pasada
 * mira la ventana de recordatorios que pueden estar venciendo y resuelve la
 * hora flotante contra la zona horaria de CADA destinatario, que es lo que
 * conserva la semantica de "avisame a las 09:00 este donde este".
 *
 * Un recordatorio compartido es un unico documento, asi que avisa a todos sus
 * `contributors`, cada uno a su hora local.
 */
export const onReminderDue = onSchedule("every 5 minutes", async () => {
  const now = Date.now();
  // La fecha local de un destinatario nunca se aleja mas de un dia de la fecha
  // UTC (los desfases reales van de -12 a +14), asi que la ventana cubre todas
  // las zonas horarias sin recorrer la coleccion entera.
  const snapshot = await db
    .collectionGroup(REMINDERS_COLLECTION)
    .where("completed", "==", false)
    .where("dueDate", ">=", utcDateKey(now - DAY_MS))
    .where("dueDate", "<=", utcDateKey(now + DAY_MS))
    .get();
  if (snapshot.empty) return;

  const tokensByUserId = new Map<string, UserTokens>();
  const marks: DueNotificationMark[] = [];

  for (const doc of snapshot.docs) {
    const reminder = doc.data();
    const ownerId = doc.ref.parent.parent?.id;
    const dueDate = asNonBlankString(reminder.dueDate);
    if (!ownerId || !dueDate) continue;

    const dueTime = asNonBlankString(reminder.dueTime);
    const signature = dueSignature(dueDate, dueTime);
    const alreadyNotified = asStringMap(reminder.dueNotified);
    const text = asNonBlankString(reminder.text);
    const recipients = [
      ...new Set([ownerId, ...asStringList(reminder.contributors)]),
    ];

    for (const userId of recipients) {
      if (alreadyNotified[userId] === signature) continue;

      const userTokens = await loadUserTokens(userId, tokensByUserId);
      if (userTokens.tokens.length === 0) continue;

      const dueAt = floatingDueToEpochMs(
        dueDate,
        dueTime ?? ALL_DAY_DUE_TIME,
        userTokens.timeZoneId,
      );
      if (dueAt === null || dueAt > now || now - dueAt > MAX_LATE_MS) continue;

      await sendPushToTokens(userId, userTokens, (texts) => ({
        title: texts.reminderDueTitle,
        body: text ?? texts.untitledReminder,
        channelId: CHANNEL_REMINDER_DUE,
        type: "reminder_due",
        // La firma va en el tag a proposito: si se edita el vencimiento, el
        // aviso nuevo es otra notificacion en vez de reemplazar a la anterior.
        tag: `reminder_due_${ownerId}_${doc.id}_${signature}`,
        clickAction: CLICK_ACTION_OPEN_REMINDERS,
        data: { openReminders: "true" },
      }));
      marks.push({ ref: doc.ref, userId, signature });
    }
  }

  await markRemindersNotified(marks);
});

export const onFriendRequestCreated = onDocumentCreated(
  "friendships/{requestId}",
  async (event) => {
    const friendship = event.data?.data();
    if (!friendship) return;

    const receiverId = asNonBlankString(friendship.receiverId);
    const senderId = asNonBlankString(friendship.senderId);
    if (!receiverId || !senderId || receiverId === senderId) return;
    if (friendship.acceptanceDate != null) return;

    const senderName = asNonBlankString(friendship.senderName) ?? senderId;

    await sendPushToUser(receiverId, (texts) => ({
      title: texts.friendRequestTitle,
      body: texts.friendRequestBody(senderName),
      channelId: CHANNEL_FRIEND_REQUESTS,
      type: "friend_request",
      tag: `friend_request_${event.params.requestId}`,
    }));
  },
);

export const onSharedListNoteCreated = onDocumentCreatedWithAuthContext(
  `users/{ownerId}/${NOTES_LIST_COLLECTION}/{listId}/${NOTES_COLLECTION}/{noteId}`,
  async (event) => {
    const note = event.data?.data();
    if (!note) return;

    const creatorId = resolveActorUserId(
      asNonBlankString(note.creatorId),
      asNonBlankString(event.authId),
    );
    if (!asNonBlankString(note.creatorId) && creatorId && event.data) {
      await event.data.ref.set({ creatorId }, { merge: true });
    } else if (!creatorId) {
      logger.warn("Shared list note notification without resolved creatorId", {
        ownerId: event.params.ownerId,
        listId: event.params.listId,
        noteId: event.params.noteId,
        authType: event.authType,
        authId: event.authId,
      });
    }

    const listSnapshot = await db
      .collection(USERS_COLLECTION)
      .doc(event.params.ownerId)
      .collection(NOTES_LIST_COLLECTION)
      .doc(event.params.listId)
      .get();
    const listData = listSnapshot.data();
    if (!listData) return;

    const contributors = asStringList(listData.contributors);
    const recipients = ifUserIdResolved(
      creatorId,
      contributors.filter((uid) => uid !== creatorId),
      contributors,
    );
    if (recipients.length === 0) return;

    const listName = asNonBlankString(listData.name);
    const noteTitle = asNonBlankString(note.title);

    await Promise.all(
      recipients.map(async (uid) => {
        await sendPushToUser(uid, (texts) => ({
          title: texts.sharedListNoteTitle(listName ?? texts.unnamedList),
          body: noteTitle ?? texts.untitledNote,
          channelId: CHANNEL_LIST_SHARED,
          type: "shared_list_note",
          tag: `shared_list_note_${event.params.ownerId}_${event.params.listId}_${event.params.noteId}`,
          clickAction: CLICK_ACTION_OPEN_LIST,
          data: openListData(
            event.params.ownerId,
            event.params.listId,
            listName ?? texts.unnamedList,
            Boolean(listData.ordered),
            Boolean(listData.isGroup),
          ),
        }));
      }),
    );
  },
);

/**
 * Sends a push notification to every active token registered for a user.
 * @param {string} userId Destination user id.
 * @param {Function} buildPayload Builds the payload for the recipient language.
 * @return {Promise<void>} Resolves when all token sends are processed.
 */
async function sendPushToUser(
  userId: string,
  buildPayload: (texts: NotificationTexts) => PushPayload,
): Promise<void> {
  await sendPushToTokens(userId, await loadUserTokens(userId), buildPayload);
}

/**
 * Resolves the notification texts for a device language tag.
 * @param {string} language Language tag written by the app, e.g. "es".
 * @return {NotificationTexts} Texts for that language, English when unknown.
 */
function textsFor(language: string): NotificationTexts {
  const key = language.toLowerCase().split(/[-_]/)[0];
  return NOTIFICATION_TEXTS[key] ?? NOTIFICATION_TEXTS[DEFAULT_LANGUAGE];
}

/**
 * Reads the FCM tokens of a user plus the time zone of the device that
 * registered most recently. The time zone lives on the token document because
 * it describes a device, and a push is delivered to a device.
 * @param {string} userId Owner of the tokens.
 * @param {Map<string, UserTokens>} [cache] Optional per-run cache.
 * @return {Promise<UserTokens>} Tokens and resolved time zone.
 */
async function loadUserTokens(
  userId: string,
  cache?: Map<string, UserTokens>,
): Promise<UserTokens> {
  const cached = cache?.get(userId);
  if (cached) return cached;

  const snap = await db
    .collection(USERS_COLLECTION)
    .doc(userId)
    .collection(FCM_TOKENS_COLLECTION)
    .get();

  const tokens: string[] = [];
  const docs: admin.firestore.QueryDocumentSnapshot[] = [];
  let timeZoneId = DEFAULT_TIME_ZONE;
  let language: string = LEGACY_LANGUAGE;
  let newestZoneUpdate = Number.NEGATIVE_INFINITY;
  let newestLanguageUpdate = Number.NEGATIVE_INFINITY;

  snap.docs.forEach((doc) => {
    const data = doc.data();
    const token = asNonBlankString(data.token);
    if (!token) return;
    tokens.push(token);
    docs.push(doc);

    // Zona e idioma describen un dispositivo, y una push se entrega a un
    // dispositivo: si el usuario tiene varios, gana el registrado mas tarde.
    // Cada campo lleva su propio maximo, para que un token escrito por una
    // version antigua de la app, que todavia no guarda `language`, no anule
    // la zona horaria de otro token mas viejo que si la tiene.
    const updatedAt = asEpochMillis(data.updatedAt);

    const zone = asNonBlankString(data.timeZoneId);
    if (zone && updatedAt >= newestZoneUpdate) {
      newestZoneUpdate = updatedAt;
      timeZoneId = zone;
    }

    const deviceLanguage = asNonBlankString(data.language);
    if (deviceLanguage && updatedAt >= newestLanguageUpdate) {
      newestLanguageUpdate = updatedAt;
      language = deviceLanguage;
    }
  });

  const userTokens: UserTokens = {
    tokens,
    docs,
    timeZoneId,
    texts: textsFor(language),
  };
  cache?.set(userId, userTokens);
  return userTokens;
}

/**
 * Delivers a payload to already loaded tokens and prunes the stale ones.
 * @param {string} userId Destination user id.
 * @param {UserTokens} userTokens Tokens previously loaded for the user.
 * @param {Function} buildPayload Builds the payload for the recipient language.
 * @return {Promise<void>} Resolves when all token sends are processed.
 */
async function sendPushToTokens(
  userId: string,
  userTokens: UserTokens,
  buildPayload: (texts: NotificationTexts) => PushPayload,
): Promise<void> {
  const { tokens, docs: docsByToken } = userTokens;
  if (tokens.length === 0) return;

  const payload = buildPayload(userTokens.texts);

  const response = await messaging.sendEachForMulticast({
    tokens,
    notification: {
      title: payload.title,
      body: payload.body,
    },
    data: {
      title: payload.title,
      body: payload.body,
      channelId: payload.channelId,
      type: payload.type,
      notificationTag: payload.tag,
      ...(payload.data ?? {}),
    },
    android: {
      notification: {
        icon: "ic_launcher",
        channelId: payload.channelId,
        tag: payload.tag,
        clickAction: payload.clickAction,
      },
    },
  });

  const staleDeletes: Promise<unknown>[] = [];
  response.responses.forEach((result, index) => {
    if (result.success) return;

    const code = result.error?.code ?? "";
    if (
      code.includes("registration-token-not-registered") ||
      code.includes("invalid-argument")
    ) {
      staleDeletes.push(docsByToken[index].ref.delete());
      return;
    }

    logger.warn("FCM send failed", {
      userId,
      code,
      message: result.error?.message,
      type: payload.type,
    });
  });

  if (staleDeletes.length > 0) {
    await Promise.all(staleDeletes);
  }
}

/**
 * Returns a trimmed string or null when the value is not a non-empty string.
 * @param {unknown} value Value to normalize.
 * @return {string | null} Trimmed string or null.
 */
function asNonBlankString(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/**
 * Returns a plain map of string values, ignoring anything else.
 * @param {unknown} value Value to normalize.
 * @return {Record<string, string>} Normalized string map.
 */
function asStringMap(value: unknown): Record<string, string> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const [key, item] of Object.entries(value)) {
    const normalized = asNonBlankString(item);
    if (normalized) result[key] = normalized;
  }
  return result;
}

/**
 * Reads a Firestore timestamp as epoch millis, defaulting to zero.
 * @param {unknown} value Value to normalize.
 * @return {number} Epoch millis or zero.
 */
function asEpochMillis(value: unknown): number {
  if (value instanceof admin.firestore.Timestamp) return value.toMillis();
  return 0;
}

/**
 * Builds the identity of a due notification. Editing the due date changes the
 * signature, which is what allows the reminder to notify again.
 * @param {string} dueDate Floating due date, "yyyy-MM-dd".
 * @param {string | null} dueTime Floating due time, "HH:mm", or null.
 * @return {string} Signature stored under `dueNotified.{uid}`.
 */
function dueSignature(dueDate: string, dueTime: string | null): string {
  return `${dueDate}T${dueTime ?? "all-day"}`;
}

/**
 * Returns the UTC calendar day of an instant as "yyyy-MM-dd".
 * @param {number} epochMs Instant in epoch millis.
 * @return {string} UTC date key.
 */
function utcDateKey(epochMs: number): string {
  return new Date(epochMs).toISOString().slice(0, 10);
}

/**
 * Resolves a floating due date/time against a time zone into an absolute
 * instant. Two passes because the offset depends on the instant being
 * computed: the first corrects the guess and the second settles DST changes.
 * @param {string} dueDate Floating due date, "yyyy-MM-dd".
 * @param {string} dueTime Floating due time, "HH:mm".
 * @param {string} timeZone IANA time zone id.
 * @return {number | null} Epoch millis, or null when the input is unusable.
 */
function floatingDueToEpochMs(
  dueDate: string,
  dueTime: string,
  timeZone: string,
): number | null {
  const dateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dueDate);
  const timeMatch = /^(\d{2}):(\d{2})/.exec(dueTime);
  if (!dateMatch || !timeMatch) return null;

  const naiveUtc = Date.UTC(
    Number(dateMatch[1]),
    Number(dateMatch[2]) - 1,
    Number(dateMatch[3]),
    Number(timeMatch[1]),
    Number(timeMatch[2]),
  );
  if (Number.isNaN(naiveUtc)) return null;

  let epochMs = naiveUtc;
  for (let attempt = 0; attempt < 2; attempt++) {
    const offset = timeZoneOffsetMs(epochMs, timeZone);
    if (offset === null) return null;
    const corrected = naiveUtc - offset;
    if (corrected === epochMs) break;
    epochMs = corrected;
  }
  return epochMs;
}

/**
 * Returns the UTC offset of a time zone at a given instant.
 * @param {number} epochMs Instant in epoch millis.
 * @param {string} timeZone IANA time zone id.
 * @return {number | null} Offset in millis, or null when the zone is invalid.
 */
function timeZoneOffsetMs(epochMs: number, timeZone: string): number | null {
  try {
    const parts = zoneFormatter(timeZone).formatToParts(new Date(epochMs));
    const values: Record<string, number> = {};
    for (const part of parts) {
      if (part.type !== "literal") values[part.type] = Number(part.value);
    }
    const asUtc = Date.UTC(
      values.year,
      values.month - 1,
      values.day,
      values.hour,
      values.minute,
      values.second,
    );
    return Number.isNaN(asUtc) ? null : asUtc - epochMs;
  } catch (error) {
    logger.warn("Unknown time zone for reminder notification", {
      timeZone,
      message: error instanceof Error ? error.message : String(error),
    });
    return null;
  }
}

const zoneFormatters = new Map<string, Intl.DateTimeFormat>();

/**
 * Returns a cached formatter that renders an instant in a given time zone.
 * @param {string} timeZone IANA time zone id.
 * @return {Intl.DateTimeFormat} Formatter for that zone.
 */
function zoneFormatter(timeZone: string): Intl.DateTimeFormat {
  const cached = zoneFormatters.get(timeZone);
  if (cached) return cached;

  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hourCycle: "h23",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
  zoneFormatters.set(timeZone, formatter);
  return formatter;
}

/**
 * Records which users already got the due notification for a given due value.
 * Runs after sending: if the write fails the next pass resends, and Android
 * collapses the repeat because both notifications share the same tag.
 * @param {DueNotificationMark[]} marks Notifications already delivered.
 * @return {Promise<void>} Resolves when every batch is committed.
 */
async function markRemindersNotified(
  marks: DueNotificationMark[],
): Promise<void> {
  if (marks.length === 0) return;

  const byPath = new Map<string, {
    ref: admin.firestore.DocumentReference;
    dueNotified: Record<string, string>;
  }>();
  for (const mark of marks) {
    const entry = byPath.get(mark.ref.path) ??
      { ref: mark.ref, dueNotified: {} };
    entry.dueNotified[mark.userId] = mark.signature;
    byPath.set(mark.ref.path, entry);
  }

  const entries = [...byPath.values()];
  for (let start = 0; start < entries.length; start += MAX_BATCH_WRITES) {
    const batch = db.batch();
    for (const entry of entries.slice(start, start + MAX_BATCH_WRITES)) {
      // `merge` hace merge profundo del mapa: no pisa las firmas de otros uids.
      batch.set(entry.ref, { dueNotified: entry.dueNotified }, { merge: true });
    }
    await batch.commit();
  }
}

/**
 * Returns a normalized string list with blanks removed and duplicates collapsed.
 * @param {unknown} value Value to normalize.
 * @return {string[]} Normalized unique string list.
 */
function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map(asNonBlankString).filter((item): item is string => item != null))];
}

/**
 * Returns direct contributors for list-share notifications, falling back to
 * legacy contributors when the newer field does not exist yet.
 * @param {FirebaseFirestore.DocumentData} list List document data.
 * @return {string[]} Direct contributor uid list.
 */
function directContributorsForNotification(
  list: FirebaseFirestore.DocumentData,
): string[] {
  const directContributors = asStringList(list.directContributors);
  return directContributors.length > 0 ? directContributors : asStringList(list.contributors);
}

/**
 * Resolves the actor uid from persisted note data or Firestore auth context.
 * @param {string | null} creatorId Persisted creator uid.
 * @param {string | null} authId Event auth identifier.
 * @return {string | null} Resolved uid when available.
 */
function resolveActorUserId(
  creatorId: string | null,
  authId: string | null,
): string | null {
  if (creatorId) return creatorId;
  if (!authId) return null;

  if (authId.startsWith("user:")) {
    return asNonBlankString(authId.slice("user:".length));
  }

  const usersMatch = /\/users\/([^/]+)$/.exec(authId);
  if (usersMatch?.[1]) {
    return asNonBlankString(usersMatch[1]);
  }

  return asNonBlankString(authId);
}

/**
 * Returns the filtered recipient list when the actor is known, otherwise all candidates.
 * @param {string | null} actorUserId Resolved actor uid.
 * @param {string[]} filtered Recipients excluding actor.
 * @param {string[]} fallback Fallback recipients.
 * @return {string[]} Recipient list to notify.
 */
function ifUserIdResolved(
  actorUserId: string | null,
  filtered: string[],
  fallback: string[],
): string[] {
  return actorUserId ? filtered : fallback;
}

/**
 * Builds the notification data payload needed to open a list directly.
 * @param {string} ownerId List owner uid.
 * @param {string} listId List id.
 * @param {string} listName List display name.
 * @param {boolean} isOrdered Whether the list is ordered.
 * @param {boolean} isGroup Whether the list is a group.
 * @return {Record<string, string>} FCM data payload fields.
 */
function openListData(
  ownerId: string,
  listId: string,
  listName: string,
  isOrdered: boolean,
  isGroup: boolean,
): Record<string, string> {
  return {
    openListOwnerId: ownerId,
    openListId: listId,
    openListName: listName,
    openListOrdered: String(isOrdered),
    openListGroup: String(isGroup),
  };
}
