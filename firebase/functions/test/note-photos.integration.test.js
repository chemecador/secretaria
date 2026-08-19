/* eslint-disable require-jsdoc, @typescript-eslint/no-var-requires */

const { after, before, test } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("firebase-admin");
const sharp = require("sharp");

const PROJECT_ID = "demo-secretaria";
const REGION = "europe-west1";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST;
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST;
const STORAGE_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST ?? "127.0.0.1:15001";
const BUCKET = `${PROJECT_ID}.appspot.com`;

let firestore;
let owner;
let collaborator;
let anonymous;
let location;
let validJpeg;
let differentJpeg;

before(async () => {
  assert.ok(AUTH_HOST, "Run this test with the Auth emulator.");
  assert.ok(FIRESTORE_HOST, "Run this test with the Firestore emulator.");
  assert.ok(STORAGE_HOST, "Run this test with the Storage emulator.");

  admin.initializeApp({
    projectId: PROJECT_ID,
    storageBucket: BUCKET,
  });
  firestore = admin.firestore();

  const suffix = `${Date.now()}-${process.pid}`;
  [owner, collaborator, anonymous] = await Promise.all([
    createPermanentUser(`note-photo-owner-${suffix}@example.test`),
    createPermanentUser(`note-photo-collaborator-${suffix}@example.test`),
    createAnonymousUser(),
  ]);
  location = {
    ownerId: owner.uid,
    listId: `integration-list-${suffix}`,
    noteId: `integration-note-${suffix}`,
  };

  const listRef = firestore.doc(
    `users/${location.ownerId}/noteslist/${location.listId}`,
  );
  await listRef.set({
    name: "Callable integration list",
    contributors: [owner.uid, collaborator.uid],
  });
  await listRef.collection("notes").doc(location.noteId).set({
    title: "Callable integration note",
    content: "Body",
  });
  await firestore.doc("notePhotoSystem/global").set({
    uploadsEnabled: true,
  }, { merge: true });

  [validJpeg, differentJpeg] = await Promise.all([
    createJpeg("#3367d6"),
    createJpeg("#d64545"),
  ]);
});

after(async () => {
  await Promise.all(admin.apps.map((app) => app.delete()));
});

test("note photo callables account attempts and let a removed uploader delete", async () => {
  const requestId = "integration-valid-upload";
  const uploadData = {
    ...location,
    requestId,
    jpegBase64: validJpeg.toString("base64"),
  };

  const anonymousUpload = await callCallable(
    "uploadNotePhoto",
    anonymous.idToken,
    uploadData,
  );
  assert.equal(anonymousUpload.ok, false);
  assert.equal(anonymousUpload.error.status, "UNAUTHENTICATED");
  assert.equal(
    (await firestore.doc(`notePhotoQuotas/${anonymous.uid}`).get()).exists,
    false,
  );
  assert.equal(
    (await firestore.doc("notePhotoSystem/global").get()).get("callBudgetCount"),
    undefined,
  );

  const firstUpload = await callCallable(
    "uploadNotePhoto",
    collaborator.idToken,
    uploadData,
  );
  assert.equal(firstUpload.ok, true, JSON.stringify(firstUpload.body));
  assert.equal(firstUpload.data.uploaderId, collaborator.uid);
  assert.equal(firstUpload.data.width, 48);
  assert.equal(firstUpload.data.height, 32);
  assert.ok(firstUpload.data.byteSize > 0);

  const photoId = firstUpload.data.photoId;
  const requestPath = `notePhotoUploadRequests/${collaborator.uid}_${requestId}`;
  const quotaPath = `notePhotoQuotas/${collaborator.uid}`;
  const photoPath = notePath(location, `photos/${photoId}`);
  const afterFirst = await readAccounting(quotaPath, requestPath);
  assert.equal(afterFirst.quota.dailyAttempts, 1);
  assert.equal(afterFirst.quota.monthlyAttempts, 1);
  assert.equal(afterFirst.quota.callBudgetCount, 1);
  assert.equal(afterFirst.quota.activeCount, 1);
  assert.equal(afterFirst.quota.reservedCount, 0);
  assert.ok(afterFirst.quota.activeBytes > 0);
  assert.equal(afterFirst.request.status, "completed");
  assert.equal(afterFirst.request.attemptCount, 1);
  assert.equal((await firestore.doc(photoPath).get()).exists, true);
  await assertStorageObjectExists(firstUpload.data.storagePath, true);
  await assertStorageObjectExists(firstUpload.data.thumbnailPath, true);

  const replay = await callCallable(
    "uploadNotePhoto",
    collaborator.idToken,
    uploadData,
  );
  assert.equal(replay.ok, true, JSON.stringify(replay.body));
  assert.deepEqual(replay.data, firstUpload.data);
  const afterReplay = await readAccounting(quotaPath, requestPath);
  assert.equal(afterReplay.quota.dailyAttempts, 1);
  assert.equal(afterReplay.quota.monthlyAttempts, 1);
  assert.equal(afterReplay.quota.callBudgetCount, 2);
  assert.equal(afterReplay.request.attemptCount, 1);
  assert.equal(afterReplay.quota.activeCount, 1);

  const mismatch = await callCallable(
    "uploadNotePhoto",
    collaborator.idToken,
    {
      ...uploadData,
      jpegBase64: differentJpeg.toString("base64"),
    },
  );
  assert.equal(mismatch.ok, false);
  assert.equal(mismatch.error.status, "INVALID_ARGUMENT");
  assert.equal(mismatch.error.details.reason, "PAYLOAD_MISMATCH");
  const afterMismatch = await readAccounting(quotaPath, requestPath);
  assert.equal(afterMismatch.quota.dailyAttempts, 2);
  assert.equal(afterMismatch.quota.monthlyAttempts, 2);
  assert.equal(afterMismatch.quota.callBudgetCount, 3);
  assert.equal(afterMismatch.request.attemptCount, 2);
  assert.equal(afterMismatch.request.status, "completed");
  assert.equal(afterMismatch.quota.activeCount, 1);

  const malformedRequestId = "integration-malformed-upload";
  const malformed = await callCallable(
    "uploadNotePhoto",
    collaborator.idToken,
    {
      ...location,
      requestId: malformedRequestId,
      jpegBase64: "!!!!",
    },
  );
  assert.equal(malformed.ok, false);
  assert.equal(malformed.error.status, "INVALID_ARGUMENT");
  assert.equal(malformed.error.details.reason, "MALFORMED");
  const malformedRequest = await firestore.doc(
    `notePhotoUploadRequests/${collaborator.uid}_${malformedRequestId}`,
  ).get();
  const afterMalformedQuota = (await firestore.doc(quotaPath).get()).data();
  assert.equal(afterMalformedQuota.dailyAttempts, 3);
  assert.equal(afterMalformedQuota.monthlyAttempts, 3);
  assert.equal(afterMalformedQuota.callBudgetCount, 4);
  assert.equal(afterMalformedQuota.activeCount, 1);
  assert.equal(malformedRequest.get("attemptCount"), 1);
  assert.equal(malformedRequest.get("status"), "rejected");
  assert.equal(malformedRequest.get("failureReason"), "MALFORMED");

  await firestore.doc(
    `users/${location.ownerId}/noteslist/${location.listId}`,
  ).update({ contributors: [owner.uid] });
  const deletion = await callCallable(
    "deleteNotePhoto",
    collaborator.idToken,
    { ...location, photoId },
  );
  assert.equal(deletion.ok, true, JSON.stringify(deletion.body));
  assert.deepEqual(deletion.data, { deleted: true });
  assert.equal((await firestore.doc(photoPath).get()).exists, false);

  const afterDeleteQuota = (await firestore.doc(quotaPath).get()).data();
  assert.equal(afterDeleteQuota.dailyAttempts, 3);
  assert.equal(afterDeleteQuota.monthlyAttempts, 3);
  assert.equal(afterDeleteQuota.callBudgetCount, 5);
  assert.equal(afterDeleteQuota.activeCount, 0);
  assert.equal(afterDeleteQuota.activeBytes, 0);
  assert.equal(afterDeleteQuota.reservedCount, 0);
  assert.equal(afterDeleteQuota.reservedBytes, 0);
  await assertStorageObjectExists(firstUpload.data.storagePath, false);
  await assertStorageObjectExists(firstUpload.data.thumbnailPath, false);

  await firestore.doc(quotaPath).update({ callBudgetCount: 100 });
  const blockedByCallBudget = await callCallable(
    "uploadNotePhoto",
    collaborator.idToken,
    {
      ...location,
      requestId: "integration-call-limit",
      jpegBase64: validJpeg.toString("base64"),
    },
  );
  assert.equal(blockedByCallBudget.ok, false);
  assert.equal(blockedByCallBudget.error.status, "RESOURCE_EXHAUSTED");
  assert.equal(blockedByCallBudget.error.details.reason, "CALL_LIMIT");
  assert.equal(
    (await firestore.doc(
      `notePhotoUploadRequests/${collaborator.uid}_integration-call-limit`,
    ).get()).exists,
    false,
  );
  const afterCallLimitQuota = (await firestore.doc(quotaPath).get()).data();
  assert.equal(afterCallLimitQuota.dailyAttempts, 3);
  assert.equal(afterCallLimitQuota.callBudgetCount, 100);
});

async function createPermanentUser(email) {
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        email,
        password: "Integration123!",
        returnSecureToken: true,
      }),
    },
  );
  const body = await response.json();
  assert.equal(response.ok, true, JSON.stringify(body));
  return { uid: body.localId, idToken: body.idToken };
}

async function createAnonymousUser() {
  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true }),
    },
  );
  const body = await response.json();
  assert.equal(response.ok, true, JSON.stringify(body));
  return { uid: body.localId, idToken: body.idToken };
}

async function createJpeg(background) {
  return sharp({
    create: {
      width: 48,
      height: 32,
      channels: 3,
      background,
    },
  }).jpeg({ quality: 85 }).toBuffer();
}

async function callCallable(name, idToken, data) {
  const response = await fetch(
    `http://${FUNCTIONS_HOST}/${PROJECT_ID}/${REGION}/${name}`,
    {
      method: "POST",
      headers: {
        "authorization": `Bearer ${idToken}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({ data }),
    },
  );
  const body = await response.json();
  return {
    ok: response.ok && !body.error,
    data: body.result ?? body.data,
    error: body.error,
    body,
  };
}

async function readAccounting(quotaPath, requestPath) {
  const [quota, request] = await Promise.all([
    firestore.doc(quotaPath).get(),
    firestore.doc(requestPath).get(),
  ]);
  assert.equal(quota.exists, true);
  assert.equal(request.exists, true);
  return { quota: quota.data(), request: request.data() };
}

function notePath(value, suffix = "") {
  const base = `users/${value.ownerId}/noteslist/${value.listId}/notes/${value.noteId}`;
  return suffix ? `${base}/${suffix}` : base;
}

async function assertStorageObjectExists(objectPath, expected) {
  const [exists] = await admin.storage().bucket().file(objectPath).exists();
  assert.equal(exists, expected, objectPath);
}
