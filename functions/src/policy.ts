// Pure policy engine: no Firebase imports so it can be unit tested.

export type Decision = "ALLOW" | "WARN" | "BLUR" | "BLOCK" | "REVIEW";
export type AgeProfile = "KIDS_3_7" | "PRETEENS_8_12" | "TEENS_13_17" | "ADULTS_18_PLUS";
export type Context = "harmful" | "educational" | "news" | "fiction" | "unclear";
export type Rule = "block" | "blur" | "warn" | "allow";

export const CATEGORIES = [
  "cyberbullying", "threat", "violence", "sexual", "self_harm", "hate",
  "drugs", "profanity", "scam", "personal_info",
] as const;
export type Category = (typeof CATEGORIES)[number];
export type Scores = Partial<Record<Category, number>>;

export const AGE_PROFILES: AgeProfile[] = ["KIDS_3_7", "PRETEENS_8_12", "TEENS_13_17", "ADULTS_18_PLUS"];

// Score at which each action starts to apply. Lower = stricter.
interface Thresholds { warn: number; blur: number; block: number }
const DEFAULTS: Record<AgeProfile, Thresholds> = {
  KIDS_3_7: { warn: 0.15, blur: 0.30, block: 0.50 },
  PRETEENS_8_12: { warn: 0.30, blur: 0.50, block: 0.70 },
  TEENS_13_17: { warn: 0.45, blur: 0.65, block: 0.85 },
  ADULTS_18_PLUS: { warn: 0.60, blur: 0.80, block: 0.92 },
};

// Categories that are never softened by educational/news/fiction context or by a parent "allow" rule.
const NEVER_SOFTEN: Category[] = ["sexual", "threat", "self_harm"];

const ORDER: Decision[] = ["ALLOW", "WARN", "BLUR", "BLOCK"];
const rank = (d: Decision) => ORDER.indexOf(d);
const maxD = (a: Decision, b: Decision) => (rank(a) >= rank(b) ? a : b);

export const REVEAL_ALLOWED: Record<AgeProfile, boolean> = {
  KIDS_3_7: false,
  PRETEENS_8_12: false,
  TEENS_13_17: true,
  ADULTS_18_PLUS: true,
};

export interface PolicyInput {
  scores: Scores;
  context: Context;
  ageProfile: AgeProfile;
  rules?: Partial<Record<Category, Rule>>;
}

export interface PolicyResult {
  decision: Decision;
  category: Category | "none";
  riskScore: number;
  ageAppropriate: boolean;
  explanation: string;
  softened: boolean;
}

export const LABEL: Record<Category, string> = {
  cyberbullying: "bullying or harassment",
  threat: "threatening language",
  violence: "violent content",
  sexual: "sexual content",
  self_harm: "self-harm content",
  hate: "hateful content",
  drugs: "drug-related content",
  profanity: "strong language",
  scam: "a possible scam",
  personal_info: "personal information",
};
const AGE_LABEL: Record<AgeProfile, string> = {
  KIDS_3_7: "ages 3–7",
  PRETEENS_8_12: "ages 8–12",
  TEENS_13_17: "ages 13–17",
  ADULTS_18_PLUS: "adults",
};

const RULE_TO_DECISION: Record<Rule, Decision> = { allow: "ALLOW", warn: "WARN", blur: "BLUR", block: "BLOCK" };

export function resolvePolicy(input: PolicyInput): PolicyResult {
  const t = DEFAULTS[input.ageProfile];
  let worst: { category: Category; score: number; decision: Decision } | null = null;
  let softened = false;

  for (const cat of CATEGORIES) {
    const raw = input.scores[cat];
    if (raw === undefined || Number.isNaN(raw)) continue;
    const score = Math.min(1, Math.max(0, raw));
    let d: Decision = score >= t.block ? "BLOCK" : score >= t.blur ? "BLUR" : score >= t.warn ? "WARN" : "ALLOW";

    // Parent per-category rule. "block" always tightens. Other rules may relax a detected category,
    // but never for the most serious categories, and never when the signal is very strong (>= 0.9).
    const rule = input.rules?.[cat];
    if (rule) {
      const ruleDecision = RULE_TO_DECISION[rule];
      if (rule === "block") {
        if (score >= t.warn) d = maxD(d, "BLOCK");
      } else if (d !== "ALLOW" && score < 0.9 && !NEVER_SOFTEN.includes(cat)) {
        d = rank(ruleDecision) < rank(d) ? ruleDecision : d;
      }
    }

    // Educational / news / fiction context lowers severity one step for suitable categories.
    if (
      d !== "ALLOW" &&
      ["educational", "news", "fiction"].includes(input.context) &&
      !NEVER_SOFTEN.includes(cat) &&
      input.ageProfile !== "KIDS_3_7"
    ) {
      d = ORDER[Math.max(0, rank(d) - 1)];
      softened = true;
    }

    if (!worst || rank(d) > rank(worst.decision) || (rank(d) === rank(worst.decision) && score > worst.score)) {
      worst = { category: cat, score, decision: d };
    }
  }

  if (!worst) {
    return {
      decision: "REVIEW", category: "none", riskScore: 0, ageAppropriate: false, softened: false,
      explanation: "K-Guard could not assess this content, so it is held for a parent to review.",
    };
  }
  if (worst.decision === "ALLOW") {
    return {
      decision: "ALLOW", category: "none", riskScore: worst.score, ageAppropriate: true, softened,
      explanation: softened
        ? "This looks educational or informational, so it is allowed for this age group."
        : "No concerns were found for this age group.",
    };
  }
  const what = LABEL[worst.category];
  const why = softened
    ? `Contains ${what} in a ${input.context} setting, so the response was reduced for ${AGE_LABEL[input.ageProfile]}.`
    : `Likely contains ${what}, which is not suitable for ${AGE_LABEL[input.ageProfile]}.`;
  return {
    decision: worst.decision, category: worst.category, riskScore: worst.score,
    ageAppropriate: false, softened, explanation: why,
  };
}
