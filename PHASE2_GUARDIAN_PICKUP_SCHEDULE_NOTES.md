# Phase 2 Update 14 — Optional Guardian Pickup Schedules

This update adds optional weekly pickup schedules for permanent backup guardians.

## Default behavior
- Existing guardians remain unrestricted.
- No schedule is required.
- Primary QR pickup behavior remains unchanged.
- School pickup policy/time windows still apply independently.

## Scheduled guardian behavior
A parent can configure a non-primary permanent guardian for selected days of the week, with optional start/end dates.
The backend checks the schedule both when issuing a QR and again during QR verification/approval.
Changing a schedule invalidates unused QR passes held by that guardian.

## Safety rules
- Temporary one-day guardians use their existing one-day authorization and cannot receive a weekly schedule.
- A non-staff guardian cannot restrict their own schedule, avoiding accidental self-lockout.
- School staff can manage schedules administratively.
- Schedule changes are audit logged.

## Firestore
No new collection or composite index is required. Schedule fields live under each student's guardian entry:
- pickupScheduleEnabled
- pickupDays
- scheduleStartDate
- scheduleEndDate
- scheduleUpdatedBy
- scheduleUpdatedAt
