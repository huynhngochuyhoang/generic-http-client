# AGENTAGENT.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Do not assume. Do not hide uncertainty. Surface tradeoffs.**

Before implementing:

* State assumptions explicitly. If uncertain, ask.
* If multiple interpretations exist, present them instead of choosing silently.
* If a simpler approach exists, mention it. Push back when appropriate.
* If something is unclear, stop and ask for clarification.

## 2. Simplicity First

**Write the minimum code required to solve the problem. Avoid speculative design.**

* Do not add features beyond the request.
* Do not introduce abstractions for single-use code.
* Do not add configurability or flexibility unless requested.
* Avoid handling impossible scenarios.
* If the solution feels overengineered, simplify it.

Ask yourself:

> "Would a senior engineer consider this unnecessarily complex?"

If yes, rewrite it more simply.

## 3. Surgical Changes

**Modify only what is necessary.**

When editing existing code:

* Do not "improve" unrelated code, comments, or formatting.
* Do not refactor unrelated areas.
* Match the existing project style, even if you would design it differently.
* If unrelated dead code is noticed, mention it instead of removing it.

When your changes create unused code:

* Remove imports, variables, or functions made unused by your change.
* Do not remove pre-existing dead code unless explicitly asked.

Rule:

> Every changed line should directly support the requested task.

## 4. Goal-Driven Execution

**Define success criteria and verify them.**

Convert requests into measurable goals:

* "Add validation" → Write failing tests for invalid input, then make them pass.
* "Fix the bug" → Reproduce the bug with a test, then fix it.
* "Refactor X" → Ensure behavior is unchanged before and after refactoring.

For multi-step tasks, define a lightweight execution plan:

```text
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria reduce ambiguity and avoid unnecessary rework.

---

These guidelines are effective when:

* Diffs contain fewer unnecessary changes
* Overengineering decreases
* Clarifying questions happen before implementation instead of after mistakes