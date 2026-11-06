import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
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
  if (user.familyId) return { familyId: user.familyId, role: user.role, childId: user.childId ?? null };
  if (req.auth?.token.firebase?.sign_in_provider === "anonymous") {
    throw new HttpsError("permission-denied", "Child devices pair with a code; they don't create families.");
  }

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
  return { familyId: famRef.id, role: "parent", childId: null };
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
  let emptyFamilyToRelease: string | null = null;
  if (user.role === "parent" && user.familyId) {
    // Parents sign in with Google only; only an anonymous child device may release an empty family.
    if (req.auth?.token.firebase?.sign_in_provider !== "anonymous") {
      throw new HttpsError("failed-precondition", "This account is a parent account.");
    }
    // A child device that signed in before entering its code may have been given an empty family.
    // Release it only if nothing was ever set up in it.
    const fam = (await db.doc(`families/${user.familyId}`).get()).data() as any;
    const children = await db.collection(`families/${user.familyId}/children`).limit(1).get();
    const soleOwner = fam && fam.ownerUid === uid && (fam.parentUids ?? []).length === 1;
    if (!soleOwner || !children.empty) {
      throw new HttpsError("failed-precondition", "This account already manages a family. Sign in with the child's own account.");
    }
    emptyFamilyToRelease = user.familyId;
  }

  await db.runTransaction(async (tx) => {
    if (emptyFamilyToRelease) tx.delete(db.doc(`families/${emptyFamilyToRelease}`));
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
  if (!user.familyId) throw new HttpsError("permission-denied", "Create or join a family first.");
  const familyId: string = user.familyId;
  // On a paired child's phone the child id comes from the server-side profile, never from the request.
  const childId = user.role === "child" ? String(user.childId ?? "") : String(req.data?.childId ?? "");
  const rawCat = String(req.data?.category ?? "custom");
  const domain = String(req.data?.domain ?? "").toLowerCase().slice(0, 253);
  if (!/^[a-z0-9.-]+$/.test(domain)) throw new HttpsError("invalid-argument", "Invalid domain.");

  const [famSnap, childSnap] = await Promise.all([
    db.doc(`families/${familyId}`).get(),
    db.doc(`families/${familyId}/children/${childId}`).get(),
  ]);
  const family = famSnap.data() as any;
  const child = childSnap.data() as any;
  const allowed = user.role === "child" ? child?.linkedUid === uid : (family?.parentUids ?? []).includes(uid);
  if (!family || !child || !allowed) throw new HttpsError("not-found", "Child profile not found.");

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

// ---------------------------------------------------------------- web protection status from the child's phone
export const onWebProtectionStatus = onDocumentWritten("families/{familyId}/webProtection/{childId}", async (event) => {
  const before = event.data?.before?.data()?.device ?? {};
  const after = event.data?.after?.data();
  if (!after || !after.enabled) return; // nothing to warn about when the parent has it off
  const dev = after.device ?? {};
  const { familyId, childId } = event.params;

  const turnedOff = dev.turnedOffOnDevice === true && before.turnedOffOnDevice !== true;
  const needsTap = dev.needsConsent === true && before.needsConsent !== true;
  if (!turnedOff && !needsTap) return;

  const [fam, child] = await Promise.all([
    db.doc(`families/${familyId}`).get().then((s) => s.data() as any),
    db.doc(`families/${familyId}/children/${childId}`).get().then((s) => s.data() as any),
  ]);
  if (!fam) return;
  const name = child?.name || "Your child";
  await pushToUsers((fam.parentUids as string[]) ?? [], {
    notification: turnedOff
      ? { title: "Web protection was switched off", body: `Web protection was switched off on ${name}'s phone. K-Guard is turning it back on.` }
      : { title: "Web protection needs one tap", body: `Android needs someone to allow web protection on ${name}'s phone once. A notification is waiting there.` },
    data: { type: "web_protection_status", familyId, childId },
    android: { priority: "high", notification: { channelId: "safety_alerts" } },
  });
});

// ---------------------------------------------------------------- push helper
/** Sends one message to every registered device of the given users and prunes tokens FCM rejects. */
async function pushToUsers(uids: string[], message: Omit<MulticastMessage, "tokens">): Promise<number> {
  const owners: Record<string, string[]> = {};
  const tokens: string[] = [];
  for (const id of new Set(uids)) {
    const u = (await db.doc(`users/${id}`).get()).data() as any;
    for (const t of u?.fcmTokens ?? []) {
      tokens.push(t);
      (owners[id] ??= []).push(t);
    }
  }
  if (tokens.length === 0) return 0;
  const res = await getMessaging().sendEachForMulticast({ ...message, tokens });
  const dead = new Set<string>();
  res.responses.forEach((r, i) => {
    const code = r.error?.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.add(tokens[i]);
  });
  for (const [id, list] of Object.entries(owners)) {
    const bad = list.filter((t) => dead.has(t));
    if (bad.length) await db.doc(`users/${id}`).update({ fcmTokens: FieldValue.arrayRemove(...bad) });
  }
  return res.successCount;
}

function clampInt(raw: unknown, min: number, max: number, fallback: number): number {
  const n = Math.round(Number(raw));
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}

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
  if (user.role === "parent" && !(family.parentUids ?? []).includes(uid)) throw new HttpsError("permission-denied", "Not your family.");
  if (user.role === "child" && child.linkedUid !== uid) throw new HttpsError("permission-denied", "This device is not paired with that child.");

  const appName = String(req.data?.appName ?? "").trim().slice(0, 100);
  const packageName = String(req.data?.packageName ?? "").trim().toLowerCase().slice(0, 150);
  if (!appName || !packageName) throw new HttpsError("invalid-argument", "appName and packageName are required.");
  if (!/^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/.test(packageName)) throw new HttpsError("invalid-argument", "Invalid package name.");

  const category = String(req.data?.category ?? "General").trim().slice(0, 50);
  const minAgeRating = clampInt(req.data?.minAgeRating, 0, 18, 3);
  const dailyLimitMinutes = clampInt(req.data?.dailyLimitMinutes, 0, 1440, 0);
  const source = user.role === "child" ? "device_install" : "parent_added";

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
    safetyReason = `Age rating (${minAgeRating}+) exceeds recommended age bracket for ${child.name} (${String(child.ageProfile).replace(/_/g, " ")}). It stays blocked on ${child.name}'s phone until you approve it.`;
  } else {
    safetyScore = Math.min(98, 85 + (minAgeRating % 10));
    safetyReason = `Age rating (${minAgeRating}+) is within the recommended bracket for ${child.name}. It stays blocked on ${child.name}'s phone until you approve it.`;
  }

  const appId = `${childId}_${packageName.replace(/[^a-zA-Z0-9_-]/g, "_")}`;
  const appRef = db.doc(`families/${familyId}/apps/${appId}`);
  const existingSnap = await appRef.get();
  const existing = existingSnap.data() as any;

  if (existing && existing.installed === false && user.role === "child") {
    await appRef.update({ installed: true, uninstalledAt: null });
    await logAppActivity(familyId, childId, child.name, "installed", packageName, appName, existing.approvalStatus);
  }
  if (existing && existing.approvalStatus === "PENDING_REVIEW") {
    return { appId, approvalStatus: "PENDING_REVIEW", message: `${appName} is already waiting for a parent's decision.` };
  }
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
    source,
    installed: true,
    uninstalledAt: null,
    dailyLimitMinutes: existing?.dailyLimitMinutes ?? dailyLimitMinutes,
    requestedAt: FieldValue.serverTimestamp(),
    reviewedAt: null,
    reviewedBy: null,
    notified: false,
  };

  await appRef.set(payload, { merge: true });
  if (user.role === "child") await logAppActivity(familyId, childId, child.name, "installed", packageName, appName, "PENDING_REVIEW");

  return {
    appId,
    approvalStatus: "PENDING_REVIEW",
    message: `Approval requested for ${appName}. Your parent will be notified.`,
    safetyScore,
    safetyReason,
  };
});

/** The paired child's phone calling: returns its family and child, or throws. */
async function requireChildDevice(req: CallableRequest) {
  const uid = requireAuth(req);
  const user = await userDoc(uid);
  if (user.role !== "child" || !user.familyId || !user.childId) throw new HttpsError("permission-denied", "Paired child devices only.");
  const child = (await db.doc(`families/${user.familyId}/children/${user.childId}`).get()).data() as any;
  if (!child || child.linkedUid !== uid) throw new HttpsError("permission-denied", "This device is not paired.");
  return { uid, familyId: user.familyId as string, childId: user.childId as string, child };
}

function cleanPackage(raw: unknown): string | null {
  const pkg = String(raw ?? "").trim().toLowerCase().slice(0, 150);
  return /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/.test(pkg) ? pkg : null;
}

const appDocId = (childId: string, pkg: string) => `${childId}_${pkg.replace(/[^a-zA-Z0-9_-]/g, "_")}`;

async function logAppActivity(
  familyId: string, childId: string, childName: string,
  type: "installed" | "uninstalled", packageName: string, appName: string, approvalStatus: string | null,
) {
  await db.collection(`families/${familyId}/appActivity`).add({
    childId, childName, type, packageName, appName, approvalStatus,
    at: FieldValue.serverTimestamp(),
  });
}

/**
 * First run on a paired child's phone: records the apps already installed as allowed, so the parent sees
 * the full list and can block any of them, without a notification per app. Never changes existing decisions.
 */
export const syncInstalledApps = onCall(async (req) => {
  const { uid, familyId, childId, child } = await requireChildDevice(req);
  const list = Array.isArray(req.data?.apps) ? req.data.apps.slice(0, 400) : [];
  let added = 0;
  for (let i = 0; i < list.length; i += 100) {
    const chunk = list.slice(i, i + 100);
    const refs = chunk.map((a: any) => cleanPackage(a?.packageName)).map((pkg: string | null) => pkg && db.doc(`families/${familyId}/apps/${appDocId(childId, pkg)}`));
    const valid = refs.filter(Boolean) as FirebaseFirestore.DocumentReference[];
    const snaps = valid.length ? await db.getAll(...valid) : [];
    const exists = new Set(snaps.filter((x) => x.exists).map((x) => x.id));
    const batch = db.batch();
    chunk.forEach((a: any, j: number) => {
      const ref = refs[j];
      if (!ref || exists.has(ref.id)) return;
      batch.set(ref, {
        id: ref.id,
        childId,
        childName: child.name,
        childLinkedUid: uid,
        appName: String(a?.appName ?? a?.packageName).trim().slice(0, 100),
        packageName: cleanPackage(a?.packageName),
        category: String(a?.category ?? "General").trim().slice(0, 50),
        approvalStatus: "APPROVED",
        source: "existing",
        installed: true,
        dailyLimitMinutes: 0,
        requestedAt: FieldValue.serverTimestamp(),
        reviewedAt: null,
        reviewedBy: null,
      });
      added++;
    });
    await batch.commit();
  }
  return { added };
});

/** The child's phone reports an uninstall; the parent is told and the app is marked as removed. */
export const reportAppRemoved = onCall(async (req) => {
  const { familyId, childId, child } = await requireChildDevice(req);
  const pkg = cleanPackage(req.data?.packageName);
  if (!pkg) throw new HttpsError("invalid-argument", "Invalid package name.");
  const ref = db.doc(`families/${familyId}/apps/${appDocId(childId, pkg)}`);
  const existing = (await ref.get()).data() as any;
  const appName = String(existing?.appName ?? req.data?.appName ?? pkg).slice(0, 100);
  if (existing?.installed === false) return { ok: true }; // already reported
  if (existing) await ref.update({ installed: false, uninstalledAt: FieldValue.serverTimestamp() });
  await logAppActivity(familyId, childId, child.name, "uninstalled", pkg, appName, existing?.approvalStatus ?? null);

  const fam = (await db.doc(`families/${familyId}`).get()).data() as any;
  if (fam) {
    await pushToUsers((fam.parentUids as string[]) ?? [], {
      notification: { title: `${appName} was uninstalled`, body: `${child.name} removed ${appName} from their phone.` },
      data: { type: "app_activity", familyId, childId, packageName: pkg },
      android: { priority: "high", notification: { channelId: "safety_alerts" } },
    }).catch((e) => console.error("reportAppRemoved push failed", e));
  }
  return { ok: true };
});

// ---------------------------------------------------------------- screen time: protection status
const PROTECTION_LABEL: Record<string, string> = {
  usageAccess: "usage access",
  overlay: "the lock screen permission (display over other apps)",
  deviceAdmin: "device admin",
  notifications: "notifications",
  batteryUnrestricted: "unrestricted battery",
};

/** Tells the parent when a protection is switched off on the child's phone. */
export const onDeviceProtectionChanged = onDocumentWritten("families/{familyId}/devices/{childId}", async (event) => {
  const before = (event.data?.before?.data()?.permissions ?? {}) as Record<string, boolean>;
  const after = (event.data?.after?.data()?.permissions ?? {}) as Record<string, boolean>;
  const lost = Object.keys(PROTECTION_LABEL).filter((k) => before[k] === true && after[k] === false);
  if (lost.length === 0) return;
  const { familyId, childId } = event.params;
  const [fam, child] = await Promise.all([
    db.doc(`families/${familyId}`).get().then((x) => x.data() as any),
    db.doc(`families/${familyId}/children/${childId}`).get().then((x) => x.data() as any),
  ]);
  if (!fam) return;
  const name = child?.name || "Your child";
  await pushToUsers((fam.parentUids as string[]) ?? [], {
    notification: {
      title: "Screen time protection weakened",
      body: (() => {
        const what = lost.map((k) => PROTECTION_LABEL[k]).join(", ");
        const verb = lost.length > 1 ? "were" : "was";
        return `${what.charAt(0).toUpperCase()}${what.slice(1)} ${verb} switched off on ${name}'s phone. Open K-Guard there to turn it back on.`;
      })(),
    },
    data: { type: "device_protection", familyId, childId },
    android: { priority: "high", notification: { channelId: "safety_alerts" } },
  });
});

/**
 * Every 30 minutes: a paired phone that has not checked in for 6 hours may be switched off, offline for a
 * long time, or have had K-Guard removed. Tell the parent once per silence.
 */
export const checkSilentDevices = onSchedule("every 30 minutes", async () => {
  const cutoff = Timestamp.fromMillis(Date.now() - 6 * 3_600_000);
  const stale = await db.collectionGroup("devices").where("lastSyncedAt", "<", cutoff).limit(500).get();
  for (const doc of stale.docs) {
    const d = doc.data() as any;
    const last: Timestamp | undefined = d.lastSyncedAt;
    if (!last || (d.silentNotifiedFor && d.silentNotifiedFor.isEqual?.(last))) continue;
    const familyId = doc.ref.parent.parent?.id;
    if (!familyId) continue;
    const fam = (await db.doc(`families/${familyId}`).get()).data() as any;
    if (!fam) continue;
    const name = d.childName || "Your child";
    await pushToUsers((fam.parentUids as string[]) ?? [], {
      notification: {
        title: `${name}'s phone hasn't checked in`,
        body: `K-Guard hasn't heard from ${name}'s phone for over 6 hours. It may be switched off or offline, or K-Guard may have been removed.`,
      },
      data: { type: "device_silent", familyId, childId: doc.id },
      android: { priority: "high", notification: { channelId: "safety_alerts" } },
    }).catch((e) => console.error("checkSilentDevices push failed", e));
    await doc.ref.update({ silentNotifiedFor: last });
  }
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
  const dailyLimitMinutes =
    req.data?.dailyLimitMinutes === undefined || req.data?.dailyLimitMinutes === null
      ? undefined
      : clampInt(req.data.dailyLimitMinutes, 0, 1440, 30);

  const appRef = db.doc(`families/${familyId}/apps/${appId}`);
  const snap = await appRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "App request not found.");
  const appData = snap.data() as any;

  const updateData: Record<string, any> = {
    approvalStatus: decision,
    reviewedAt: FieldValue.serverTimestamp(),
    reviewedBy: uid,
  };
  if (dailyLimitMinutes !== undefined) updateData.dailyLimitMinutes = dailyLimitMinutes;

  await appRef.update(updateData);

  // Tell the child's device. Data-only and high priority so the app handles it even in the background:
  // it updates its local allow-list first, then shows the notification itself.
  if (appData.childLinkedUid) {
    const isApproved = decision === "APPROVED";
    await pushToUsers([appData.childLinkedUid], {
      data: {
        type: "app_approval_response",
        familyId,
        appId,
        appName: String(appData.appName ?? ""),
        packageName: String(appData.packageName ?? ""),
        decision,
        title: isApproved ? `${appData.appName} approved` : `${appData.appName} not approved`,
        body: isApproved
          ? `Your parent approved ${appData.appName}. You can open it now.`
          : `Your parent did not approve ${appData.appName}. It stays blocked on this device.`,
      },
      android: { priority: "high" },
    }).catch((e) => console.error("respondToAppApproval push failed", e));
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

  const childName = after.childName || "Your child";
  const appName = after.appName || "an application";
  const category = after.category || "App";
  const minAge = after.minAgeRating || 0;

  const sent = await pushToUsers((fam.parentUids as string[]) ?? [], {
    notification: {
      title: `App install request: ${appName}`,
      body: `${childName} installed ${appName} (${category}, ages ${minAge}+). It stays blocked until you review it.`,
    },
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
    android: { priority: "high", notification: { channelId: "safety_alerts" } },
  });

  await event.data?.after?.ref.update({ notified: sent > 0 });
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
