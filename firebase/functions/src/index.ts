import {
  onDocumentCreated,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();
const USERS_COLLECTION = "users";
const FCM_TOKENS_COLLECTION = "fcm_tokens";
const CHANNEL_LIST_SHARED = "list_shared";
const CHANNEL_FRIEND_REQUESTS = "friend_requests";

type PushPayload = {
  title: string;
  body: string;
  channelId: string;
  type: string;
  tag: string;
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
    },
    android: {
      notification: {
        icon: "ic_launcher",
        channelId: payload.channelId,
        tag: payload.tag,
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
