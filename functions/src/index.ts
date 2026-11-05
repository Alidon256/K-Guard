import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { randomInt } from "node:crypto";

import { analyzeImage, analyzeText, Analysis } from "./engine";
import { AGE_PROFILES, AgeProfile, Category, CATEGORIES, LABEL, REVEAL_ALLOWED, resolvePolicy, Rule } from "./policy";

initializeApp();
const db = getFirestore();

// Configure in functions/.env.k-guarducc (git-ignored). Empty URL => built-in rule engine for text, REVIEW for images.

const DEFAULT_PRIVACY = {
  notifyOn: "warn_and_above", // "all" | "warn_and_above" | "block_only"
  includeSnippet: false,
  retainDays: 30,
  quietHours: { enabled: false, start: "21:00", end: "07:00", utcOffsetMinutes: 0 },
};

function requireAuth(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in to continue.");
  return req.auth.uid;
}

async function userDoc(uid: string) {
  const snap = await db.doc(`users/${uid}`).get();
  return snap.exists ? (snap.data() as any) : {};
}

// ---------------------------------------------------------------- createFamily
export const createFamily = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.familyId) return { familyId: user.familyId, role: user.role };

  const famRef = db.collection("families").doc();
  await db.runTransaction(async (tx) => {
    tx.set(famRef, {
      ownerUid: uid,
      parentUids: [uid],
      privacy: DEFAULT_PRIVACY,
      createdAt: FieldValue.serverTimestamp(),
    });
    tx.set(
      db.doc(`users/${uid}`),
      {
        role: "parent",
        familyId: famRef.id,
        displayName: req.auth?.token.name ?? null,
        fcmTokens: [],
      },
      { merge: true },
    );
  });
  return { familyId: famRef.id, role: "parent" };
});

// ---------------------------------------------------------------- pairing
export const createPairingCode = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.role !== "parent" || !user.familyId) throw new HttpsError("permission-denied", "Parents only.");
  const childId = String(req.data?.childId ?? "");
  const child = await db.doc(`families/${user.familyId}/children/${childId}`).get();
  if (!child.exists) throw new HttpsError("not-found", "Child profile not found.");

  const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
  await db.doc(`pairingCodes/${code}`).set({
    familyId: user.familyId,
    childId,
    createdBy: uid,
    expiresAt: Timestamp.fromMillis(Date.now() + 15 * 60 * 1000),
  });
  return { code, expiresInMinutes: 15 };
});

export const redeemPairingCode = onCall(async (req) => {
  const uid = requireAuth(req);
  const code = String(req.data?.code ?? "").trim();
  if (!/^\d{6}$/.test(code)) throw new HttpsError("invalid-argument", "Enter the 6-digit code.");
  const ref = db.doc(`pairingCodes/${code}`);
  const snap = await ref.get();
  const data = snap.data();
  if (!data || data.expiresAt.toMillis() < Date.now()) {
    if (snap.exists) await ref.delete();
    throw new HttpsError("not-found", "That code is invalid or has expired.");
  }
  const user = await userDoc(uid);
  if (user.role === "parent") throw new HttpsError("failed-precondition", "This account is a parent account.");

  await db.runTransaction(async (tx) => {
    tx.update(db.doc(`families/${data.familyId}/children/${data.childId}`), { linkedUid: uid });
    tx.set(
      db.doc(`users/${uid}`),
      { role: "child", familyId: data.familyId, childId: data.childId, fcmTokens: [] },
      { merge: true },
    );
    tx.delete(ref);
  });
  return { familyId: data.familyId, childId: data.childId };
});

// ---------------------------------------------------------------- moderateContent
export const moderateContent = onCall({ timeoutSeconds: 60, memory: "512MiB" }, async (req) => {
  const uid = requireAuth(req);
  const type = req.data?.type;
  if (type !== "text" && type !== "image") throw new HttpsError("invalid-argument", "type must be text or image.");

  const user = await userDoc(uid);
  if (!user.familyId) throw new HttpsError("failed-precondition", "Create or join a family first.");
  const familyId: string = user.familyId;
  const childId: string = user.role === "child" ? user.childId : String(req.data?.childId ?? "");
  if (!childId) throw new HttpsError("invalid-argument", "childId is required.");

  const [famSnap, childSnap] = await Promise.all([
    db.doc(`families/${familyId}`).get(),
    db.doc(`families/${familyId}/children/${childId}`).get(),
  ]);
  const family = famSnap.data() as any;
  const child = childSnap.data() as any;
  if (!family || !child) throw new HttpsError("not-found", "Child profile not found.");
  if (user.role === "parent" && !family.parentUids.includes(uid)) throw new HttpsError("permission-denied", "Not your family.");

  const ageProfile: AgeProfile = AGE_PROFILES.includes(child.ageProfile) ? child.ageProfile : "KIDS_3_7"; // strictest default
  const rules = sanitiseRules(child.categoryRules);
  const privacy = { ...DEFAULT_PRIVACY, ...(family.privacy ?? {}) };
  const cfg = { url: process.env.MODERATION_API_URL || undefined, key: process.env.MODERATION_API_KEY || undefined };

  let analysis: Analysis | null = null;
  let storagePath: string | undefined;
  let snippet: string | undefined;

  if (type === "text") {
    const text = String(req.data?.text ?? "").trim();
    if (!text) throw new HttpsError("invalid-argument", "Text is empty.");
    if (text.length > 5000) throw new HttpsError("invalid-argument", "Text is too long (max 5000 characters).");
    analysis = await analyzeText(text, ageProfile, cfg);
    if (privacy.includeSnippet) snippet = text.slice(0, 200);
  } else {
    storagePath = String(req.data?.storagePath ?? "");
    const callerUid = user.role === "child" ? uid : uid;
    if (!storagePath.startsWith(`users/${callerUid}/uploads/`) || storagePath.includes("..")) {
      throw new HttpsError("permission-denied", "Invalid image path.");
    }
    const file = getStorage().bucket().file(storagePath);
    const [exists] = await file.exists();
    if (!exists) throw new HttpsError("not-found", "Uploaded image not found.");
    const [bytes] = await file.download();
    const [meta] = await file.getMetadata();
    analysis = await analyzeImage(bytes, String(meta.contentType ?? "image/jpeg"), ageProfile, cfg);
  }

  const policy = analysis
    ? resolvePolicy({ scores: analysis.scores, context: analysis.context, ageProfile, rules })
    : resolvePolicy({ scores: {}, context: "unclear", ageProfile }); // -> REVIEW (fail closed)

  const explanation =
    !analysis && type === "image"
      ? "The image could not be analysed right now, so it is held for a parent to review."
      : policy.explanation;

  const result = {
    decision: policy.decision,
    category: policy.category,
    riskScore: Number(policy.riskScore.toFixed(3)),
    context: analysis?.context ?? "unclear",
    ageAppropriate: policy.ageAppropriate,
    explanation,
    categories: analysis?.scores ?? {},
    source: analysis?.source ?? "none",
    canReveal: policy.decision === "BLUR" && (child.allowReveal ?? REVEAL_ALLOWED[ageProfile]) && REVEAL_ALLOWED[ageProfile],
    eventId: null as string | null,
  };

  // Keep the uploaded image only if a parent may need to review it and has opted in to storing content.
  const keepFile = type === "image" && result.decision !== "ALLOW" && privacy.includeSnippet;
  if (type === "image" && storagePath && !keepFile) {
    getStorage().bucket().file(storagePath).delete().catch(() => undefined);
    storagePath = undefined;
  }

  if (result.decision !== "ALLOW") {
    const ev = await db.collection(`families/${familyId}/events`).add({
      childId,
      childName: child.name,
      ageProfile,
      contentType: type,
      category: result.category,
      riskScore: result.riskScore,
      decision: result.decision,
      context: result.context,
      explanation: result.explanation,
      source: result.source,
      snippet: snippet ?? null,
      storagePath: storagePath ?? null,
      status: "open",
      createdAt: FieldValue.serverTimestamp(),
    });
    result.eventId = ev.id;
  }
  return result;
});

// ---------------------------------------------------------------- web protection (VPN) reports
export const reportWebBlock = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.role !== "parent" || !user.familyId) throw new HttpsError("permission-denied", "Parents only.");
  const familyId: string = user.familyId;
  const childId = String(req.data?.childId ?? "");
  const rawCat = String(req.data?.category ?? "custom");
  const domain = String(req.data?.domain ?? "").toLowerCase().slice(0, 253);
  if (!/^[a-z0-9.-]+$/.test(domain)) throw new HttpsError("invalid-argument", "Invalid domain.");

  const [famSnap, childSnap] = await Promise.all([
    db.doc(`families/${familyId}`).get(),
    db.doc(`families/${familyId}/children/${childId}`).get(),
  ]);
  const family = famSnap.data() as any;
  const child = childSnap.data() as any;
  if (!family || !child || !family.parentUids.includes(uid)) throw new HttpsError("not-found", "Child profile not found.");

  const category = (CATEGORIES as readonly string[]).includes(rawCat) ? (rawCat as Category) : null;
  const privacy = { ...DEFAULT_PRIVACY, ...(family.privacy ?? {}) };
  const what = category ? LABEL[category] : "a site you chose to block";
  await db.collection(`families/${familyId}/events`).add({
    childId,
    childName: child.name,
    ageProfile: child.ageProfile,
    contentType: "web",
    category: category ?? "none",
    riskScore: 1,
    decision: "BLOCK",
    context: "harmful",
    explanation: `Web Protection blocked a visit to ${what}.`,
    source: "dns",
    snippet: privacy.includeSnippet ? domain : null,
    storagePath: null,
    status: "open",
    createdAt: FieldValue.serverTimestamp(),
  });
  return { ok: true };
});

function sanitiseRules(raw: unknown): Partial<Record<Category, Rule>> {
  const out: Partial<Record<Category, Rule>> = {};
  if (!raw || typeof raw !== "object") return out;
  for (const c of CATEGORIES) {
    const v = (raw as any)[c];
    if (v === "block" || v === "blur" || v === "warn" || v === "allow") out[c] = v;
  }
  return out;
}

// ---------------------------------------------------------------- notifications
function inQuietHours(
  q: { enabled?: boolean; start?: string; end?: string; utcOffsetMinutes?: number } | undefined,
  now = new Date(),
): boolean {
  if (!q?.enabled || !q.start || !q.end) return false;
  const toMin = (s: string) => {
    const [h, m] = s.split(":").map(Number);
    return h * 60 + (m || 0);
  };
  const offset = Number.isFinite(q.utcOffsetMinutes) ? (q.utcOffsetMinutes as number) : 0;
  const cur = (((now.getUTCHours() * 60 + now.getUTCMinutes() + offset) % 1440) + 1440) % 1440;
  const s = toMin(q.start), e = toMin(q.end);
  return s <= e ? cur >= s && cur < e : cur >= s || cur < e;
}

export const onEventCreated = onDocumentCreated("families/{familyId}/events/{eventId}", async (event) => {
  const ev = event.data?.data();
  if (!ev) return;
  const { familyId } = event.params;
  const fam = (await db.doc(`families/${familyId}`).get()).data() as any;
  if (!fam) return;
  const privacy = { ...DEFAULT_PRIVACY, ...(fam.privacy ?? {}) };

  const d: string = ev.decision;
  const significant =
    privacy.notifyOn === "all" ? true : privacy.notifyOn === "block_only" ? d === "BLOCK" : d !== "ALLOW" && d !== "WARN" ? true : d === "WARN";
  const ruleOk = privacy.notifyOn === "warn_and_above" ? d !== "ALLOW" : significant;
  if (!ruleOk) return;
  if (inQuietHours(privacy.quietHours) && d !== "BLOCK") return; // BLOCK still gets through

  const tokens: string[] = [];
  const owners: Record<string, string[]> = {};
  for (const pid of fam.parentUids as string[]) {
    const u = (await db.doc(`users/${pid}`).get()).data() as any;
    for (const t of u?.fcmTokens ?? []) {
      tokens.push(t);
      (owners[pid] ??= []).push(t);
    }
  }
  if (tokens.length === 0) return;

  const body = privacy.includeSnippet && ev.snippet
    ? `${ev.childName}: ${ev.explanation}`
    : `${ev.childName}: ${ev.explanation}`; // snippet text itself is never put in a push
  const res = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title: d === "BLOCK" ? "K-Guard blocked content" : "K-Guard safety alert", body },
    data: { familyId, eventId: event.params.eventId, decision: d },
    android: { priority: "high", notification: { channelId: "safety_alerts" } },
  });

  // Remove tokens that are no longer valid.
  const dead = new Set<string>();
  res.responses.forEach((r, i) => {
    const code = r.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.add(tokens[i]);
  });
  if (dead.size) {
    for (const [pid, list] of Object.entries(owners)) {
      const bad = list.filter((t) => dead.has(t));
      if (bad.length) await db.doc(`users/${pid}`).update({ fcmTokens: FieldValue.arrayRemove(...bad) });
    }
  }
  await event.data?.ref.update({ notified: res.successCount > 0 });
});

// ---------------------------------------------------------------- retention
export const purgeOldEvents = onSchedule("every day 03:00", async () => {
  const families = await db.collection("families").get();
  for (const fam of families.docs) {
    const days = Number(fam.data().privacy?.retainDays ?? DEFAULT_PRIVACY.retainDays);
    const cutoff = Timestamp.fromMillis(Date.now() - days * 86_400_000);
    const old = await fam.ref.collection("events").where("createdAt", "<", cutoff).limit(400).get();
    if (old.empty) continue;
    const batch = db.batch();
    for (const doc of old.docs) {
      const p = doc.data().storagePath;
      if (p) await getStorage().bucket().file(p).delete().catch(() => undefined);
      batch.delete(doc.ref);
    }
    await batch.commit();
  }
});
