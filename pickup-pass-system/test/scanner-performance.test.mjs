import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../', import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8');
}

test('android qr camera keeps decoding work off the main thread', async () => {
  const scanner = await read('../pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/teacher/scanner/QrScannerView.kt');

  assert.match(scanner, /Executors\.newSingleThreadExecutor\(\)/);
  assert.match(scanner, /setAnalyzer\(analysisExecutor\)/);
  assert.match(scanner, /STRATEGY_KEEP_ONLY_LATEST/);
  assert.match(scanner, /Size\(960,\s*540\)/);
  assert.match(scanner, /ImplementationMode\.PERFORMANCE/);
  assert.match(scanner, /Barcode\.FORMAT_QR_CODE/);
  assert.match(scanner, /AtomicBoolean/);
  assert.match(scanner, /FocusMeteringAction/);
  assert.match(scanner, /startFocusAndMetering/);
  assert.doesNotMatch(scanner, /setAnalyzer\(ContextCompat\.getMainExecutor/);
});

test('android scanner loads independent identity records concurrently', async () => {
  const viewModel = await read('../pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/teacher/scanner/ScannerViewModel.kt');

  assert.match(viewModel, /coroutineScope\s*\{/);
  assert.match(viewModel, /studentDeferred\s*=\s*async/);
  assert.match(viewModel, /guardianDeferred\s*=\s*async/);
  assert.match(viewModel, /studentDeferred\.await\(\)\s+to\s+guardianDeferred\.await\(\)/);
  assert.match(viewModel, /withContext\(Dispatchers\.Default\)/);
});
