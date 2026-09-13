package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Outbound communication and lightweight CRM workflow for verified demo leads.
 * Public/unverified submissions never reach this service.
 */
@Service
public class DemoRequestCommunicationService {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestCommunicationService.class);
    private static final Set<String> MEETING_TYPES = Set.of("video", "phone", "onsite", "other");
    private static final DateTimeFormatter SCHEDULE_FORMAT =
            DateTimeFormatter.ofPattern("MMMM d, uuuu 'at' h:mm a z", Locale.ENGLISH);

    private final Firestore firestore;
    private final EmailService emailService;

    public DemoRequestCommunicationService(Firestore firestore, EmailService emailService) {
        this.firestore = firestore;
        this.emailService = emailService;
    }

    public Map<String, Object> list() throws Exception {
        List<QueryDocumentSnapshot> documents = firestore.collection("demoRequests")
                .orderBy("verifiedAt", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .get()
                .getDocuments();

        List<Map<String, Object>> requests = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        DemoRequestService.STATUSES.forEach(status -> counts.put(status, 0));

        for (QueryDocumentSnapshot document : documents) {
            if (!Boolean.TRUE.equals(document.getBoolean("emailVerified"))) continue;
            Map<String, Object> item = toLeadResponse(document);
            requests.add(item);
            String status = String.valueOf(item.get("status"));
            counts.compute(status, (key, value) -> value == null ? 1 : value + 1);
        }

        return Map.of(
                "requests", requests,
                "total", requests.size(),
                "counts", counts);
    }

    /**
     * Best-effort confirmation after successful ownership verification. The
     * verified lead remains valid even if SMTP is temporarily unavailable.
     */
    public Map<String, Object> sendVerifiedConfirmation(String requestId) {
        try {
            DocumentSnapshot lead = requireVerifiedLead(requestId);
            if (lead.getTimestamp("verifiedConfirmationSentAt") != null) {
                return Map.of("sent", true, "alreadySent", true);
            }

            String name = value(lead.getString("name"));
            String email = value(lead.getString("email"));
            String school = value(lead.getString("school"));
            String subject = "PickupPass demo request confirmed";
            String body = "Your verified demo request for " + school + " is now with the PickupPass team.";

            boolean sent = emailService.sendDemoVerifiedConfirmation(email, name, school);
            boolean historyRecorded = recordCommunication(
                    lead.getReference(),
                    "verification_confirmation",
                    subject,
                    body,
                    email,
                    "system",
                    sent);

            if (sent) {
                lead.getReference().update(
                        "verifiedConfirmationSentAt", FieldValue.serverTimestamp(),
                        "lastCommunicationAt", FieldValue.serverTimestamp()).get();
            }

            return Map.of(
                    "sent", sent,
                    "alreadySent", false,
                    "historyRecorded", historyRecorded);
        } catch (Exception e) {
            log.warn("Could not send verified demo confirmation for {}: {}", requestId, e.getMessage());
            return Map.of("sent", false, "alreadySent", false, "historyRecorded", false);
        }
    }

    public Map<String, Object> updateStatus(
            String requestId,
            Map<String, Object> raw,
            String actorUid) throws Exception {
        DocumentSnapshot before = requireVerifiedLead(requestId);
        String status = DemoRequestService.normalizeStatus(string(raw, "status"));
        String previous = DemoRequestService.normalizeStatus(before.getString("status"));

        boolean scheduleUpdate = "demo_scheduled".equals(status);
        if (previous.equals(status) && !scheduleUpdate) {
            Map<String, Object> unchanged = toLeadResponse(before);
            unchanged.put("previousStatus", previous);
            unchanged.put("notificationEmailSent", false);
            return unchanged;
        }

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", status);
        update.put("statusUpdatedAt", FieldValue.serverTimestamp());
        update.put("statusUpdatedBy", actorUid);

        ScheduleDetails schedule = null;
        if ("contacted".equals(status)) {
            update.put("contactedAt", FieldValue.serverTimestamp());
        }
        if ("demo_scheduled".equals(status)) {
            schedule = parseSchedule(raw);
            update.put("demoScheduledAt", FieldValue.serverTimestamp());
            update.put("demoScheduledFor", timestamp(schedule.scheduledAt()));
            update.put("demoScheduleTimezone", schedule.timezone());
            update.put("demoMeetingType", schedule.meetingType());
            update.put("demoMeetingDetails", schedule.meetingDetails());
        }
        if ("converted".equals(status)) {
            update.put("convertedAt", FieldValue.serverTimestamp());
        }
        if ("closed".equals(status)) {
            update.put("closedAt", FieldValue.serverTimestamp());
        }

        DocumentReference ref = before.getReference();
        ref.update(update).get();
        DocumentSnapshot after = ref.get().get();

        boolean notificationEmailSent = false;
        if ("demo_scheduled".equals(status) && schedule != null) {
            notificationEmailSent = sendScheduledEmail(after, schedule, actorUid);
        } else if ("converted".equals(status) && !"converted".equals(previous)) {
            notificationEmailSent = sendConvertedEmail(after, actorUid);
        } else if ("closed".equals(status) && truthy(raw == null ? null : raw.get("notifyRequester"))) {
            String closeMessage = cleanMultiline(raw == null ? null : raw.get("closeMessage"), 2000);
            notificationEmailSent = sendClosedEmail(after, closeMessage, actorUid);
        }

        Map<String, Object> result = toLeadResponse(ref.get().get());
        result.put("previousStatus", previous);
        result.put("notificationEmailSent", notificationEmailSent);
        return result;
    }

    public Map<String, Object> sendManualUpdate(
            String requestId,
            Map<String, Object> raw,
            String actorUid) throws Exception {
        DocumentSnapshot lead = requireVerifiedLead(requestId);
        String subject = clean(raw == null ? null : raw.get("subject"), 160);
        String message = cleanMultiline(raw == null ? null : raw.get("message"), 4000);

        if (subject.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email subject is required");
        }
        if (message.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Update message is too short");
        }

        String name = value(lead.getString("name"));
        String email = value(lead.getString("email"));
        String fullBody = "Hi " + name + ",\n\n" + message + "\n\nRegards,\nPickupPass";
        boolean sent = emailService.sendDemoUpdate(email, subject, fullBody);
        boolean historyRecorded = recordCommunication(
                lead.getReference(),
                "manual_update",
                subject,
                message,
                email,
                actorUid,
                sent);

        if (sent) {
            lead.getReference().update("lastCommunicationAt", FieldValue.serverTimestamp()).get();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The update could not be emailed. Check the mail service and try again.");
        }

        return Map.of(
                "sent", true,
                "historyRecorded", historyRecorded,
                "recipient", email,
                "subject", subject);
    }

    public Map<String, Object> communicationHistory(String requestId) throws Exception {
        DocumentSnapshot lead = requireVerifiedLead(requestId);
        List<QueryDocumentSnapshot> documents = lead.getReference()
                .collection("communications")
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .get()
                .getDocuments();

        List<Map<String, Object>> items = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("communicationId", document.getId());
            item.put("type", value(document.getString("type")));
            item.put("channel", value(document.getString("channel")));
            item.put("subject", value(document.getString("subject")));
            item.put("body", value(document.getString("body")));
            item.put("recipient", value(document.getString("recipient")));
            item.put("actorUid", value(document.getString("actorUid")));
            item.put("deliveryStatus", value(document.getString("deliveryStatus")));
            item.put("sentAt", timestampString(document, "sentAt"));
            items.add(item);
        }

        return Map.of(
                "requestId", lead.getId(),
                "communications", items,
                "total", items.size());
    }

    private boolean sendScheduledEmail(
            DocumentSnapshot lead,
            ScheduleDetails schedule,
            String actorUid) {
        String name = value(lead.getString("name"));
        String email = value(lead.getString("email"));
        String school = value(lead.getString("school"));
        String when = formatSchedule(schedule.scheduledAt(), schedule.timezone());
        String meetingLabel = meetingTypeLabel(schedule.meetingType());
        String subject = "Your PickupPass demo is scheduled";
        String historyBody = when + " · " + meetingLabel
                + (schedule.meetingDetails().isBlank() ? "" : " · " + schedule.meetingDetails());

        boolean sent = emailService.sendDemoScheduled(
                email,
                name,
                school,
                when,
                meetingLabel,
                schedule.meetingDetails());
        recordCommunication(
                lead.getReference(),
                "demo_scheduled",
                subject,
                historyBody,
                email,
                actorUid,
                sent);
        if (sent) {
            updateLastCommunication(lead.getReference());
        }
        return sent;
    }

    private boolean sendConvertedEmail(DocumentSnapshot lead, String actorUid) {
        String name = value(lead.getString("name"));
        String email = value(lead.getString("email"));
        String school = value(lead.getString("school"));
        String subject = "Next steps for " + school + " on PickupPass";
        String body = "School onboarding and launch-readiness next steps sent to the verified requester.";

        boolean sent = emailService.sendDemoConverted(email, name, school);
        recordCommunication(
                lead.getReference(),
                "converted_next_steps",
                subject,
                body,
                email,
                actorUid,
                sent);
        if (sent) {
            updateLastCommunication(lead.getReference());
        }
        return sent;
    }

    private boolean sendClosedEmail(
            DocumentSnapshot lead,
            String customMessage,
            String actorUid) {
        String name = value(lead.getString("name"));
        String email = value(lead.getString("email"));
        String school = value(lead.getString("school"));
        String subject = "Update on your PickupPass demo inquiry";
        String body = customMessage.isBlank()
                ? "Inquiry closure notice sent."
                : customMessage;

        boolean sent = emailService.sendDemoClosed(email, name, school, customMessage);
        recordCommunication(
                lead.getReference(),
                "inquiry_closed",
                subject,
                body,
                email,
                actorUid,
                sent);
        if (sent) {
            updateLastCommunication(lead.getReference());
        }
        return sent;
    }

    private boolean recordCommunication(
            DocumentReference leadRef,
            String type,
            String subject,
            String body,
            String recipient,
            String actorUid,
            boolean sent) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", type);
            record.put("channel", "email");
            record.put("subject", clean(subject, 200));
            record.put("body", cleanMultiline(body, 4500));
            record.put("recipient", normalizeEmail(recipient));
            record.put("actorUid", value(actorUid));
            record.put("deliveryStatus", sent ? "sent" : "failed");
            record.put("sentAt", FieldValue.serverTimestamp());
            leadRef.collection("communications").add(record).get();
            return true;
        } catch (Exception e) {
            log.warn("Could not record demo communication for {}: {}", leadRef.getId(), e.getMessage());
            return false;
        }
    }

    private void updateLastCommunication(DocumentReference ref) {
        try {
            ref.update("lastCommunicationAt", FieldValue.serverTimestamp()).get();
        } catch (Exception e) {
            log.warn("Could not update last communication time for {}: {}", ref.getId(), e.getMessage());
        }
    }

    private DocumentSnapshot requireVerifiedLead(String rawRequestId) throws Exception {
        String requestId = cleanRequestId(rawRequestId);
        DocumentSnapshot lead = firestore.collection("demoRequests").document(requestId).get().get();
        if (!lead.exists() || !Boolean.TRUE.equals(lead.getBoolean("emailVerified"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo request not found");
        }
        return lead;
    }

    private static ScheduleDetails parseSchedule(Map<String, Object> raw) {
        String scheduledAtRaw = clean(raw == null ? null : raw.get("scheduledAt"), 80);
        String timezone = clean(raw == null ? null : raw.get("timezone"), 80);
        String meetingType = normalizeMeetingType(string(raw, "meetingType"));
        String meetingDetails = cleanMultiline(raw == null ? null : raw.get("meetingDetails"), 1000);

        if (scheduledAtRaw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo date and time are required");
        }
        Instant scheduledAt;
        try {
            scheduledAt = Instant.parse(scheduledAtRaw);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo date and time are invalid");
        }
        if (!scheduledAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo must be scheduled in the future");
        }
        if (timezone.isBlank()) timezone = "Asia/Manila";
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo timezone is invalid");
        }

        return new ScheduleDetails(scheduledAt, timezone, meetingType, meetingDetails);
    }

    private static String normalizeMeetingType(String raw) {
        String value = clean(raw, 30).toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!MEETING_TYPES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid meeting type");
        }
        return value;
    }

    private static String meetingTypeLabel(String meetingType) {
        return switch (meetingType) {
            case "video" -> "Video call";
            case "phone" -> "Phone call";
            case "onsite" -> "On-site meeting";
            default -> "Other arrangement";
        };
    }

    private static String formatSchedule(Instant scheduledAt, String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime local = ZonedDateTime.ofInstant(scheduledAt, zone);
        return SCHEDULE_FORMAT.format(local) + " (" + timezone + ")";
    }

    private static Map<String, Object> toLeadResponse(DocumentSnapshot document) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", document.getId());
        result.put("name", value(document.getString("name")));
        result.put("email", value(document.getString("email")));
        result.put("school", value(document.getString("school")));
        result.put("phone", value(document.getString("phone")));
        result.put("message", value(document.getString("message")));
        result.put("status", DemoRequestService.normalizeStatus(document.getString("status")));
        result.put("source", value(document.getString("source")));
        result.put("emailVerified", Boolean.TRUE.equals(document.getBoolean("emailVerified")));
        result.put("emailDomain", value(document.getString("emailDomain")));
        result.put("emailDomainType", value(document.getString("emailDomainType")));
        result.put("emailTrustLabel", DemoRequestService.trustLabel(document.getString("emailDomainType")));
        result.put("createdAt", timestampString(document, "createdAt"));
        result.put("verifiedAt", timestampString(document, "verifiedAt"));
        result.put("statusUpdatedAt", timestampString(document, "statusUpdatedAt"));
        result.put("lastCommunicationAt", timestampString(document, "lastCommunicationAt"));
        result.put("demoScheduledFor", timestampString(document, "demoScheduledFor"));
        result.put("demoScheduleTimezone", value(document.getString("demoScheduleTimezone")));
        result.put("demoMeetingType", value(document.getString("demoMeetingType")));
        result.put("demoMeetingDetails", value(document.getString("demoMeetingDetails")));
        return result;
    }

    private static String cleanRequestId(String raw) {
        String id = clean(raw, 200);
        if (id.isBlank() || id.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demo request reference");
        }
        return id;
    }

    private static String string(Map<String, Object> raw, String key) {
        return raw == null ? "" : value(raw.get(key));
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalizeEmail(String email) {
        return value(email).trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(Object raw, int max) {
        if (raw == null) return "";
        String value = String.valueOf(raw).trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String cleanMultiline(Object raw, int max) {
        if (raw == null) return "";
        String value = String.valueOf(raw)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim()
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.of(Date.from(instant));
    }

    private static String timestampString(DocumentSnapshot document, String field) {
        Timestamp timestamp = document.getTimestamp(field);
        return timestamp == null ? "" : timestamp.toDate().toInstant().toString();
    }

    private record ScheduleDetails(
            Instant scheduledAt,
            String timezone,
            String meetingType,
            String meetingDetails) { }
}
