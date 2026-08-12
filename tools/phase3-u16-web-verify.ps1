$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
node (Join-Path $ScriptDir "phase3-u16-web-verify.js")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
