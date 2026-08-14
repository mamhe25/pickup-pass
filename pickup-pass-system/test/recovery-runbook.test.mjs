import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const runbook = readFileSync('../DISASTER_RECOVERY_RUNBOOK.md', 'utf8');
const controller = readFileSync(
  'backend/src/main/java/com/pickuppass/controller/MasterDisasterRecoveryController.java',
  'utf8',
);
const service = readFileSync(
  'backend/src/main/java/com/pickuppass/service/FirestoreDisasterRecoveryService.java',
  'utf8',
);
const configuration = readFileSync('backend/src/main/resources/application.yml', 'utf8');

const endpointContracts = [
  ['/overview', 'GET /api/master-admin/disaster-recovery/overview'],
  ['/protection/free', 'POST /api/master-admin/disaster-recovery/protection/free'],
  ['/protection/startup', 'POST /api/master-admin/disaster-recovery/protection/startup'],
  ['/protection/recommended', 'POST /api/master-admin/disaster-recovery/protection/recommended'],
  ['/recovery-drills', 'POST /api/master-admin/disaster-recovery/recovery-drills'],
  ['/recovery-drills/{jobId}/refresh', 'POST /api/master-admin/disaster-recovery/recovery-drills/{jobId}/refresh'],
];

test('runbook covers every operator-facing disaster-recovery endpoint', () => {
  assert.match(controller, /@RequestMapping\("\/api\/master-admin\/disaster-recovery"\)/);
  assert.match(controller, /hasRole\('master_admin'\)/);
  for (const [controllerPath, documentedEndpoint] of endpointContracts) {
    assert.ok(controller.includes(`\"${controllerPath}\"`), `controller missing ${controllerPath}`);
    assert.ok(runbook.includes(`\`${documentedEndpoint}\``), `runbook missing ${documentedEndpoint}`);
  }
});

test('runbook preserves implemented recovery safety gates and confirmation phrases', () => {
  const contracts = [
    ['FIRESTORE_DR_ENABLED', configuration],
    ['FIRESTORE_DR_ALLOW_RESTORE_DRILLS', configuration],
    ['FIRESTORE_DR_PROJECT_ID', configuration],
    ['FIRESTORE_DR_DATABASE_ID', configuration],
    ['FIRESTORE_DR_MAX_BACKUP_AGE_HOURS', configuration],
    ['ENABLE FREE SAFEGUARDS', service],
    ['ENABLE STARTUP BACKUP', service],
    ['ENABLE BACKUP PROTECTION', service],
    ['RESTORE TO ISOLATED DATABASE', service],
    ['Only a READY backup can be used for a recovery drill', service],
    ['productionCutoverAutomatic', service],
    ['sourceVerified', service],
  ];
  for (const [contract, implementation] of contracts) {
    assert.ok(implementation.includes(contract), `implementation missing ${contract}`);
    assert.ok(runbook.includes(contract), `runbook missing ${contract}`);
  }
});

test('runbook defines measurable recovery and validation evidence', () => {
  for (const section of [
    '## Recovery objectives',
    '## Safety invariants',
    '## Backup-health check',
    '## Incident recovery procedure',
    '## Quarterly restore-drill evidence',
  ]) assert.ok(runbook.includes(section), `runbook missing ${section}`);
  for (const requirement of ['Target RPO', 'Target RTO', 'cross-tenant', 'cannot be replayed', 'actual RPO', 'actual RTO']) {
    assert.ok(runbook.includes(requirement), `runbook missing ${requirement}`);
  }
});
