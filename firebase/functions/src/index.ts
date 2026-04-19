import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

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
        const snap = await db
          .collection("users")
          .doc(uid)
          .collection("fcm_tokens")
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
            title: "Nueva lista disponible",
            body: `El usuario ${creator} te ha compartido la lista "${listName}".`,
          },
          android: {
            notification: {
              icon: "ic_launcher",
            },
          },
        });

        const staleDeletes: Promise<unknown>[] = [];
        response.responses.forEach((r, i) => {
          if (!r.success) {
            const code = r.error?.code ?? "";
            if (
              code.includes("registration-token-not-registered") ||
              code.includes("invalid-argument")
            ) {
              staleDeletes.push(docsByToken[i].ref.delete());
            } else {
              logger.warn("FCM send failed", {
                uid,
                code,
                message: r.error?.message,
              });
            }
          }
        });
        if (staleDeletes.length > 0) {
          await Promise.all(staleDeletes);
        }
      }),
    );
  },
);
