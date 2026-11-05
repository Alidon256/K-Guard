import { CATEGORIES, Category, Context, Scores } from "./policy";

export interface Analysis {
  scores: Scores;
  context: Context;
  source: "ai" | "rules";
}

export interface ApiConfig {
  url?: string;
  key?: string;
}

function normaliseCategory(raw: string): Category | null {
  const s = raw.toLowerCase();
  if (s.includes("bully") || s.includes("harass")) return "cyberbullying";
  if (s.includes("threat")) return "threat";
  if (s.includes("violen") || s.includes("graphic")) return "violence";
  if (s.includes("sex") || s.includes("explicit") || s.includes("nsfw")) return "sexual";
  if (s.includes("self")) return "self_harm";
  if (s.includes("hate")) return "hate";
  if (s.includes("drug")) return "drugs";
  if (s.includes("profan")) return "profanity";
  if (s.includes("scam") || s.includes("fraud")) return "scam";
  if (s.includes("personal") || s.includes("pii")) return "personal_info";
  return null;
}

/** Calls the external moderation API. Returns null on any failure so callers can fail closed. */
async function callApi(cfg: ApiConfig, path: string, init: RequestInit): Promise<Analysis | null> {
  if (!cfg.url) return null;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 12000);
  try {
    const headers: Record<string, string> = { ...((init.headers as Record<string, string>) ?? {}) };
    if (cfg.key) headers["Authorization"] = `Bearer ${cfg.key}`;
    const res = await fetch(cfg.url.replace(/\/$/, "") + path, { ...init, headers, signal: ctrl.signal });
    if (!res.ok) return null;
    const body: any = await res.json();
    const scores: Scores = {};
    if (body.categories && typeof body.categories === "object") {
      for (const c of CATEGORIES) if (typeof body.categories[c] === "number") scores[c] = body.categories[c];
    } else if (typeof body.risk_score === "number" && typeof body.category === "string") {
      const c = normaliseCategory(body.category);
      if (c) scores[c] = body.risk_score;
    }
    // A clean ALLOW with no category: treat every category as zero risk.
    if (Object.keys(scores).length === 0 && body.decision === "ALLOW" && typeof body.risk_score === "number") {
      for (const c of CATEGORIES) scores[c] = Math.min(body.risk_score, 0.1);
    }
    if (Object.keys(scores).length === 0) return null;
    const ctx: Context = ["harmful", "educational", "news", "fiction", "unclear"].includes(body.context)
      ? body.context
      : "unclear";
    return { scores, context: ctx, source: "ai" };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// ---- Built-in rule engine: coarse, transparent fallback for TEXT only. Never used for images. ----
const PATTERNS: { cat: Category; re: RegExp; w: number }[] = [
  { cat: "threat", re: /\b(i('| a)?m going to|i will|gonna)\s+(kill|hurt|stab|shoot|beat)\b/i, w: 0.92 },
  { cat: "threat", re: /\b(kill|hurt|stab|shoot)\s+(you|him|her|them|yourself)\b/i, w: 0.88 },
  { cat: "self_harm", re: /\b(kill myself|end my life|want to die|hurt myself|self[- ]harm)\b/i, w: 0.9 },
  { cat: "cyberbullying", re: /\b(you('| a)?re|u r|ur)\s+(so\s+)?(ugly|stupid|worthless|a loser|fat|dumb)\b/i, w: 0.72 },
  { cat: "cyberbullying", re: /\b(nobody likes you|shut up|go away loser|kill yourself)\b/i, w: 0.75 },
  { cat: "hate", re: /\bi hate (all )?(blacks|whites|jews|muslims|christians|gays|immigrants)\b/i, w: 0.9 },
  { cat: "sexual", re: /\b(nude|nudes|porn|sexting|send pics)\b/i, w: 0.8 },
  { cat: "violence", re: /\b(blood|gore|murder|massacre|shooting|weapon|knife attack)\b/i, w: 0.6 },
  { cat: "drugs", re: /\b(cocaine|heroin|meth|weed|get high|molly)\b/i, w: 0.6 },
  { cat: "profanity", re: /\b(fuck|shit|bitch|asshole)\b/i, w: 0.55 },
  { cat: "scam", re: /\b(you('| ha)ve won|claim your prize|send (me )?your (password|pin|otp)|gift card code)\b/i, w: 0.7 },
  { cat: "personal_info", re: /\b(my (home )?address is|my phone (number )?is)\b/i, w: 0.55 },
];
const EDU = /\b(history|historical|lesson|class|school|documentary|study|research|textbook|awareness|prevent|how to stay safe|get help|news)\b/i;
const FICTION = /\b(movie|film|novel|story|game|fictional|character|chapter)\b/i;

export function analyzeTextWithRules(text: string): Analysis {
  const scores: Scores = {};
  for (const c of CATEGORIES) scores[c] = 0;
  for (const p of PATTERNS) if (p.re.test(text)) scores[p.cat] = Math.max(scores[p.cat] ?? 0, p.w);
  const flagged = Object.values(scores).some((v) => (v ?? 0) > 0);
  const context: Context = !flagged ? "unclear" : EDU.test(text) ? "educational" : FICTION.test(text) ? "fiction" : "harmful";
  return { scores, context, source: "rules" };
}

export async function analyzeText(text: string, ageProfile: string, cfg: ApiConfig): Promise<Analysis> {
  const ai = await callApi(cfg, "/moderate/text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, age_group: ageProfile }),
  });
  return ai ?? analyzeTextWithRules(text);
}

/** Images need the external AI. There is no rule fallback: null means "could not assess". */
export async function analyzeImage(
  bytes: Buffer,
  contentType: string,
  ageProfile: string,
  cfg: ApiConfig,
): Promise<Analysis | null> {
  const form = new FormData();
  form.append("image", new Blob([new Uint8Array(bytes)], { type: contentType }), "upload");
  form.append("age_group", ageProfile);
  return callApi(cfg, "/moderate/image", { method: "POST", body: form });
}
