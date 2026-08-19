/* eslint-disable require-jsdoc, @typescript-eslint/no-var-requires */

const { after, before, beforeEach, test } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = "demo-secretaria";
const BUCKET_URL = `gs://${PROJECT_ID}.appspot.com`;
const LIST_PATH = "users/owner/noteslist/list-1";
const NOTE_PATH = `${LIST_PATH}/notes/note-1`;
const PHOTO_PATH = `${NOTE_PATH}/photos/photo-1`;
const ORIGINAL_PATH = "note-images/owner/list-1/note-1/photo-1/original.jpg";
const THUMBNAIL_PATH = "note-images/owner/list-1/note-1/photo-1/thumbnail.jpg";

let environment;

before(async () => {
  const firebaseRoot = path.resolve(__dirname, "..", "..");
  environment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync(path.join(firebaseRoot, "firestore.rules"), "utf8"),
    },
    storage: {
      rules: fs.readFileSync(path.join(firebaseRoot, "storage.rules"), "utf8"),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
  await environment.clearStorage();
  await environment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await firestore.doc(LIST_PATH).set({
      name: "Shared",
      contributors: ["owner", "collaborator"],
      directContributors: ["owner", "collaborator"],
      inheritedGroupContributors: [],
      archivedBy: [],
      archivedAtBy: {},
      groupId: null,
      groupOwnerId: null,
      groupOrder: 0,
      isGroup: false,
    });
    await firestore.doc(NOTE_PATH).set({
      title: "Note",
      content: "Body",
      order: 0,
    });
    await firestore.doc(PHOTO_PATH).set({
      status: "ready",
      storagePath: ORIGINAL_PATH,
      thumbnailPath: THUMBNAIL_PATH,
      byteSize: 3,
      width: 1,
      height: 1,
      uploaderId: "owner",
      createdAt: new Date(),
    });
    await firestore.doc("users/collaborator/noteslist/group-1").set({
      name: "Group",
      contributors: ["collaborator", "group-member"],
      directContributors: ["collaborator", "group-member"],
      inheritedGroupContributors: [],
      isGroup: true,
    });
    await firestore.doc("users/owner/reminders/reminder-1").set({
      text: "Reminder",
      dueDate: null,
      dueTime: null,
      completed: false,
      completedAt: null,
      order: 0,
      contributors: ["owner", "collaborator"],
    });
    await firestore.doc("friendships/friendship-1").set({
      senderId: "owner",
      receiverId: "collaborator",
      acceptanceDate: null,
    });

    const storage = context.storage(BUCKET_URL);
    await storage.ref(ORIGINAL_PATH).put(new Uint8Array([1, 2, 3]), {
      contentType: "image/jpeg",
    });
    await storage.ref(THUMBNAIL_PATH).put(new Uint8Array([1, 2]), {
      contentType: "image/jpeg",
    });
  });
});

after(async () => {
  await environment.cleanup();
});

function context(uid, provider = "password") {
  return environment.authenticatedContext(uid, {
    firebase: { sign_in_provider: provider },
  });
}

test("notes are limited to the owner and current collaborators", async () => {
  await assertSucceeds(context("owner").firestore().doc(NOTE_PATH).get());
  await assertSucceeds(context("collaborator").firestore().doc(NOTE_PATH).get());
  await assertSucceeds(context("collaborator").firestore().doc(NOTE_PATH).update({
    content: "Shared edit",
  }));
  await assertFails(context("outsider").firestore().doc(NOTE_PATH).get());
  await assertFails(context("outsider").firestore().doc(NOTE_PATH).update({
    content: "Attack",
  }));
});

test("a collaborator cannot grant arbitrary list access", async () => {
  await assertFails(context("collaborator").firestore().doc(LIST_PATH).update({
    directContributors: ["owner", "collaborator", "outsider"],
    contributors: ["owner", "collaborator", "outsider"],
  }));
  await assertSucceeds(context("owner").firestore().doc(LIST_PATH).update({
    directContributors: ["owner", "collaborator", "friend"],
    contributors: ["owner", "collaborator", "friend"],
  }));
});

test("a collaborator can archive, leave, and manage an owned group", async () => {
  const list = context("collaborator").firestore().doc(LIST_PATH);
  await assertSucceeds(list.update({
    archivedBy: ["collaborator"],
    archivedAtBy: { collaborator: new Date() },
  }));
  await assertSucceeds(list.update({
    groupId: "group-1",
    groupOwnerId: "collaborator",
    groupOrder: 0,
    inheritedGroupContributors: ["collaborator", "group-member"],
    contributors: ["owner", "collaborator", "group-member"],
    directContributors: ["owner", "collaborator"],
  }));
  await assertSucceeds(list.update({
    directContributors: ["owner"],
    contributors: ["owner", "collaborator", "group-member"],
  }));
});

test("group sharing can propagate to a child list in one batch", async () => {
  const firestore = context("collaborator").firestore();
  const group = firestore.doc("users/collaborator/noteslist/group-1");
  const child = firestore.doc(LIST_PATH);
  await child.update({
    groupId: "group-1",
    groupOwnerId: "collaborator",
    groupOrder: 0,
    inheritedGroupContributors: ["collaborator", "group-member"],
    contributors: ["owner", "collaborator", "group-member"],
    directContributors: ["owner", "collaborator"],
  });

  const batch = firestore.batch();
  batch.update(group, {
    directContributors: ["collaborator", "group-member", "new-member"],
    contributors: ["collaborator", "group-member", "new-member"],
  });
  batch.update(child, {
    directContributors: ["owner", "collaborator"],
    inheritedGroupContributors: ["collaborator", "group-member", "new-member"],
    contributors: ["owner", "collaborator", "group-member", "new-member"],
  });
  await assertSucceeds(batch.commit());
});

test("leaving a group can remove inherited child access in one batch", async () => {
  await environment.withSecurityRulesDisabled(async (adminContext) => {
    const firestore = adminContext.firestore();
    await firestore.doc("users/group-owner/noteslist/group-2").set({
      contributors: ["group-owner", "collaborator"],
      directContributors: ["group-owner", "collaborator"],
      inheritedGroupContributors: [],
      isGroup: true,
    });
    await firestore.doc(LIST_PATH).update({
      groupId: "group-2",
      groupOwnerId: "group-owner",
      inheritedGroupContributors: ["group-owner", "collaborator"],
      directContributors: ["owner"],
      contributors: ["owner", "group-owner", "collaborator"],
    });
  });

  const firestore = context("collaborator").firestore();
  const batch = firestore.batch();
  batch.update(firestore.doc("users/group-owner/noteslist/group-2"), {
    directContributors: ["group-owner"],
    contributors: ["group-owner"],
  });
  batch.update(firestore.doc(LIST_PATH), {
    directContributors: ["owner"],
    inheritedGroupContributors: ["group-owner"],
    contributors: ["owner", "group-owner"],
  });
  await assertSucceeds(batch.commit());
});

test("collection group list reads require the matching contributor query", async () => {
  const firestore = context("collaborator").firestore();
  await assertSucceeds(
    firestore.collectionGroup("noteslist")
      .where("contributors", "array-contains", "collaborator")
      .get(),
  );
  await assertFails(firestore.collectionGroup("noteslist").get());
});

test("photo metadata is read-only and anonymous accounts are excluded", async () => {
  await assertSucceeds(context("collaborator").firestore().doc(PHOTO_PATH).get());
  await assertFails(context("anonymous", "anonymous").firestore().doc(PHOTO_PATH).get());
  await assertFails(context("outsider").firestore().doc(PHOTO_PATH).get());
  await assertFails(context("owner").firestore().doc(`${NOTE_PATH}/photos/new`).set({
    status: "ready",
  }));
});

test("storage allows only authorised reads and never client writes", async () => {
  const collaboratorObject = context("collaborator").storage(BUCKET_URL).ref(ORIGINAL_PATH);
  await assertSucceeds(collaboratorObject.getMetadata());
  await assertFails(context("outsider").storage(BUCKET_URL).ref(ORIGINAL_PATH).getMetadata());
  await assertFails(
    context("anonymous", "anonymous").storage(BUCKET_URL).ref(ORIGINAL_PATH).getMetadata(),
  );
  await assertFails(collaboratorObject.put(new Uint8Array([4, 5, 6]), {
    contentType: "image/jpeg",
  }));
});

test("shared reminders allow edits and leaving but not resharing", async () => {
  const reminder = context("collaborator").firestore()
    .doc("users/owner/reminders/reminder-1");
  await assertSucceeds(reminder.update({ text: "Shared edit" }));
  await assertFails(reminder.update({
    contributors: ["owner", "collaborator", "outsider"],
  }));
  await assertSucceeds(reminder.update({ contributors: ["owner"] }));
});

test("only the receiver can accept a friendship without changing its actors", async () => {
  const friendship = context("collaborator").firestore().doc("friendships/friendship-1");
  await assertSucceeds(friendship.update({
    acceptanceDate: new Date(),
    receiverName: "Collaborator",
  }));
  await assertFails(context("outsider").firestore().doc("friendships/friendship-1").get());
  await assertFails(friendship.update({ senderId: "outsider" }));
});
