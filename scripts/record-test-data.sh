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

# A different moon version cannot always read the cache of the previous version. Thus remove the cache.
rm -rf "$workspace/.moon/cache"

# Write each file to a temporary directory first. If a moon command fails, the script stops, and the recorded files do not change.
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

npx moon --version > "$tmp/moon-version.txt"
# The output of moon query projects contains the absolute path of each project root.
# Replace the path of the workspace with /workspace. Thus the file does not contain paths of this computer.
npx moon query projects | sed "s|$workspace|/workspace|g" > "$tmp/query-projects.json"

# Record one inherited task, one inherited task that a project changes, and one local task.
for target in api:audit web:lint web:build; do
  npx moon task "$target" --json > "$tmp/task-${target/:/-}.json"
done

# The recorded files must not contain paths of this computer.
if grep -rqF "$root" "$tmp"; then
  echo "error: the recorded files contain the path $root" >&2
  exit 1
fi

mkdir -p "$out"
cp "$tmp"/* "$out/"

echo "Recorded the output of $(cat "$out/moon-version.txt") in $out"
