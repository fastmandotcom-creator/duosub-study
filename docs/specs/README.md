# Feature specs

A spec records the problem, intended behavior, constraints, acceptance criteria,
release intent, and validation evidence for one coherent change. Keep ordinary specs
short; put the implementation plan in the same file and link existing explanations.

## Document map

| Location | Purpose |
| --- | --- |
| [AGENTS.md](../../AGENTS.md) | Operational instructions shared by coding agents and contributors |
| [docs/project/mission.md](../project/mission.md) | Stable product intent |
| [docs/project/tech-stack.md](../project/tech-stack.md) | Technical context and architecture boundaries |
| [ROADMAP.md](../../ROADMAP.md) | Single directional roadmap |
| `docs/specs/YYYY-MM-DD-short-change-name.md` | One change's behavior, plan, release intent, criteria and results |

## When to write one

Create or update a spec before implementation for new user-facing features,
significant bug fixes, architecture or multi-component behavior changes, risky
WebView/caption/playback work, persistence/migrations, CI/CD or release changes,
and performance work with measurable targets.

Typos, tiny documentation edits, and obvious small fixes without architectural impact
do not need a full spec. State `Not required — <reason>` in the commit message and give brief,
checkable acceptance criteria. Risk and behavior matter more than line count.

## Start and lifecycle

1. Read [mission](../project/mission.md), [technical context](../project/tech-stack.md),
   [AGENTS.md](../../AGENTS.md), [ROADMAP.md](../../ROADMAP.md), related specs and source/tests.
   Repository documentation carries stable intent; chat history alone is insufficient.
2. Copy [TEMPLATE.md](TEMPLATE.md) to `YYYY-MM-DD-short-change-name.md` using the creation
   date and a lowercase kebab-case name. Add a specific suffix if that name already
   exists. Update a relevant active spec rather than duplicating it.
3. Define behavior, acceptance criteria, validation, release intent, and the small
   implementation plan before coding. Revise these when investigation changes scope.
4. Implement the smallest coherent solution, validate, and record actual results and
   deviations. Update lasting product/architecture/convention decisions in the relevant
   permanent docs in the same commit.
5. Commit on `main`, linking the spec in the commit body. Push, then publish the
   Study release with `python3 tools/publish_study_apk.py` when the owner asked for
   a new APK. The manual `Android build` workflow is available for extra evidence
   but does not gate publication.

Lifecycle: idea → feature spec (including plan and release intent) → implementation
on `main` → tests/validation → commit on `main` → push → owner runs
`python3 tools/publish_study_apk.py` for the Study APK. Put spec changes in the same
commit as the code change. Feedback can return work to earlier stages.

| Status | Meaning |
| --- | --- |
| Draft | Scope or material decisions are still unresolved. |
| Approved for implementation | Existing owner authorization covers this scope; note that authorization. This does not grant merge/publication permission. |
| Implemented | Code/docs are written; list incomplete validation explicitly. |
| Validated | Required acceptance checks have evidence; optional/unavailable checks remain clearly identified. |
| Superseded | Retain the record and link its replacement and reason. |

A request to implement an agreed change already authorizes that implementation. Do not
add an approval round just to move a status, or invent owner approval. Ask only when a
material scope/product decision is unresolved. CI success does not approve a spec or PR.
Keep implemented specs as historical records; a new change should link the earlier
record rather than erase its evidence. There is no global implementation-plan file;
each significant change owns its plan and results in its feature spec. Existing
[QA records](../qa/issues-56-59/README.md) retain historical evidence and unresolved checks.

## Release intent is a decision, not automation

Record one intent and its reason before implementation where possible. Follow explicit
owner instructions; the Study publish flow (`python3 tools/publish_study_apk.py`)
ships the Study channel without a label, while the legacy `release-on-main` pipeline
(owner merge) still honors GitHub labels and `Release-Version:` directives.

| Spec intent | How it applies |
| --- | --- |
| Study channel (default) | Push to `main`, run `python3 tools/publish_study_apk.py`. The command numbers `study-X.Y.Z.N` from the current `appVersionName` and increments `N`. |
| Legacy official app (`duosub.study`) | Owner merges a PR and applies one GitHub label (`release:patch`/`release:minor`/`release:major`/`release:skip`) OR a standalone `Release-Version: X.Y.Z` in the PR body. |
| Documentation / tooling / tests only | No release. Push to `main`; the next Study publish picks up the change as part of the next `study-X.Y.Z.N` release. |

Never commit signing material (`*.jks`/`*.keystore` are gitignored). The Study publish
flow uses the local Android debug signer. For the legacy pipeline, release labels and
`Release-Version:` directives are applied on the PR, not in code; the parser
([tools/automatic_release.py](../../tools/automatic_release.py)) reads them from the PR
body and labels. Do not manually edit `appVersionName` or `appVersionCode` in normal
commits; both channels compute their next version from existing releases.

Security fixes (owner decision 2026-10-30, [spec](../releasing.md)): when a commit
fixes a security problem, say so in the commit message and in the Study release notes;
the release page's "What's new" carries the same wording the app's Security update
dialog would show.

## Evidence proportional to the change

Write exact commands/scenarios, expected results, and necessary environments before
implementation. Record pass/fail/not-run/not-applicable with reasons afterward. Unit,
lint/build, emulator/managed-device, physical-device, live YouTube, and performance
checks establish different things; offline fixtures cannot satisfy live validation.
For performance work include baseline, target, device/build, and repeatable measurement.
For docs-only work check links, consistency, and affected template behavior; do not
claim Android runtime validation. Run the manual `Android build` workflow for extra
evidence when the change reaches the APK.
