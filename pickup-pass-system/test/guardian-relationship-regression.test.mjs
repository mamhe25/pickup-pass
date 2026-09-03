import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";

const root = new URL("../", import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, root), "utf8");
}

test("teacher roster resolves legacy and explicit primary guardian state", async () => {
  const source = await read("frontend/teacher/students.html");
  assert.match(source, /function\s+primaryGuardianUid\(student\)/);
  assert.match(source, /guardians\[uid\]\?\.isPrimary\s*===\s*true/);
  assert.match(source, /hasOwnProperty\.call\(entry,\s*"isPrimary"\)/);
  assert.match(source, /Boolean\(primaryGuardianUid\(student\)\)/);
});

test("teacher registration blocks a second primary for legacy records", async () => {
  const source = await read("frontend/teacher/register-parent.html");
  assert.match(source, /function\s+primaryGuardianUid\(student\)/);
  assert.match(
    source,
    /const\s+hasPrimary\s*=\s*Boolean\(primaryGuardianUid\(student\)\)/
  );
});

test("teacher guardian management normalizes primary state before rendering", async () => {
  const source = await read("frontend/teacher/manage-guardians.html");
  assert.match(source, /function\s+primaryGuardianUid\(\)/);
  assert.match(source, /const\s+primaryUid\s*=\s*primaryGuardianUid\(\)/);
  assert.match(source, /isPrimary:\s*uid\s*===\s*primaryUid/);
});

test("parent guardian management protects normalized legacy primary", async () => {
  const source = await read("frontend/parent/manage-guardians.html");
  assert.match(source, /function\s+primaryGuardianUid\(\)/);
  assert.match(source, /isPrimary:\s*uid\s*===\s*primaryUid/);
});

test("staff guardian profile API never erases a linked relationship on identity lookup failure", async () => {
  const source = await read(
    "backend/src/main/java/com/pickuppass/controller/ParentGuardianController.java"
  );
  assert.match(
    source,
    /GuardianRelationshipResolver\.resolvePrimaryUid\(studentSnap\)/
  );
  assert.match(source, /item\.put\("identityAvailable", identityAvailable\)/);
  assert.doesNotMatch(
    source,
    /if\s*\(!user\.exists\(\)\s*\|\|\s*!actor\.getSchoolId\(\)\.equals\(user\.getString\("schoolId"\)\)\)\s*continue;/
  );
});
