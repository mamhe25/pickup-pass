# Phase 2 Update 13 — Gate Activity Dashboard

This update extends the existing Live Dismissal dashboard with school-wide pickup-method and gate activity counts.

## Behavior

- No pickup queue or parent arrival check-in is introduced.
- Schools with no configured pickup gate continue to use the existing scanner flow.
- Schools with one active gate can continue using automatic gate selection; staff gate assignment remains optional.
- Schools with multiple active gates can see release activity for each configured gate.
- Active configured gates appear even when their current release count is zero.
- Archived gates are not seeded as active operational gates, but historical releases still retain their saved gate/campus snapshots.

## Dashboard additions

- QR release count for the selected business day.
- Manual override count for the selected business day.
- Per-gate total release count.
- Per-gate QR vs manual-override breakdown.
- Campus activity data is returned by the backend for later multi-campus dashboard expansion.

## Data/index impact

No new Firestore composite index is required by Update 13. The dashboard reads active gate configuration by school and aggregates today's existing exit logs in memory.
