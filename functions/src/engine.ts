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

const DEFAULT_KEY = process.env.MODERATION_API_KEY || "AIzaSyCnwCZJc5EVdRBEpCwZE_sZq4Ni-1WJP10";

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

/** Calls an external custom moderation API if configured. Returns null on any failure. */
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

/** Google Cloud Natural Language API moderateText for production text moderation. */
async function callGoogleNaturalLanguage(text: string, key: string): Promise<Scores | null> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 10000);
  try {
    const res = await fetch(`https://language.googleapis.com/v1/documents:moderateText?key=${key}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ document: { type: "PLAIN_TEXT", content: text } }),
      signal: ctrl.signal,
    });
    if (!res.ok) return null;
    const body: any = await res.json();
    const categories: any[] = body.moderationCategories ?? [];
    if (!Array.isArray(categories) || categories.length === 0) return null;
    const scores: Scores = {};
    for (const cat of categories) {
      const name = String(cat.name ?? "").toLowerCase();
      const conf = Number(cat.confidence ?? 0);
      if (name.includes("toxic") || name.includes("insult")) {
        scores["cyberbullying"] = Math.max(scores["cyberbullying"] ?? 0, conf);
      }
      if (name.includes("violent") || name.includes("death") || name.includes("weapon") || name.includes("firearm")) {
        scores["violence"] = Math.max(scores["violence"] ?? 0, conf);
      }
      if (name.includes("sexual")) {
        scores["sexual"] = Math.max(scores["sexual"] ?? 0, conf);
      }
      if (name.includes("illicit drug") || name.includes("drug")) {
        scores["drugs"] = Math.max(scores["drugs"] ?? 0, conf);
      }
      if (name.includes("profanity")) {
        scores["profanity"] = Math.max(scores["profanity"] ?? 0, conf);
      }
      if (name.includes("derogatory")) {
        scores["hate"] = Math.max(scores["hate"] ?? 0, conf);
      }
    }
    return scores;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/** Google Cloud Vision API SafeSearchDetection for production image moderation. */
async function callGoogleVisionSafeSearch(bytes: Buffer, key: string): Promise<Scores | null> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 12000);
  try {
    const base64Img = bytes.toString("base64");
    const res = await fetch(`https://vision.googleapis.com/v1/images:annotate?key=${key}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        requests: [
          {
            image: { content: base64Img },
            features: [{ type: "SAFE_SEARCH_DETECTION" }],
          },
        ],
      }),
      signal: ctrl.signal,
    });
    if (!res.ok) return null;
    const body: any = await res.json();
    const annot = body.responses?.[0]?.safeSearchAnnotation;
    if (!annot) return null;

    const likelihoodMap: Record<string, number> = {
      VERY_UNLIKELY: 0.05,
      UNLIKELY: 0.2,
      POSSIBLE: 0.5,
      LIKELY: 0.75,
      VERY_LIKELY: 0.95,
    };
    const adultScore = likelihoodMap[annot.adult] ?? 0.05;
    const racyScore = likelihoodMap[annot.racy] ?? 0.05;
    const violenceScore = likelihoodMap[annot.violence] ?? 0.05;

    return {
      sexual: Math.max(adultScore, racyScore),
      violence: violenceScore,
    };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

// ---- Built-in rule engine: coarse, transparent fallback for TEXT only. ----
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
const EDU = /\b(history|historical|lesson|class|school|documentary|study|research|textbook|awareness|prevent|how to stay safe|get help|news|biology|science|anatomy)\b/i;
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
  // 1. Try external custom API if configured
  if (cfg.url) {
    const ai = await callApi(cfg, "/moderate/text", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text, age_group: ageProfile }),
    });
    if (ai) return ai;
  }

  // 2. Try Google Cloud Natural Language API (production-grade)
  const key = cfg.key || DEFAULT_KEY;
  if (key) {
    const cloudScores = await callGoogleNaturalLanguage(text, key);
    if (cloudScores) {
      // Merge with regex patterns for local coverage
      const ruleScores = analyzeTextWithRules(text).scores;
      for (const c of CATEGORIES) {
        if ((ruleScores[c] ?? 0) > (cloudScores[c] ?? 0)) {
          cloudScores[c] = ruleScores[c];
        }
      }
      const flagged = Object.values(cloudScores).some((v) => (v ?? 0) > 0.3);
      const context: Context = !flagged ? "unclear" : EDU.test(text) ? "educational" : FICTION.test(text) ? "fiction" : "harmful";
      return { scores: cloudScores, context, source: "ai" };
    }
  }

  // 3. Fallback to transparent built-in rules
  return analyzeTextWithRules(text);
}

/** Images use Google Cloud Vision SafeSearch detection or external API. Null means could not assess. */
export async function analyzeImage(
  bytes: Buffer,
  contentType: string,
  ageProfile: string,
  cfg: ApiConfig,
): Promise<Analysis | null> {
  // 1. Try external custom API if configured
  if (cfg.url) {
    const form = new FormData();
    form.append("image", new Blob([new Uint8Array(bytes)], { type: contentType }), "upload");
    form.append("age_group", ageProfile);
    const ai = await callApi(cfg, "/moderate/image", { method: "POST", body: form });
    if (ai) return ai;
  }

  // 2. Try Google Cloud Vision SafeSearch API (production-grade)
  const key = cfg.key || DEFAULT_KEY;
  if (key) {
    const visionScores = await callGoogleVisionSafeSearch(bytes, key);
    if (visionScores) {
      return { scores: visionScores, context: "harmful", source: "ai" };
    }
  }

  return null;
}
