#!/usr/bin/env bash
# Record the moon CLI output for the test workspace.
# The tests use the recorded files. They do not start the moon CLI.
# Refer to docs/development.md.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
workspace="$root/src/test/testData/workspace"
out="$root/src/test/testData/moon"

cd "$workspace"

# Install the moon version that package.json gives.
npm ci --no-audit --no-fund --silent

mkdir -p "$out"
npx moon --version > "$out/moon-version.txt"
npx moon query tasks > "$out/query-tasks.json"

# Record one inherited task, one inherited task that a project changes, and one local task.
for target in api:audit web:lint web:build; do
  npx moon task "$target" --json > "$out/task-${target/:/-}.json"
done

# The recorded files must not contain paths of this computer.
if grep -rqF "$root" "$out"; then
  echo "error: the recorded files contain the path $root" >&2
  exit 1
fi

echo "Recorded the output of $(cat "$out/moon-version.txt") in $out"
