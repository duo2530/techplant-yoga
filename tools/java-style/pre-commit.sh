#!/usr/bin/env bash
set -eo pipefail

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

root=$(git rev-parse --show-toplevel)
cd "$root"
root=$(pwd -P)
mkdir -p "$root/.tmp"
temp_root=$(mktemp -d "$root/.tmp/java-style.XXXXXXXX")
cleanup() {
    case "$temp_root" in
        "$root"/.tmp/java-style.*) rm -rf -- "$temp_root" ;;
    esac
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

git diff --cached --name-only --diff-filter=ACMR -z > "$temp_root/staged"
files=()
hashes=()
index_hashes=()
while IFS= read -r -d '' file; do
    case "$file" in
        backend/*/src/main/java/*.java|backend/*/src/test/java/*.java)
            files+=("$file")
            ;;
    esac
done < "$temp_root/staged"
if [ "${#files[@]}" -eq 0 ]; then
    exit 0
fi

command -v mvn >/dev/null 2>&1 || fail 'Maven is required to format staged Java files.'
for file in "${files[@]}"; do
    git diff --quiet -- "$file" ||
        fail "Partially staged file: $file. Stage the complete file or unstage it before committing."
    hashes+=("$(git hash-object --no-filters -- "$file")")
    index_hashes+=("$(git rev-parse ":$file")")
    destination="$temp_root/sources/$file"
    mkdir -p "${destination%/*}"
    cp -- "$file" "$destination"
done

if ! mvn -B -f tools/java-style/pom.xml "-Dstyle.sourceDirectory=$temp_root/sources" \
        formatter:format checkstyle:check; then
    fail 'Java style validation failed. No source files were updated.'
fi

for ((i = 0; i < ${#files[@]}; i++)); do
    file=${files[$i]}
    [ "$(git hash-object --no-filters -- "$file")" = "${hashes[$i]}" ] ||
        fail "File changed during formatting: $file. Retry the commit."
    [ "$(git rev-parse ":$file")" = "${index_hashes[$i]}" ] ||
        fail "Staging changed during formatting: $file. Retry the commit."
    git diff --quiet -- "$file" || fail "File changed during formatting: $file. Retry the commit."
done
for file in "${files[@]}"; do
    cp -- "$temp_root/sources/$file" "$file"
done
git add -- "${files[@]}"
