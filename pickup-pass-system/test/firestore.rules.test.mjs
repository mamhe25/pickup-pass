import { readFile } from "node:fs/promises";
import { after, before, beforeEach, describe, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc } from "firebase/firestore";

const projectId = "demo-pickup-pass";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules: await readFile(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(async () => testEnv.cleanup());

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await Promise.all([
      setDoc(doc(db, "schools/school-a"), { name: "School A", status: "active" }),
      setDoc(doc(db, "schools/school-b"), { name: "School B", status: "active" }),
      setDoc(doc(db, "users/teacher-a"), { schoolId: "school-a", role: "teacher", isActive: true }),
      setDoc(doc(db, "users/teacher-b"), { schoolId: "school-b", role: "teacher", isActive: true }),
      setDoc(doc(db, "users/suspended-a"), { schoolId: "school-a", role: "teacher", isActive: false }),
      setDoc(doc(db, "users/guardian-a"), { schoolId: "school-a", role: "parent", isActive: true, photoUrl: "old" }),
      setDoc(doc(db, "users/guardian-b"), { schoolId: "school-b", role: "parent", isActive: true }),
      setDoc(doc(db, "students/student-a"), { schoolId: "school-a", guardianUids: ["guardian-a"] }),
      setDoc(doc(db, "students/student-b"), { schoolId: "school-b", guardianUids: ["guardian-b"] }),
      setDoc(doc(db, "pickupTokens/token-a"), { schoolId: "school-a", studentId: "student-a", parentUid: "guardian-a", used: false }),
      setDoc(doc(db, "exitLogs/log-a"), { schoolId: "school-a", parentUid: "guardian-a", studentId: "student-a" }),
      setDoc(doc(db, "notifications/notification-a"), { recipientUid: "guardian-a", read: false, message: "Student released" }),
    ]);
  });
});

function dbFor(uid, schoolId, role) {
  return testEnv.authenticatedContext(uid, { schoolId, role }).firestore();
}

describe("Firestore tenant isolation", () => {
  test("active staff can read their school but not another school", async () => {
    const db = dbFor("teacher-a", "school-a", "teacher");
    await assertSucceeds(getDoc(doc(db, "schools/school-a")));
    await assertSucceeds(getDoc(doc(db, "students/student-a")));
    await assertFails(getDoc(doc(db, "schools/school-b")));
    await assertFails(getDoc(doc(db, "students/student-b")));
  });

  test("suspended staff lose same-school access", async () => {
    const db = dbFor("suspended-a", "school-a", "teacher");
    await assertFails(getDoc(doc(db, "schools/school-a")));
    await assertFails(getDoc(doc(db, "students/student-a")));
  });

  test("guardian can read only students explicitly linked to their uid", async () => {
    const db = dbFor("guardian-a", "school-a", "parent");
    await assertSucceeds(getDoc(doc(db, "students/student-a")));
    await assertFails(getDoc(doc(db, "students/student-b")));
  });

  test("pickup token ledger is inaccessible to every client role", async () => {
    await assertFails(getDoc(doc(dbFor("teacher-a", "school-a", "teacher"), "pickupTokens/token-a")));
    await assertFails(getDoc(doc(dbFor("guardian-a", "school-a", "parent"), "pickupTokens/token-a")));
    await assertFails(getDoc(doc(dbFor("master-1", null, "master_admin"), "pickupTokens/token-a")));
  });

  test("guardian can update only their own photo field", async () => {
    const db = dbFor("guardian-a", "school-a", "parent");
    await assertSucceeds(updateDoc(doc(db, "users/guardian-a"), { photoUrl: "new" }));
    await assertFails(updateDoc(doc(db, "users/guardian-a"), { role: "school_admin" }));
    await assertFails(updateDoc(doc(db, "users/guardian-b"), { photoUrl: "stolen" }));
  });

  test("exit log is visible only to same-school staff or its guardian", async () => {
    await assertSucceeds(getDoc(doc(dbFor("teacher-a", "school-a", "teacher"), "exitLogs/log-a")));
    await assertSucceeds(getDoc(doc(dbFor("guardian-a", "school-a", "parent"), "exitLogs/log-a")));
    await assertFails(getDoc(doc(dbFor("teacher-b", "school-b", "teacher"), "exitLogs/log-a")));
    await assertFails(getDoc(doc(dbFor("guardian-b", "school-b", "parent"), "exitLogs/log-a")));
  });

  test("notification recipient can mark read but cannot alter its contents", async () => {
    const ownerDb = dbFor("guardian-a", "school-a", "parent");
    const notification = doc(ownerDb, "notifications/notification-a");
    await assertSucceeds(getDoc(notification));
    await assertSucceeds(updateDoc(notification, { read: true }));
    await assertFails(updateDoc(notification, { message: "forged" }));
    await assertFails(getDoc(doc(dbFor("guardian-b", "school-b", "parent"), "notifications/notification-a")));
  });
});
