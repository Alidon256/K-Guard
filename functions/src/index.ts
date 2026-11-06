import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
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

// ---------------------------------------------------------------- pairing & OTP
export const createPairingCode = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.role !== "parent" || !user.familyId) throw new HttpsError("permission-denied", "Parents only.");
  const childId = String(req.data?.childId ?? "");
  const childDoc = await db.doc(`families/${user.familyId}/children/${childId}`).get();
  if (!childDoc.exists) throw new HttpsError("not-found", "Child profile not found.");
  const childData = childDoc.data() ?? {};

  const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
  await db.doc(`pairingCodes/${code}`).set({
    familyId: user.familyId,
    childId,
    childEmail: childData.email ?? req.data?.email ?? null,
    createdBy: uid,
    expiresAt: Timestamp.fromMillis(Date.now() + 15 * 60 * 1000),
  });
  return { code, expiresInMinutes: 15 };
});

export const sendChildOtp = createPairingCode;

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

// ---------------------------------------------------------------- app approvals
export const requestAppApproval = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (!user.familyId) throw new HttpsError("failed-precondition", "No family found.");

  const familyId = user.familyId;
  const childId = user.role === "child" ? user.childId : String(req.data?.childId ?? "");
  if (!childId) throw new HttpsError("invalid-argument", "childId is required.");

  const [famSnap, childSnap] = await Promise.all([
    db.doc(`families/${familyId}`).get(),
    db.doc(`families/${familyId}/children/${childId}`).get(),
  ]);
  const family = famSnap.data() as any;
  const child = childSnap.data() as any;
  if (!family || !child) throw new HttpsError("not-found", "Child profile not found.");

  const appName = String(req.data?.appName ?? "").trim().slice(0, 100);
  const packageName = String(req.data?.packageName ?? "").trim().toLowerCase().slice(0, 150);
  if (!appName || !packageName) throw new HttpsError("invalid-argument", "appName and packageName are required.");

  const category = String(req.data?.category ?? "General").trim().slice(0, 50);
  const minAgeRating = Number(req.data?.minAgeRating ?? 3);
  const dailyLimitMinutes = Number(req.data?.dailyLimitMinutes ?? 30);

  // Compute suitability assessment based on child's age profile
  const maxAgeMap: Record<string, number> = {
    KIDS_3_7: 7,
    PRETEENS_8_12: 12,
    TEENS_13_17: 17,
    ADULTS_18_PLUS: 99,
  };
  const childMaxAge = maxAgeMap[child.ageProfile] ?? 7;
  let safetyScore: number;
  let safetyReason: string;

  if (minAgeRating > childMaxAge) {
    safetyScore = Math.max(25, 100 - (minAgeRating - childMaxAge) * 18);
    safetyReason = `Age rating (${minAgeRating}+) exceeds recommended age bracket for ${child.name} (${String(child.ageProfile).replace(/_/g, " ")}). Requires parental approval.`;
  } else {
    safetyScore = Math.min(98, 85 + (minAgeRating % 10));
    safetyReason = `Age rating (${minAgeRating}+) is within the recommended bracket for ${child.name}. Requires parental sign-off before installation.`;
  }

  const appId = `${childId}_${packageName.replace(/[^a-zA-Z0-9_-]/g, "_")}`;
  const appRef = db.doc(`families/${familyId}/apps/${appId}`);
  const existingSnap = await appRef.get();
  const existing = existingSnap.data() as any;

  if (existing && existing.approvalStatus === "APPROVED") {
    return {
      appId,
      approvalStatus: "APPROVED",
      message: `${appName} is already approved for ${child.name}.`,
      appName,
      packageName,
    };
  }

  const payload = {
    id: appId,
    childId,
    childName: child.name,
    childLinkedUid: child.linkedUid ?? (user.role === "child" ? uid : null),
    appName,
    packageName,
    category,
    minAgeRating,
    safetyScore,
    safetyReason,
    approvalStatus: "PENDING_REVIEW",
    dailyLimitMinutes: existing?.dailyLimitMinutes ?? dailyLimitMinutes,
    requestedAt: FieldValue.serverTimestamp(),
    reviewedAt: null,
    reviewedBy: null,
    notified: false,
  };

  await appRef.set(payload, { merge: true });

  return {
    appId,
    approvalStatus: "PENDING_REVIEW",
    message: `Approval requested for ${appName}. Your parent will be notified.`,
    safetyScore,
    safetyReason,
  };
});

export const respondToAppApproval = onCall(async (req) => {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.role !== "parent" || !user.familyId) {
    throw new HttpsError("permission-denied", "Parents only.");
  }
  const familyId = user.familyId;
  const appId = String(req.data?.appId ?? "").trim();
  const decision = String(req.data?.decision ?? "").toUpperCase();
  if (decision !== "APPROVED" && decision !== "REJECTED") {
    throw new HttpsError("invalid-argument", "decision must be APPROVED or REJECTED.");
  }
  const dailyLimitMinutes = req.data?.dailyLimitMinutes !== undefined ? Number(req.data.dailyLimitMinutes) : undefined;

  const appRef = db.doc(`families/${familyId}/apps/${appId}`);
  const snap = await appRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "App request not found.");
  const appData = snap.data() as any;

  const updateData: Record<string, any> = {
    approvalStatus: decision,
    reviewedAt: FieldValue.serverTimestamp(),
    reviewedBy: uid,
  };
  if (dailyLimitMinutes !== undefined && !Number.isNaN(dailyLimitMinutes)) {
    updateData.dailyLimitMinutes = dailyLimitMinutes;
  }

  await appRef.update(updateData);

  // Send push notification to the child device if linked
  if (appData.childLinkedUid) {
    const childUser = await userDoc(appData.childLinkedUid);
    const tokens: string[] = childUser.fcmTokens ?? [];
    if (tokens.length > 0) {
      const isApproved = decision === "APPROVED";
      await getMessaging().sendEachForMulticast({
        tokens,
        notification: {
          title: isApproved ? `App Approved: ${appData.appName}` : `App Request Declined`,
          body: isApproved
            ? `Your parent approved ${appData.appName}. You can now install and use it!`
            : `Your request to install ${appData.appName} was not approved.`,
        },
        data: {
          familyId,
          appId,
          packageName: appData.packageName,
          decision,
          type: "app_approval_response",
        },
        android: { priority: "high", notification: { channelId: "safety_alerts" } },
      }).catch(() => undefined);
    }
  }

  return { ok: true, appId, status: decision };
});

export const onAppApprovalWritten = onDocumentWritten("families/{familyId}/apps/{appId}", async (event) => {
  const after = event.data?.after?.data();
  const before = event.data?.before?.data();
  if (!after) return;

  const { familyId, appId } = event.params;

  const isNewPending = after.approvalStatus === "PENDING_REVIEW" && (!before || before.approvalStatus !== "PENDING_REVIEW");
  if (!isNewPending) return;

  const famDoc = await db.doc(`families/${familyId}`).get();
  const fam = famDoc.data() as any;
  if (!fam) return;

  const tokens: string[] = [];
  const owners: Record<string, string[]> = {};
  for (const pid of (fam.parentUids as string[] ?? [])) {
    const u = (await db.doc(`users/${pid}`).get()).data() as any;
    for (const t of u?.fcmTokens ?? []) {
      tokens.push(t);
      (owners[pid] ??= []).push(t);
    }
  }
  if (tokens.length === 0) return;

  const childName = after.childName || "Your child";
  const appName = after.appName || "an application";
  const category = after.category || "App";
  const minAge = after.minAgeRating || 0;

  const title = `App Install Request: ${appName}`;
  const body = `${childName} wants to install ${appName} (${category} • Age ${minAge}+). Tap to review.`;

  const res = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: {
      familyId,
      appId,
      childId: after.childId || "",
      childName,
      appName,
      packageName: after.packageName || "",
      type: "app_approval",
      status: "PENDING_REVIEW",
    },
    android: {
      priority: "high",
      notification: {
        channelId: "safety_alerts",
      },
    },
  });

  const dead = new Set<string>();
  res.responses.forEach((r, i) => {
    const code = r.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      dead.add(tokens[i]);
    }
  });
  if (dead.size) {
    for (const [pid, list] of Object.entries(owners)) {
      const bad = list.filter((t) => dead.has(t));
      if (bad.length) await db.doc(`users/${pid}`).update({ fcmTokens: FieldValue.arrayRemove(...bad) });
    }
  }

  await event.data?.after?.ref.update({ notified: res.successCount > 0 });
});

/**
 * Remote commands are written by the parent app to `families/{familyId}/commands/{commandId}`.
 * This trigger is the pipe that actually delivers them to the child device: it forwards every
 * new command as a data-only FCM push to the child's linked device, keeping the child app's
 * existing `remote_lock` contract intact.
 */
export const onCommandCreated = onDocumentCreated("families/{familyId}/commands/{commandId}", async (event) => {
  const cmd = event.data?.data();
  const { familyId, commandId } = event.params;
  if (!cmd) return;

  const childId: string = cmd.childId ?? "";
  if (!childId) return;

  const childSnap = await db.doc(`families/${familyId}/children/${childId}`).get();
  const linkedUid = childSnap.data()?.linkedUid as string | undefined;
  if (!linkedUid) {
    // Unpaired child: nothing to push to. The command stays "Sent" and the parent UI can
    // surface "device not linked" — never fake a delivery.
    await event.data?.ref.update({ deliveryError: "child not linked" });
    return;
  }

  const childUser = await userDoc(linkedUid);
  const tokens: string[] = childUser.fcmTokens ?? [];
  if (tokens.length === 0) {
    await event.data?.ref.update({ deliveryError: "child device has no FCM token" });
    return;
  }

  const action: string = cmd.action ?? "";
  const isLock = action === "switch_off_lock";

  const res = await getMessaging().sendEachForMulticast({
    tokens,
    // Data-only (no notification payload) so onMessageReceived always fires and the child
    // app can execute silently — lock screens and alarms must not be user-gated.
    data: {
      familyId,
      commandId,
      childId,
      childName: cmd.childName ?? "",
      type: isLock ? "remote_lock" : "remote_command",
      action,
      description: cmd.description ?? "",
      reason: cmd.reason ?? "",
    },
    android: { priority: "high" },
  });

  const dead = new Set<string>();
  res.responses.forEach((r, i) => {
    const code = r.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      dead.add(tokens[i]);
    }
  });
  if (dead.size) {
    await db.doc(`users/${linkedUid}`).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  }

  await event.data?.ref.update({
    pushedAt: FieldValue.serverTimestamp(),
    deliveryError: res.successCount > 0 ? "" : "FCM delivery failed",
  });
});

function sanitiseRules(raw: unknown): Partial<Record<Category, Rule>> {  const out: Partial<Record<Category, Rule>> = {};
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
