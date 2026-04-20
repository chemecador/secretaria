import {
  onDocumentCreated,
  onDocumentCreatedWithAuthContext,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();
const USERS_COLLECTION = "users";
const FCM_TOKENS_COLLECTION = "fcm_tokens";
const NOTES_LIST_COLLECTION = "noteslist";
const NOTES_COLLECTION = "notes";
const CHANNEL_LIST_SHARED = "list_shared";
const CHANNEL_FRIEND_REQUESTS = "friend_requests";
const CLICK_ACTION_OPEN_LIST = "com.chemecador.secretaria.OPEN_LIST";

type PushPayload = {
  title: string;
  body: string;
  channelId: string;
  type: string;
  tag: string;
  clickAction?: string;
  data?: Record<string, string>;
};

export const onListShared = onDocumentUpdated(
  "users/{userId}/noteslist/{listId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const oldContributors: string[] = before.contributors ?? [];
    const newContributors: string[] = after.contributors ?? [];
    const ownerId = event.params.userId;
    const added = newContributors.filter(
      (uid) => !oldContributors.includes(uid) && uid !== ownerId,
    );
    if (added.length === 0) return;

    const creator: string = after.creator ?? "";
    const listName: string = after.name ?? "";

    await Promise.all(
      added.map(async (uid) => {
        await sendPushToUser(uid, {
          title: "Nueva lista disponible",
          body: `El usuario ${creator} te ha compartido la lista "${listName}".`,
          channelId: CHANNEL_LIST_SHARED,
          type: "list_shared",
          tag: `list_shared_${event.params.listId}`,
          clickAction: CLICK_ACTION_OPEN_LIST,
          data: openListData(
            event.params.userId,
            event.params.listId,
            listName,
            Boolean(after.ordered),
          ),
        });
      }),
    );
  },
);

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

    await sendPushToUser(receiverId, {
      title: "Nueva solicitud de amistad",
      body: `${senderName} te ha enviado una solicitud de amistad.`,
      channelId: CHANNEL_FRIEND_REQUESTS,
      type: "friend_request",
      tag: `friend_request_${event.params.requestId}`,
    });
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

    const listName = asNonBlankString(listData.name) ?? "lista compartida";
    const noteTitle = asNonBlankString(note.title) ?? "Nota sin titulo";

    await Promise.all(
      recipients.map(async (uid) => {
        await sendPushToUser(uid, {
          title: `Nueva nota en ${listName}`,
          body: noteTitle,
          channelId: CHANNEL_LIST_SHARED,
          type: "shared_list_note",
          tag: `shared_list_note_${event.params.ownerId}_${event.params.listId}_${event.params.noteId}`,
          clickAction: CLICK_ACTION_OPEN_LIST,
          data: openListData(
            event.params.ownerId,
            event.params.listId,
            listName,
            Boolean(listData.ordered),
          ),
        });
      }),
    );
  },
);

/**
 * Sends a push notification to every active token registered for a user.
 * @param {string} userId Destination user id.
 * @param {PushPayload} payload Notification payload to deliver.
 * @return {Promise<void>} Resolves when all token sends are processed.
 */
async function sendPushToUser(
  userId: string,
  payload: PushPayload,
): Promise<void> {
  const snap = await db
    .collection(USERS_COLLECTION)
    .doc(userId)
    .collection(FCM_TOKENS_COLLECTION)
    .get();
  if (snap.empty) return;

  const tokens: string[] = [];
  const docsByToken: admin.firestore.QueryDocumentSnapshot[] = [];
  snap.docs.forEach((doc) => {
    const token = doc.data().token as string | undefined;
    if (token) {
      tokens.push(token);
      docsByToken.push(doc);
    }
  });
  if (tokens.length === 0) return;

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
 * Returns a normalized string list with blanks removed and duplicates collapsed.
 * @param {unknown} value Value to normalize.
 * @return {string[]} Normalized unique string list.
 */
function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map(asNonBlankString).filter((item): item is string => item != null))];
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
 * @return {Record<string, string>} FCM data payload fields.
 */
function openListData(
  ownerId: string,
  listId: string,
  listName: string,
  isOrdered: boolean,
): Record<string, string> {
  return {
    openListOwnerId: ownerId,
    openListId: listId,
    openListName: listName,
    openListOrdered: String(isOrdered),
  };
}
