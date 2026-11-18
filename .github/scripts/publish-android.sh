#!/usr/bin/env bash
set -euo pipefail

repository="repos/$GITHUB_REPOSITORY"
run_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
tag="$GITHUB_REF_NAME"
if [[ "$GITHUB_REF_TYPE" == branch ]]; then
    tag=ci
fi

find_release() {
    # Listing releases includes drafts, which the REST lookup by tag omits.
    gh api --paginate --slurp "$repository/releases?per_page=100" |
        jq -c --arg tag "$tag" 'first(.[][] | select(.tag_name == $tag)) // null'
}

release=$(find_release)
if [[ "$release" == null ]]; then
    if [[ "$tag" == ci ]]; then
        gh release create ci --draft --prerelease --latest=false --target "$SOURCE_COMMIT" \
            --title 'CI build' --notes ''
    else
        notes=$(printf '待补充中文更新说明。\n\n提交 `%s` · [Actions 构建](%s)' "$SOURCE_COMMIT" "$run_url")
        gh release create "$tag" --verify-tag --draft --title "$tag" --notes "$notes"
    fi
    release=$(find_release)
fi

release_id=$(jq -er '.id' <<< "$release")
ci_asset_pattern='^KIRAKIRA-[0-9]+(\.[0-9]+){1,2}-ci-(?<run>[0-9]+)-[0-9a-f]{7}\.apk$'
if [[ "$tag" == ci ]]; then
    if ! jq -e '.prerelease and (.immutable != true)' <<< "$release" >/dev/null; then
        echo "::error::The ci release must be a mutable prerelease."
        exit 1
    fi

    newest_run=$(jq --arg pattern "$ci_asset_pattern" '
        [.assets[] | select(.state == "uploaded") | .name | capture($pattern).run | tonumber]
        | max // 0
    ' <<< "$release")
    if (( newest_run > GITHUB_RUN_NUMBER )); then
        echo "CI build $newest_run already has an APK attached."
        exit 0
    fi
elif jq -e '.draft == false' <<< "$release" >/dev/null; then
    echo "Release $tag is already published."
    exit 0
fi

asset=$(jq -c --arg name "$APK_NAME" 'first(.assets[] | select(.name == $name)) // null' <<< "$release")
if ! jq -e '.state == "uploaded"' <<< "$asset" >/dev/null; then
    # GitHub can leave a starter asset after an interrupted upload.
    if [[ "$asset" != null ]]; then
        asset_id=$(jq -er '.id' <<< "$asset")
        gh api --method DELETE "$repository/releases/assets/$asset_id"
    fi
    gh release upload "$tag" "dist/$APK_NAME"
fi

if [[ "$tag" != ci ]]; then
    exit 0
fi

# Move the rolling tag only once the new APK is available.
ci_ref=$(gh api "$repository/git/matching-refs/tags/ci" |
    jq -r '.[] | select(.ref == "refs/tags/ci") | .object.sha')
if [[ -z "$ci_ref" ]]; then
    gh api --method POST "$repository/git/refs" \
        -f ref=refs/tags/ci -f sha="$SOURCE_COMMIT" --silent
elif [[ "$ci_ref" != "$SOURCE_COMMIT" ]]; then
    gh api --method PATCH "$repository/git/refs/tags/ci" \
        -f sha="$SOURCE_COMMIT" -F force=true --silent
fi

apk_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/releases/download/ci/$APK_NAME"
notes=$(printf '[下载 %s](%s)\n\n构建 #%s · 提交 `%s` · [Actions 构建](%s)' \
    "$APK_NAME" "$apk_url" "$GITHUB_RUN_NUMBER" "$SOURCE_COMMIT" "$run_url")
gh api --method PATCH "$repository/releases/$release_id" \
    -f body="$notes" -F draft=false -F prerelease=true -f make_latest=false --silent

old_assets=$(jq -r --arg pattern "$ci_asset_pattern" --argjson run "$GITHUB_RUN_NUMBER" '
    .assets[] | select((.name | capture($pattern).run | tonumber) < $run) | .id
' <<< "$release")
while IFS= read -r asset_id; do
    if [[ -n "$asset_id" ]]; then
        gh api --method DELETE "$repository/releases/assets/$asset_id"
    fi
done <<< "$old_assets"
