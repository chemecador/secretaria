/* eslint-disable require-jsdoc */

import { createHash, randomUUID } from "node:crypto";
import * as admin from "firebase-admin";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import sharp from "sharp";

const REGION = "europe-west1";
const CALLABLE_OPTIONS = {
  region: REGION,
  // Enforced in production once App Check metrics read 100% verified. The emulator cannot mint a
  // valid App Check token, so the end-to-end test would fail on every call; FUNCTIONS_EMULATOR is
  // set only inside the emulator and never during a deploy.
  enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== "true",
  maxInstances: 3,
  concurrency: 1,
  memory: "512MiB" as const,
  timeoutSeconds: 60,
};

const MAX_CLIENT_BYTES = 1024 * 1024;
const MAX_INPUT_PIXELS = 12_000_000;
const MAX_INPUT_SIDE = 4096;
const MAX_ORIGINAL_BYTES = 1024 * 1024;
const TARGET_ORIGINAL_BYTES = 600 * 1024;
const MAX_ORIGINAL_SIDE = 1600;
const FALLBACK_ORIGINAL_SIDE = 1280;
const THUMBNAIL_SIDE = 320;
const NOTE_PHOTO_LIMIT = 3;
const ACCOUNT_PHOTO_LIMIT = 50;
const ACCOUNT_BYTES_LIMIT = 50 * 1024 * 1024;
const ACCOUNT_DAILY_LIMIT = 10;
const ACCOUNT_MONTHLY_LIMIT = 50;
const ACCOUNT_DAILY_CALL_LIMIT = 100;
const GLOBAL_BYTES_LIMIT = 2 * 1024 * 1024 * 1024;
const GLOBAL_DAILY_LIMIT = 250;
const GLOBAL_DAILY_CALL_LIMIT = 1000;
const RESERVATION_TTL_MS = 10 * 60 * 1000;
const VALIDATION_TTL_MS = 2 * 60 * 1000;
const MAX_REQUEST_ATTEMPTS = 3;

const USERS = "users";
const LISTS = "noteslist";
const NOTES = "notes";
const PHOTOS = "photos";
const REQUESTS = "notePhotoUploadRequests";
const USER_USAGE = "notePhotoQuotas";
const GLOBAL_USAGE = "notePhotoSystem";
const INTERNAL = "_photoInternal";
const NOTE_USAGE_DOCUMENT = "usage";

type UnknownRecord = Record<string, unknown>;

type PhotoResponse = {
  photoId: string;
  storagePath: string;
  thumbnailPath: string;
  byteSize: number;
  width: number;
  height: number;
  createdAtEpochMs: number;
  uploaderId: string;
};

type PreparedPhoto = {
  original: Buffer;
  thumbnail: Buffer;
  width: number;
  height: number;
};

type JpegPayload = {
  bytes: Buffer;
};

type AttemptStart = {
  response: PhotoResponse | null;
  error: HttpsError | null;
  staleObjects: {
    storagePath: string;
    thumbnailPath: string;
  } | null;
};

type Usage = {
  activeCount: number;
  activeBytes: number;
  reservedCount: number;
  reservedBytes: number;
};

type Reservation = {
  kind: "reserved";
  token: string;
};

type CompletedReservation = {
  kind: "completed";
  response: PhotoResponse;
};

type ReservationResult = Reservation | CompletedReservation;

type PhotoLocation = {
  ownerId: string;
  listId: string;
  noteId: string;
};

export const uploadNotePhoto = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthenticatedUser(request.auth);
  assertPermanentUser(request.auth);
  await consumeCallBudget(uid);

  const data = asRecord(request.data);
  const location = readLocation(data);
  const requestId = readIdentifier(data, "requestId", 64);
  const refs = references(location, uid, requestId);

  await assertListAccessAndNoteExists(uid, location);
  const payloadHash = fingerprintPayload(data.jpegBase64);
  const attempt = await beginAttempt(uid, location, requestId, payloadHash);
  if (attempt.error) throw attempt.error;
  if (attempt.response) return attempt.response;
  if (attempt.staleObjects) {
    await deleteStorageObjects(
      attempt.staleObjects.storagePath,
      attempt.staleObjects.thumbnailPath,
    );
  }

  let payload: JpegPayload;
  try {
    payload = readPayload(data);
  } catch (error) {
    if (error instanceof HttpsError) {
      await markRequestRejected(refs.request, error);
    }
    throw error;
  }

  let prepared: PreparedPhoto;
  try {
    prepared = await prepareImage(payload.bytes);
  } catch (error) {
    if (error instanceof HttpsError) {
      await markRequestRejected(refs.request, error);
    }
    throw error;
  }
  const photoId = stablePhotoId(uid, requestId);
  const storagePath = storageObjectPath(location, photoId, "original.jpg");
  const thumbnailPath = storageObjectPath(location, photoId, "thumbnail.jpg");
  const storageBytes = prepared.original.length + prepared.thumbnail.length;

  let reservation: ReservationResult;
  try {
    reservation = await reserveUpload({
      uid,
      location,
      requestId,
      photoId,
      storagePath,
      thumbnailPath,
      storageBytes,
      prepared,
    });
  } catch (error) {
    if (error instanceof HttpsError && error.code === "resource-exhausted") {
      await markRequestRejected(refs.request, error);
    }
    throw error;
  }

  if (reservation.kind === "completed") return reservation.response;

  try {
    await writeStorageObjects({
      uid,
      location,
      photoId,
      storagePath,
      thumbnailPath,
      prepared,
    });

    return await completeUpload({
      uid,
      location,
      requestId,
      photoId,
      storagePath,
      thumbnailPath,
      storageBytes,
      prepared,
      reservationToken: reservation.token,
    });
  } catch (error) {
    const released = await releaseReservation(refs.request, reservation.token)
      .catch((releaseError) => {
        logger.error("Could not release note photo reservation", {
          uid,
          ...location,
          requestId,
          error: errorMessage(releaseError),
        });
        return false;
      });
    if (released) {
      await deleteStorageObjects(storagePath, thumbnailPath);
    }

    if (error instanceof HttpsError) throw error;
    logger.error("Note photo upload failed", {
      uid,
      ...location,
      requestId,
      error: errorMessage(error),
    });
    throw new HttpsError("unavailable", "Photo upload failed. Retry safely.");
  }
});

export const deleteNotePhoto = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireAuthenticatedUser(request.auth);
  assertPermanentUser(request.auth);
  await consumeCallBudget(uid);

  const data = asRecord(request.data);
  const location = readLocation(data);
  const photoId = readIdentifier(data, "photoId", 64);

  const photoRef = photoDocument(location, photoId);
  const removed = await removePhotoRecord(location, photoRef, uid);
  if (removed) {
    await deleteStorageObjects(removed.storagePath, removed.thumbnailPath);
  }
  return { deleted: removed != null };
});

export const onNotePhotoMetadataDeleted = onDocumentDeleted(
  {
    region: REGION,
    document: "users/{ownerId}/noteslist/{listId}/notes/{noteId}/photos/{photoId}",
    maxInstances: 3,
  },
  async (event) => {
    const data = event.data?.data();
    const storagePath = optionalString(data?.storagePath);
    const thumbnailPath = optionalString(data?.thumbnailPath);
    if (storagePath) {
      await deleteStorageObjects(storagePath, thumbnailPath ?? storagePath);
    }
  },
);

export const onNoteWithPhotosDeleted = onDocumentDeleted(
  {
    region: REGION,
    document: "users/{ownerId}/noteslist/{listId}/notes/{noteId}",
    maxInstances: 3,
  },
  async (event) => {
    const location: PhotoLocation = {
      ownerId: event.params.ownerId,
      listId: event.params.listId,
      noteId: event.params.noteId,
    };
    const snapshot = await noteDocument(location).collection(PHOTOS).limit(NOTE_PHOTO_LIMIT).get();
    for (const photo of snapshot.docs) {
      const removed = await removePhotoRecord(location, photo.ref);
      if (removed) {
        await deleteStorageObjects(removed.storagePath, removed.thumbnailPath);
      }
    }
  },
);

export const cleanupExpiredNotePhotoReservations = onSchedule(
  {
    region: REGION,
    schedule: "every 30 minutes",
    maxInstances: 1,
    timeoutSeconds: 120,
  },
  async () => {
    const cutoff = Timestamp.fromMillis(
      Date.now() - RESERVATION_TTL_MS,
    );
    const expired = await db().collection(REQUESTS)
      .where("reservedAt", "<", cutoff)
      .limit(100)
      .get();

    for (const requestSnapshot of expired.docs) {
      const token = optionalString(requestSnapshot.get("reservationToken"));
      const storagePath = optionalString(requestSnapshot.get("storagePath"));
      const thumbnailPath = optionalString(requestSnapshot.get("thumbnailPath"));
      if (!token) continue;
      const released = await releaseReservation(requestSnapshot.ref, token);
      if (released && storagePath) {
        await deleteStorageObjects(storagePath, thumbnailPath ?? storagePath);
      }
    }
  },
);

function requireAuthenticatedUser(
  auth: { uid: string; token: UnknownRecord } | undefined,
): string {
  if (!auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  return auth.uid;
}

function assertPermanentUser(
  auth: { uid: string; token: UnknownRecord } | undefined,
): void {
  if (!auth) throw new HttpsError("unauthenticated", "Sign in is required.");
  const firebase = asOptionalRecord(auth.token.firebase);
  if (firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError(
      "unauthenticated",
      "Anonymous accounts cannot use note photos.",
    );
  }
}

async function consumeCallBudget(uid: string): Promise<void> {
  const userRef = db().collection(USER_USAGE).doc(uid);
  const globalRef = db().collection(GLOBAL_USAGE).doc("global");
  const now = Timestamp.now();
  const dayKey = new Date(now.toMillis()).toISOString().slice(0, 10);

  await db().runTransaction(async (transaction) => {
    const [userSnapshot, globalSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(globalRef),
    ]);
    const userData = userSnapshot.data();
    const globalData = globalSnapshot.data();
    const userCalls = userData?.callBudgetDayKey === dayKey ?
      positiveNumber(userData.callBudgetCount) : 0;
    const globalCalls = globalData?.callBudgetDayKey === dayKey ?
      positiveNumber(globalData.callBudgetCount) : 0;

    if (userCalls >= ACCOUNT_DAILY_CALL_LIMIT) {
      throw quotaError("CALL_LIMIT", "Daily photo call limit reached.");
    }
    if (globalCalls >= GLOBAL_DAILY_CALL_LIMIT) {
      throw quotaError("CALL_LIMIT", "Global daily photo call limit reached.");
    }

    transaction.set(userRef, {
      callBudgetDayKey: dayKey,
      callBudgetCount: userCalls + 1,
      updatedAt: now,
    }, { merge: true });
    transaction.set(globalRef, {
      callBudgetDayKey: dayKey,
      callBudgetCount: globalCalls + 1,
      updatedAt: now,
    }, { merge: true });
  });
}

function readLocation(data: UnknownRecord): PhotoLocation {
  return {
    ownerId: readIdentifier(data, "ownerId"),
    listId: readIdentifier(data, "listId"),
    noteId: readIdentifier(data, "noteId"),
  };
}

function readIdentifier(
  data: UnknownRecord,
  field: string,
  maxLength = 128,
): string {
  const value = data[field];
  if (
    typeof value !== "string" ||
    value.length < 1 ||
    value.length > maxLength ||
    !/^[A-Za-z0-9_-]+$/.test(value)
  ) {
    throw invalidArgument("MALFORMED", `Invalid ${field}.`);
  }
  return value;
}

function readPayload(data: UnknownRecord): JpegPayload {
  const value = data.jpegBase64;
  const maxLength = Math.ceil(MAX_CLIENT_BYTES / 3) * 4 + 4;
  if (
    typeof value !== "string" ||
    value.length < 4 ||
    value.length > maxLength ||
    value.length % 4 !== 0 ||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(value)
  ) {
    throw invalidArgument("MALFORMED", "Invalid JPEG payload.");
  }
  const bytes = Buffer.from(value, "base64");
  if (bytes.length > MAX_CLIENT_BYTES) {
    throw invalidArgument("TOO_LARGE", "The image payload is too large.");
  }
  return {
    bytes,
  };
}

function fingerprintPayload(value: unknown): string {
  const fingerprint = typeof value === "string" ?
    `string:${value}` : `non-string:${typeof value}`;
  return createHash("sha256").update(fingerprint).digest("hex");
}

async function prepareImage(input: Buffer): Promise<PreparedPhoto> {
  if (
    input.length < 4 ||
    input.length > MAX_CLIENT_BYTES ||
    input[0] !== 0xff ||
    input[1] !== 0xd8 ||
    input[2] !== 0xff
  ) {
    throw invalidArgument("NOT_JPEG", "Only JPEG images are accepted.");
  }

  try {
    const metadata = await sharp(input, {
      failOn: "error",
      limitInputPixels: MAX_INPUT_PIXELS,
    }).metadata();
    if (
      metadata.format !== "jpeg" ||
      !metadata.width ||
      !metadata.height ||
      metadata.width > MAX_INPUT_SIDE ||
      metadata.height > MAX_INPUT_SIDE ||
      metadata.width * metadata.height > MAX_INPUT_PIXELS
    ) {
      throw invalidArgument("IMAGE_INVALID", "Invalid image dimensions.");
    }

    let best: { data: Buffer; width: number; height: number } | null = null;
    for (const side of [MAX_ORIGINAL_SIDE, FALLBACK_ORIGINAL_SIDE]) {
      for (const quality of [82, 76, 70, 64, 60]) {
        const encoded = await encodeJpeg(input, side, quality);
        if (!best || encoded.data.length < best.data.length) best = encoded;
        if (encoded.data.length <= TARGET_ORIGINAL_BYTES) {
          best = encoded;
          break;
        }
      }
      if (best && best.data.length <= TARGET_ORIGINAL_BYTES) break;
    }

    if (!best || best.data.length > MAX_ORIGINAL_BYTES) {
      throw invalidArgument("TOO_LARGE", "The compressed image is too large.");
    }

    const thumbnail = await sharp(best.data, { failOn: "error" })
      .resize({
        width: THUMBNAIL_SIDE,
        height: THUMBNAIL_SIDE,
        fit: "inside",
        withoutEnlargement: true,
      })
      .jpeg({ quality: 72, mozjpeg: true })
      .toBuffer();

    return {
      original: best.data,
      thumbnail,
      width: best.width,
      height: best.height,
    };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    logger.warn("Rejected invalid note image", { error: errorMessage(error) });
    throw invalidArgument("IMAGE_INVALID", "The image could not be decoded.");
  }
}

async function encodeJpeg(
  input: Buffer,
  side: number,
  quality: number,
): Promise<{ data: Buffer; width: number; height: number }> {
  const output = await sharp(input, {
    failOn: "error",
    limitInputPixels: MAX_INPUT_PIXELS,
  })
    .rotate()
    .flatten({ background: "#ffffff" })
    .toColorspace("srgb")
    .resize({
      width: side,
      height: side,
      fit: "inside",
      withoutEnlargement: true,
    })
    .jpeg({ quality, mozjpeg: true })
    .toBuffer({ resolveWithObject: true });

  return {
    data: output.data,
    width: output.info.width,
    height: output.info.height,
  };
}

async function assertListAccessAndNoteExists(
  uid: string,
  location: PhotoLocation,
): Promise<void> {
  const [list, note] = await Promise.all([
    listDocument(location).get(),
    noteDocument(location).get(),
  ]);
  if (!list.exists || !isListMember(uid, location.ownerId, list.data())) {
    throw new HttpsError("permission-denied", "List access denied.");
  }
  if (!note.exists) throw new HttpsError("not-found", "Note not found.");
}

async function beginAttempt(
  uid: string,
  location: PhotoLocation,
  requestId: string,
  payloadHash: string,
): Promise<AttemptStart> {
  const refs = references(location, uid, requestId);
  const now = Timestamp.now();
  const nowMs = now.toMillis();
  const dayKey = new Date(nowMs).toISOString().slice(0, 10);
  const monthKey = dayKey.slice(0, 7);

  return db().runTransaction(async (transaction) => {
    const requestSnapshot = await transaction.get(refs.request);
    const userSnapshot = await transaction.get(refs.userUsage);
    const noteUsageSnapshot = await transaction.get(refs.noteUsage);
    const globalSnapshot = await transaction.get(refs.globalUsage);

    let userUsage = readUsage(userSnapshot.data());
    let noteUsage = readUsage(noteUsageSnapshot.data());
    let globalUsage = readUsage(globalSnapshot.data());
    let previousAttemptCount = 0;
    let releasedStaleReservation = false;
    let payloadMismatch = false;
    let staleObjects: AttemptStart["staleObjects"] = null;

    if (requestSnapshot.exists) {
      assertSameRequest(requestSnapshot.data(), location);
      const storedPayloadHash = optionalString(
        requestSnapshot.get("payloadHash"),
      );
      payloadMismatch = storedPayloadHash != null &&
        storedPayloadHash !== payloadHash;
      previousAttemptCount = Math.max(
        1,
        Math.floor(positiveNumber(requestSnapshot.get("attemptCount"))),
      );
      const status = requestSnapshot.get("status");
      if (!payloadMismatch && status === "completed") {
        if (!optionalString(requestSnapshot.get("payloadHash"))) {
          transaction.set(refs.request, {
            payloadHash,
            updatedAt: now,
          }, { merge: true });
        }
        return {
          response: responseFromRequest(requestSnapshot.data()),
          error: null,
          staleObjects: null,
        };
      }
      if (!payloadMismatch && status === "rejected") {
        throwStoredRejection(requestSnapshot.data());
      }
      if (!payloadMismatch && status === "validating") {
        const attemptAt = timestampMillis(requestSnapshot.get("attemptAt"));
        if (attemptAt && nowMs - attemptAt < VALIDATION_TTL_MS) {
          throw new HttpsError(
            "aborted",
            "This upload is already being validated. Retry safely.",
          );
        }
      } else if (!payloadMismatch && status === "reserved") {
        const reservedAt = timestampMillis(requestSnapshot.get("reservedAt"));
        if (reservedAt && nowMs - reservedAt < RESERVATION_TTL_MS) {
          throw new HttpsError(
            "aborted",
            "This upload is already in progress. Retry safely.",
          );
        }

        const oldBytes = positiveNumber(requestSnapshot.get("storageBytes"));
        userUsage = releaseUsage(userUsage, oldBytes);
        noteUsage = releaseUsage(noteUsage, oldBytes);
        globalUsage = releaseUsage(globalUsage, oldBytes);
        releasedStaleReservation = true;
      } else if (!payloadMismatch && status !== "failed") {
        throw new HttpsError("internal", "Invalid upload request state.");
      }

      if (previousAttemptCount >= MAX_REQUEST_ATTEMPTS) {
        throw quotaError(
          "RETRY_LIMIT",
          "This upload request has reached its retry limit.",
        );
      }

      const storagePath = optionalString(requestSnapshot.get("storagePath"));
      if (!payloadMismatch && storagePath) {
        staleObjects = {
          storagePath,
          thumbnailPath: optionalString(
            requestSnapshot.get("thumbnailPath"),
          ) ?? storagePath,
        };
      }
    }

    const userData = userSnapshot.data();
    const globalData = globalSnapshot.data();
    const dailyAttempts = userData?.dailyKey === dayKey ?
      positiveNumber(userData.dailyAttempts) : 0;
    const monthlyAttempts = userData?.monthlyKey === monthKey ?
      positiveNumber(userData.monthlyAttempts) : 0;
    const globalDailyAttempts = globalData?.dailyKey === dayKey ?
      positiveNumber(globalData.dailyAttempts) : 0;

    if (globalData?.uploadsEnabled === false) {
      throw quotaError("GLOBAL_LIMIT", "Photo uploads are temporarily disabled.");
    }
    if (dailyAttempts >= ACCOUNT_DAILY_LIMIT) {
      throw quotaError("DAILY_LIMIT", "Daily upload limit reached.");
    }
    if (monthlyAttempts >= ACCOUNT_MONTHLY_LIMIT) {
      throw quotaError("MONTHLY_LIMIT", "Monthly upload limit reached.");
    }
    if (globalDailyAttempts >= GLOBAL_DAILY_LIMIT) {
      throw quotaError("GLOBAL_LIMIT", "Global daily photo limit reached.");
    }

    transaction.set(refs.userUsage, {
      ...userUsage,
      dailyKey: dayKey,
      dailyAttempts: dailyAttempts + 1,
      monthlyKey: monthKey,
      monthlyAttempts: monthlyAttempts + 1,
      updatedAt: now,
    }, { merge: true });
    transaction.set(refs.globalUsage, {
      ...globalUsage,
      dailyKey: dayKey,
      dailyAttempts: globalDailyAttempts + 1,
      updatedAt: now,
    }, { merge: true });
    if (payloadMismatch) {
      transaction.set(refs.request, {
        attemptCount: previousAttemptCount + 1,
        lastPayloadMismatchAt: now,
        updatedAt: now,
      }, { merge: true });
      return {
        response: null,
        error: invalidArgument(
          "PAYLOAD_MISMATCH",
          "Request id was already used for a different image.",
        ),
        staleObjects: null,
      };
    }
    if (releasedStaleReservation) {
      if (isEmptyUsage(noteUsage)) {
        transaction.delete(refs.noteUsage);
      } else {
        transaction.set(refs.noteUsage, {
          ...noteUsage,
          updatedAt: now,
        }, { merge: true });
      }
    }

    const nextRequest = {
      status: "validating",
      uid,
      ...location,
      requestId,
      payloadHash,
      attemptCount: previousAttemptCount + 1,
      attemptAt: now,
      updatedAt: now,
    };
    if (requestSnapshot.exists) {
      transaction.set(refs.request, {
        ...nextRequest,
        reservedAt: FieldValue.delete(),
        reservationToken: FieldValue.delete(),
        failedAt: FieldValue.delete(),
        failureCode: FieldValue.delete(),
        failureReason: FieldValue.delete(),
      }, { merge: true });
    } else {
      transaction.create(refs.request, {
        ...nextRequest,
        createdAt: now,
      });
    }
    return { response: null, error: null, staleObjects };
  });
}

async function markRequestRejected(
  requestRef: FirebaseFirestore.DocumentReference,
  error: HttpsError,
): Promise<void> {
  const reason = errorReason(error);
  await requestRef.set({
    status: "rejected",
    failureCode: error.code,
    failureReason: reason,
    rejectedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });
}

function throwStoredRejection(
  data: FirebaseFirestore.DocumentData | undefined,
): never {
  const reason = optionalString(data?.failureReason) ?? "MALFORMED";
  if (data?.failureCode === "resource-exhausted") {
    throw quotaError(reason, "This upload request exceeded a quota.");
  }
  throw invalidArgument(reason, "This upload request was rejected.");
}

async function reserveUpload(args: {
  uid: string;
  location: PhotoLocation;
  requestId: string;
  photoId: string;
  storagePath: string;
  thumbnailPath: string;
  storageBytes: number;
  prepared: PreparedPhoto;
}): Promise<ReservationResult> {
  const {
    uid,
    location,
    requestId,
    photoId,
    storagePath,
    thumbnailPath,
    storageBytes,
    prepared,
  } = args;
  const refs = references(location, uid, requestId);
  const now = Timestamp.now();
  const nowMs = now.toMillis();

  return db().runTransaction(async (transaction) => {
    const requestSnapshot = await transaction.get(refs.request);
    const listSnapshot = await transaction.get(refs.list);
    const noteSnapshot = await transaction.get(refs.note);
    const userSnapshot = await transaction.get(refs.userUsage);
    const noteUsageSnapshot = await transaction.get(refs.noteUsage);
    const globalSnapshot = await transaction.get(refs.globalUsage);

    if (!listSnapshot.exists || !isListMember(uid, location.ownerId, listSnapshot.data())) {
      throw new HttpsError("permission-denied", "List access denied.");
    }
    if (!noteSnapshot.exists) throw new HttpsError("not-found", "Note not found.");

    let userUsage = readUsage(userSnapshot.data());
    let noteUsage = readUsage(noteUsageSnapshot.data());
    let globalUsage = readUsage(globalSnapshot.data());

    if (requestSnapshot.exists) {
      assertSameRequest(requestSnapshot.data(), location);
      const status = requestSnapshot.get("status");
      if (status === "completed") {
        return {
          kind: "completed",
          response: responseFromRequest(requestSnapshot.data()),
        };
      }
      if (status === "reserved") {
        const reservedAt = timestampMillis(requestSnapshot.get("reservedAt"));
        if (reservedAt && nowMs - reservedAt < RESERVATION_TTL_MS) {
          throw new HttpsError(
            "aborted",
            "This upload is already in progress. Retry with the same request id.",
          );
        }
        const oldBytes = positiveNumber(requestSnapshot.get("storageBytes"));
        userUsage = releaseUsage(userUsage, oldBytes);
        noteUsage = releaseUsage(noteUsage, oldBytes);
        globalUsage = releaseUsage(globalUsage, oldBytes);
      }
    }

    const globalData = globalSnapshot.data();

    if (globalData?.uploadsEnabled === false) {
      throw quotaError("GLOBAL_LIMIT", "Photo uploads are temporarily disabled.");
    }
    if (noteUsage.activeCount + noteUsage.reservedCount >= NOTE_PHOTO_LIMIT) {
      throw quotaError("NOTE_LIMIT", "This note already has the maximum number of photos.");
    }
    if (userUsage.activeCount + userUsage.reservedCount >= ACCOUNT_PHOTO_LIMIT) {
      throw quotaError("ACCOUNT_COUNT", "Account photo limit reached.");
    }
    if (userUsage.activeBytes + userUsage.reservedBytes + storageBytes > ACCOUNT_BYTES_LIMIT) {
      throw quotaError("ACCOUNT_BYTES", "Account photo storage limit reached.");
    }
    if (globalUsage.activeBytes + globalUsage.reservedBytes + storageBytes > GLOBAL_BYTES_LIMIT) {
      throw quotaError("GLOBAL_LIMIT", "Global photo limit reached.");
    }

    const token = randomUUID();
    transaction.set(refs.userUsage, {
      ...reserveUsage(userUsage, storageBytes),
      updatedAt: now,
    }, { merge: true });
    transaction.set(refs.noteUsage, {
      ...reserveUsage(noteUsage, storageBytes),
      updatedAt: now,
    }, { merge: true });
    transaction.set(refs.globalUsage, {
      ...reserveUsage(globalUsage, storageBytes),
      updatedAt: now,
    }, { merge: true });
    transaction.set(refs.request, {
      status: "reserved",
      uid,
      ...location,
      requestId,
      photoId,
      storagePath,
      thumbnailPath,
      byteSize: prepared.original.length,
      storageBytes,
      width: prepared.width,
      height: prepared.height,
      reservationToken: token,
      reservedAt: now,
      updatedAt: now,
    }, { merge: true });
    return { kind: "reserved", token };
  });
}

async function writeStorageObjects(args: {
  uid: string;
  location: PhotoLocation;
  photoId: string;
  storagePath: string;
  thumbnailPath: string;
  prepared: PreparedPhoto;
}): Promise<void> {
  const { uid, location, photoId, storagePath, thumbnailPath, prepared } = args;
  const customMetadata = {
    uploaderId: uid,
    ownerId: location.ownerId,
    listId: location.listId,
    noteId: location.noteId,
    photoId,
  };
  const metadata = {
    contentType: "image/jpeg",
    cacheControl: "private,max-age=604800,immutable",
    metadata: customMetadata,
  };
  await bucket().file(storagePath).save(prepared.original, {
    resumable: false,
    metadata,
  });
  await bucket().file(thumbnailPath).save(prepared.thumbnail, {
    resumable: false,
    metadata,
  });
}

async function completeUpload(args: {
  uid: string;
  location: PhotoLocation;
  requestId: string;
  photoId: string;
  storagePath: string;
  thumbnailPath: string;
  storageBytes: number;
  prepared: PreparedPhoto;
  reservationToken: string;
}): Promise<PhotoResponse> {
  const {
    uid,
    location,
    requestId,
    photoId,
    storagePath,
    thumbnailPath,
    storageBytes,
    prepared,
    reservationToken,
  } = args;
  const refs = references(location, uid, requestId);
  const photoRef = photoDocument(location, photoId);
  const now = Timestamp.now();
  const response: PhotoResponse = {
    photoId,
    storagePath,
    thumbnailPath,
    byteSize: prepared.original.length,
    width: prepared.width,
    height: prepared.height,
    createdAtEpochMs: now.toMillis(),
    uploaderId: uid,
  };

  return db().runTransaction(async (transaction) => {
    const requestSnapshot = await transaction.get(refs.request);
    const listSnapshot = await transaction.get(refs.list);
    const noteSnapshot = await transaction.get(refs.note);
    const userSnapshot = await transaction.get(refs.userUsage);
    const noteUsageSnapshot = await transaction.get(refs.noteUsage);
    const globalSnapshot = await transaction.get(refs.globalUsage);

    if (!listSnapshot.exists ||
        !isListMember(uid, location.ownerId, listSnapshot.data())) {
      throw new HttpsError("permission-denied", "List access denied.");
    }
    if (
      !requestSnapshot.exists ||
      requestSnapshot.get("status") !== "reserved" ||
      requestSnapshot.get("reservationToken") !== reservationToken
    ) {
      throw new HttpsError("aborted", "Upload reservation expired. Retry safely.");
    }
    if (!noteSnapshot.exists) throw new HttpsError("not-found", "Note not found.");

    transaction.set(refs.userUsage, {
      ...activateUsage(readUsage(userSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    const nextNoteUsage = activateUsage(readUsage(noteUsageSnapshot.data()), storageBytes);
    transaction.set(refs.noteUsage, {
      ...nextNoteUsage,
      updatedAt: now,
    }, { merge: true });
    transaction.set(refs.globalUsage, {
      ...activateUsage(readUsage(globalSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    // Readable badge for the notes list; clients cannot write this field.
    transaction.set(refs.note, { photoCount: nextNoteUsage.activeCount }, { merge: true });
    transaction.create(photoRef, {
      status: "ready",
      storagePath,
      thumbnailPath,
      byteSize: prepared.original.length,
      storageBytes,
      width: prepared.width,
      height: prepared.height,
      uploaderId: uid,
      createdAt: now,
    });
    transaction.set(refs.request, {
      ...response,
      status: "completed",
      completedAt: now,
      reservedAt: FieldValue.delete(),
      reservationToken: FieldValue.delete(),
      updatedAt: now,
    }, { merge: true });
    return response;
  });
}

async function releaseReservation(
  requestRef: FirebaseFirestore.DocumentReference,
  reservationToken: string,
): Promise<boolean> {
  return db().runTransaction(async (transaction) => {
    const requestSnapshot = await transaction.get(requestRef);
    if (
      !requestSnapshot.exists ||
      requestSnapshot.get("status") !== "reserved" ||
      requestSnapshot.get("reservationToken") !== reservationToken
    ) return false;

    const data = requestSnapshot.data() ?? {};
    const location: PhotoLocation = {
      ownerId: requiredStoredString(data.ownerId),
      listId: requiredStoredString(data.listId),
      noteId: requiredStoredString(data.noteId),
    };
    const uid = requiredStoredString(data.uid);
    const storageBytes = positiveNumber(data.storageBytes);
    const refs = references(location, uid, requiredStoredString(data.requestId));
    const userSnapshot = await transaction.get(refs.userUsage);
    const noteUsageSnapshot = await transaction.get(refs.noteUsage);
    const globalSnapshot = await transaction.get(refs.globalUsage);
    const now = Timestamp.now();

    transaction.set(refs.userUsage, {
      ...releaseUsage(readUsage(userSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    const nextNoteUsage = releaseUsage(
      readUsage(noteUsageSnapshot.data()),
      storageBytes,
    );
    if (isEmptyUsage(nextNoteUsage)) {
      transaction.delete(refs.noteUsage);
    } else {
      transaction.set(refs.noteUsage, {
        ...nextNoteUsage,
        updatedAt: now,
      }, { merge: true });
    }
    transaction.set(refs.globalUsage, {
      ...releaseUsage(readUsage(globalSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    transaction.set(requestRef, {
      status: "failed",
      failedAt: now,
      reservedAt: FieldValue.delete(),
      reservationToken: FieldValue.delete(),
      updatedAt: now,
    }, { merge: true });
    return true;
  });
}

async function removePhotoRecord(
  location: PhotoLocation,
  photoRef: FirebaseFirestore.DocumentReference,
  authorizedUid?: string,
): Promise<{ storagePath: string; thumbnailPath: string } | null> {
  return db().runTransaction(async (transaction) => {
    const photoSnapshot = await transaction.get(photoRef);
    if (!photoSnapshot.exists) return null;

    // The note is gone when this runs from the note deletion trigger.
    const noteRef = noteDocument(location);
    const noteSnapshot = await transaction.get(noteRef);
    const uploaderId = requiredStoredString(photoSnapshot.get("uploaderId"));
    if (authorizedUid && authorizedUid !== uploaderId) {
      const listSnapshot = await transaction.get(listDocument(location));
      if (!listSnapshot.exists ||
          !isListMember(authorizedUid, location.ownerId, listSnapshot.data())) {
        throw new HttpsError("permission-denied", "List access denied.");
      }
      if (!noteSnapshot.exists) {
        throw new HttpsError("not-found", "Note not found.");
      }
    }

    const storagePath = requiredStoredString(photoSnapshot.get("storagePath"));
    const thumbnailPath = optionalString(photoSnapshot.get("thumbnailPath")) ?? storagePath;
    const storageBytes = positiveNumber(photoSnapshot.get("storageBytes")) ||
      positiveNumber(photoSnapshot.get("byteSize"));
    const userRef = db().collection(USER_USAGE).doc(uploaderId);
    const noteUsageRef = noteRef.collection(INTERNAL).doc(NOTE_USAGE_DOCUMENT);
    const globalRef = db().collection(GLOBAL_USAGE).doc("global");
    const userSnapshot = await transaction.get(userRef);
    const noteUsageSnapshot = await transaction.get(noteUsageRef);
    const globalSnapshot = await transaction.get(globalRef);
    const now = Timestamp.now();

    transaction.set(userRef, {
      ...removeActiveUsage(readUsage(userSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    const nextNoteUsage = removeActiveUsage(
      readUsage(noteUsageSnapshot.data()),
      storageBytes,
    );
    if (isEmptyUsage(nextNoteUsage)) {
      transaction.delete(noteUsageRef);
    } else {
      transaction.set(noteUsageRef, {
        ...nextNoteUsage,
        updatedAt: now,
      }, { merge: true });
    }
    if (noteSnapshot.exists) {
      transaction.set(noteRef, { photoCount: nextNoteUsage.activeCount }, { merge: true });
    }
    transaction.set(globalRef, {
      ...removeActiveUsage(readUsage(globalSnapshot.data()), storageBytes),
      updatedAt: now,
    }, { merge: true });
    transaction.delete(photoRef);
    return { storagePath, thumbnailPath };
  });
}

async function deleteStorageObjects(
  storagePath: string,
  thumbnailPath: string,
): Promise<void> {
  const paths = [...new Set([storagePath, thumbnailPath].filter(Boolean))];
  await Promise.all(paths.map(async (path) => {
    try {
      await bucket().file(path).delete({ ignoreNotFound: true });
    } catch (error) {
      logger.error("Could not delete note photo object", {
        path,
        error: errorMessage(error),
      });
    }
  }));
}

function references(location: PhotoLocation, uid: string, requestId: string) {
  const note = noteDocument(location);
  return {
    list: listDocument(location),
    note,
    request: db().collection(REQUESTS).doc(`${uid}_${requestId}`),
    userUsage: db().collection(USER_USAGE).doc(uid),
    noteUsage: note.collection(INTERNAL).doc(NOTE_USAGE_DOCUMENT),
    globalUsage: db().collection(GLOBAL_USAGE).doc("global"),
  };
}

function listDocument(location: PhotoLocation): FirebaseFirestore.DocumentReference {
  return db().collection(USERS).doc(location.ownerId).collection(LISTS).doc(location.listId);
}

function noteDocument(location: PhotoLocation): FirebaseFirestore.DocumentReference {
  return listDocument(location).collection(NOTES).doc(location.noteId);
}

function photoDocument(
  location: PhotoLocation,
  photoId: string,
): FirebaseFirestore.DocumentReference {
  return noteDocument(location).collection(PHOTOS).doc(photoId);
}

function db(): FirebaseFirestore.Firestore {
  return admin.firestore();
}

function bucket() {
  return admin.storage().bucket();
}

function storageObjectPath(
  location: PhotoLocation,
  photoId: string,
  fileName: string,
): string {
  return [
    "note-images",
    location.ownerId,
    location.listId,
    location.noteId,
    photoId,
    fileName,
  ].join("/");
}

function stablePhotoId(uid: string, requestId: string): string {
  return createHash("sha256").update(`${uid}:${requestId}`).digest("hex").slice(0, 32);
}

function isListMember(
  uid: string,
  ownerId: string,
  list: FirebaseFirestore.DocumentData | undefined,
): boolean {
  if (uid === ownerId) return true;
  const contributors = Array.isArray(list?.contributors) ? list.contributors : [];
  return contributors.includes(uid);
}

function readUsage(data: FirebaseFirestore.DocumentData | undefined): Usage {
  return {
    activeCount: positiveNumber(data?.activeCount),
    activeBytes: positiveNumber(data?.activeBytes),
    reservedCount: positiveNumber(data?.reservedCount),
    reservedBytes: positiveNumber(data?.reservedBytes),
  };
}

function reserveUsage(usage: Usage, bytes: number): Usage {
  return {
    ...usage,
    reservedCount: usage.reservedCount + 1,
    reservedBytes: usage.reservedBytes + bytes,
  };
}

function releaseUsage(usage: Usage, bytes: number): Usage {
  return {
    ...usage,
    reservedCount: Math.max(0, usage.reservedCount - 1),
    reservedBytes: Math.max(0, usage.reservedBytes - bytes),
  };
}

function activateUsage(usage: Usage, bytes: number): Usage {
  const released = releaseUsage(usage, bytes);
  return {
    ...released,
    activeCount: released.activeCount + 1,
    activeBytes: released.activeBytes + bytes,
  };
}

function removeActiveUsage(usage: Usage, bytes: number): Usage {
  return {
    ...usage,
    activeCount: Math.max(0, usage.activeCount - 1),
    activeBytes: Math.max(0, usage.activeBytes - bytes),
  };
}

function isEmptyUsage(usage: Usage): boolean {
  return usage.activeCount === 0 &&
    usage.activeBytes === 0 &&
    usage.reservedCount === 0 &&
    usage.reservedBytes === 0;
}

function responseFromRequest(data: FirebaseFirestore.DocumentData | undefined): PhotoResponse {
  if (!data) throw new HttpsError("internal", "Invalid upload request state.");
  return {
    photoId: requiredStoredString(data.photoId),
    storagePath: requiredStoredString(data.storagePath),
    thumbnailPath: requiredStoredString(data.thumbnailPath),
    byteSize: requiredPositiveNumber(data.byteSize),
    width: requiredPositiveNumber(data.width),
    height: requiredPositiveNumber(data.height),
    createdAtEpochMs: requiredPositiveNumber(data.createdAtEpochMs),
    uploaderId: requiredStoredString(data.uploaderId),
  };
}

function assertSameRequest(
  data: FirebaseFirestore.DocumentData | undefined,
  location: PhotoLocation,
): void {
  if (
    data?.ownerId !== location.ownerId ||
    data?.listId !== location.listId ||
    data?.noteId !== location.noteId
  ) {
    throw invalidArgument("MALFORMED", "Request id was already used elsewhere.");
  }
}

function timestampMillis(value: unknown): number {
  return value instanceof Timestamp ? value.toMillis() : 0;
}

function positiveNumber(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : 0;
}

function requiredPositiveNumber(value: unknown): number {
  const number = positiveNumber(value);
  if (!number) throw new HttpsError("internal", "Invalid stored photo metadata.");
  return number;
}

function requiredStoredString(value: unknown): string {
  const string = optionalString(value);
  if (!string) throw new HttpsError("internal", "Invalid stored photo metadata.");
  return string;
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function asRecord(value: unknown): UnknownRecord {
  const record = asOptionalRecord(value);
  if (!record) throw invalidArgument("MALFORMED", "Invalid request.");
  return record;
}

function asOptionalRecord(value: unknown): UnknownRecord | null {
  return typeof value === "object" && value != null && !Array.isArray(value) ?
    value as UnknownRecord : null;
}

function invalidArgument(reason: string, message: string): HttpsError {
  return new HttpsError("invalid-argument", message, { reason });
}

function quotaError(reason: string, message: string): HttpsError {
  return new HttpsError("resource-exhausted", message, { reason });
}

function errorReason(error: HttpsError): string {
  const details = asOptionalRecord(error.details);
  return optionalString(details?.reason) ?? "MALFORMED";
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
