---
name: decision-structure
description: Gives a person structure for choosing between two options by scoring each against the ten most relevant of their own personal values and adding up the totals. Use it when the user is torn between option A and option B, asks "which should I pick", feels pulled toward too many opportunities at once, wants to decide in line with their values, wants to build or edit their list of values, or says "run my values on this decision", even if they never say "values".
---

# Decision structure

## Why this skill exists

Some people see opportunity everywhere. The author's view is that this is not a skill. It is closer to an affliction, and it comes from a lack of structure: with nothing to measure a new idea against, every idea looks equally promising, so attention scatters and little gets finished. Structure is how a person navigates life, and it is how they apply their values to make choices. This skill provides that structure. The saved list of values is the structure, and the scoring below is how it gets applied to a real choice, so the decision follows from what the person believes and not from whichever option is most exciting right now.

What this means for how you work:

- The values are the fixed point and the options are what varies. That is why values always come first.
- Excitement is not a value unless the person has put it on their list. Do not let a thrilling option win by being thrilling.
- Never diagnose or label the person. The framing above is the author's lens, not a clinical statement, and the user is never told they have a condition. If someone says they are chasing too many opportunities, say that a list of values gives them something to measure each one against, and offer to run it.

## How it works

The user keeps a personal list of values that grows over time. For each decision you score two options against the ten values that matter most, then add up the totals. The AI scores and explains. The user decides. The totals are a lens, not a verdict.

Always three steps, in this order: **1. values, 2. options, 3. scores.**

## 1. Values first, always

Ask for the current list of values before asking anything about A or B, even if the user opens with both options. Values shift, and hearing the options first bends the scores toward the option described. If the options came first, acknowledge them in one line, ask for the values, and say you'll come back to the options.

- **Values pasted in this same message:** they are current. Use them and go straight on.
- **Values from earlier in the chat or a saved `values-profile.md`:** show them and ask one thing: "Still current?"
- **No values yet:** build them together. A value is a preference with a reason, specific enough that two options could differ on it ("I would rather ship less, finished, than more, rough" is scoreable; "I value quality" is not). Prompt from experience: a decision they regretted, advice they give again and again, a shortcut they refuse to take. Offer a tightened rewrite of each and give each a short name of 2 to 4 words. Start with about ten; there is no upper limit.

Once confirmed, save `values-profile.md` (one line per value, `1. **Short name**: the value.`) to the folder the user names, or their last-used folder, or their workspace. Tell them the exact path. If you can't write files, print it in a code block. Keep short names stable between sessions. Every value counts equally; do not offer weights.

## 2. Then the two options

Ask for A and B in the user's words, plus what is at stake. If the facts are thin, do not interrogate: state your assumptions in one line at the top of the scores and let the user correct them. Ask a question only when an option is too vague to score at all. Use neutral labels (A and B plus a short name).

**Pick the ten values.** If the list has ten or fewer, use all of them. If more, pick the ten the decision touches most, by relevance and never by which option a value would favor. Prefer values where the options differ. Open the scoring reply with the ten chosen and the ones left out, a few words each, and say they can swap any. Values the decision does not touch go on one line ("Not touched here: ...") and are not scored. Never pad the table.

## 3. Score and report

Score each option 0 to 10 per value: 0 works against the value, 5 is neutral, 10 is a textbook fit. Score A and B independently against the value, not against each other. Give each its own one-line reason grounded in a concrete fact. Be willing to give low scores.

Add each column. Use code if you can; otherwise add it twice in different orders and check both agree. Re-check any number you quote in the read-out against the table.

```markdown
| Value | Option A: <label> | Option B: <label> | Score A | Score B |
|-------|-------------------|-------------------|---------|---------|
| 1. In person first | A is a site visit. | B is a video call. | 9/10 | 3/10 |
| **Overall** (10 of your 20 values) | **Cumulative alignment: 72 / 100** | **Cumulative alignment: 61 / 100** | **7.2 / 10** | **6.1 / 10** |
```

Under the table, give the simple answer on its own line, as a relative percentage: the winner's total minus the loser's, divided by the loser's total, rounded to a whole percent. Name the option, not just the letter.

**Answer: Cook at home fits your values 18% better than Fancy burger (72 vs 61).**

Say "fits your values better" and not "more likely to choose": the percentage compares alignment, not a probability. Then a short read-out of three lines: the two or three values that drove it, a one-line assumption note if you made any, and one gut-check question ("does this match how you feel?"). If the relative gap is under about 10 percent, say the values do not clearly separate the options and name the rows that would flip it. If the user feels strongly against the winner, a value is probably missing or a score rested on a bad assumption: offer to add it and re-run.

Offer to change any score, swap values in or out, or add a new value to the saved profile. When the user overrules a score, update it and call it their judgement. Never drift scores toward the option they seem to want.
