import test from "node:test";
import assert from "node:assert/strict";
import { resolvePolicy } from "../src/policy";
import { analyzeTextWithRules } from "../src/engine";

test("no scores -> REVIEW, never ALLOW (fail closed)", () => {
  const r = resolvePolicy({ scores: {}, context: "unclear", ageProfile: "KIDS_3_7" });
  assert.equal(r.decision, "REVIEW");
});

test("same score is stricter for younger ages", () => {
  const kid = resolvePolicy({ scores: { violence: 0.55 }, context: "harmful", ageProfile: "KIDS_3_7" });
  const teen = resolvePolicy({ scores: { violence: 0.55 }, context: "harmful", ageProfile: "TEENS_13_17" });
  assert.equal(kid.decision, "BLOCK");
  assert.equal(teen.decision, "WARN");
});

test("educational context softens violence one step for pre-teens", () => {
  const harmful = resolvePolicy({ scores: { violence: 0.75 }, context: "harmful", ageProfile: "PRETEENS_8_12" });
  const edu = resolvePolicy({ scores: { violence: 0.75 }, context: "educational", ageProfile: "PRETEENS_8_12" });
  assert.equal(harmful.decision, "BLOCK");
  assert.equal(edu.decision, "BLUR");
  assert.equal(edu.softened, true);
});

test("educational context does not soften anything for the youngest profile", () => {
  const r = resolvePolicy({ scores: { violence: 0.75 }, context: "educational", ageProfile: "KIDS_3_7" });
  assert.equal(r.decision, "BLOCK");
});

test("threats are never softened by context", () => {
  const r = resolvePolicy({ scores: { threat: 0.9 }, context: "fiction", ageProfile: "TEENS_13_17" });
  assert.equal(r.decision, "BLOCK");
});

test("parent can relax moderate profanity to warn for a teen, but not a very strong signal", () => {
  const mild = resolvePolicy({ scores: { profanity: 0.7 }, context: "harmful", ageProfile: "TEENS_13_17", rules: { profanity: "warn" } });
  assert.equal(mild.decision, "WARN");
  const strong = resolvePolicy({ scores: { profanity: 0.95 }, context: "harmful", ageProfile: "TEENS_13_17", rules: { profanity: "warn" } });
  assert.equal(strong.decision, "BLOCK");
});

test("a parent allow rule cannot relax sexual content", () => {
  const r = resolvePolicy({ scores: { sexual: 0.7 }, context: "harmful", ageProfile: "TEENS_13_17", rules: { sexual: "allow" } });
  assert.equal(r.decision, "BLUR");
});

test("parent block rule tightens", () => {
  const r = resolvePolicy({ scores: { drugs: 0.5 }, context: "harmful", ageProfile: "TEENS_13_17", rules: { drugs: "block" } });
  assert.equal(r.decision, "BLOCK");
});

test("fallback rules catch bullying and threats, and pass benign text", () => {
  assert.ok((analyzeTextWithRules("you are so ugly").scores.cyberbullying ?? 0) > 0.5);
  assert.ok((analyzeTextWithRules("I'm going to kill you").scores.threat ?? 0) > 0.8);
  const ok = resolvePolicy({ ...analyzeTextWithRules("let's do homework together"), ageProfile: "KIDS_3_7" });
  assert.equal(ok.decision, "ALLOW");
});
