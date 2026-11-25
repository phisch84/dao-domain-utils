# Instructions for AI coding agents

The instructions for this repository are in [CLAUDE.md](CLAUDE.md). They apply to every AI agent, not only to
Claude: read that file before changing anything. It has the commands, the code map, the architecture pattern, the
design decisions that must not change without asking, and the code, Javadoc and test rules.

This file only repeats the essentials, so they are visible even before CLAUDE.md is read:

- Definition of done: `mvn -B clean verify` passes (tests and the strict Javadoc check, which fails on any finding).
- The code must compile against Java 8 (no `var`, records, `List.of`, text blocks, ...).
- Work test-first, in small steps the maintainer can review and commit one at a time.
- Ask before changing a design decision listed in CLAUDE.md; offer options with a recommendation.

If CLAUDE.md and this file disagree, CLAUDE.md is right; fix this file.
