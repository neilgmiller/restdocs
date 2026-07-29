#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
props_file="$script_dir/copy-doc-template.properties"
template_file="$script_dir/copy-doc-template.properties.template"

if [[ ! -f "$props_file" ]]; then
  echo "Error: $props_file not found." >&2
  echo "Copy $template_file to $props_file and set allegoServerRepoDir to your local allego1.0 checkout." >&2
  exit 1
fi

allego_server_repo_dir="$(grep -E '^allegoServerRepoDir=' "$props_file" | cut -d'=' -f2-)"

if [[ -z "$allego_server_repo_dir" ]]; then
  echo "Error: allegoServerRepoDir is not set in $props_file." >&2
  exit 1
fi

if [[ ! -d "$allego_server_repo_dir" ]]; then
  echo "Error: allegoServerRepoDir '$allego_server_repo_dir' does not exist." >&2
  exit 1
fi

restdocs_tool_dir="$allego_server_repo_dir/doc/api-docs/restdocs-tool"

if [[ ! -d "$restdocs_tool_dir" ]]; then
  echo "Error: destination directory '$restdocs_tool_dir' does not exist." >&2
  exit 1
fi

restdocs_properties="$restdocs_tool_dir/restdocs.properties"

if [[ ! -f "$restdocs_properties" ]]; then
  echo "Error: '$restdocs_properties' not found." >&2
  exit 1
fi

cd "$repo_root"
./gradlew clean distZip

zip_files=("$repo_root"/build/distributions/*.zip)

if [[ ${#zip_files[@]} -ne 1 ]]; then
  echo "Error: expected exactly one zip in build/distributions, found ${#zip_files[@]}." >&2
  exit 1
fi

zip_file="${zip_files[0]}"
zip_basename="$(basename "$zip_file" .zip)"
version="${zip_basename#restdocs-}"

if [[ -z "$version" || "$version" == "$zip_basename" ]]; then
  echo "Error: could not determine version from zip name '$zip_basename'." >&2
  exit 1
fi

install_dir="$restdocs_tool_dir/.restdocs/$version"

if [[ -d "$install_dir" ]]; then
  rm -rf "$install_dir"
fi

mkdir -p "$restdocs_tool_dir/.restdocs"

extract_tmp_dir="$(mktemp -d)"
trap 'rm -rf "$extract_tmp_dir"' EXIT

unzip -q "$zip_file" -d "$extract_tmp_dir"
mv "$extract_tmp_dir/$zip_basename" "$install_dir"

if [[ "$(grep -c '^distributionVersion=' "$restdocs_properties")" -ne 1 ]]; then
  echo "Error: expected exactly one 'distributionVersion=' line in '$restdocs_properties'." >&2
  exit 1
fi

sed -i '' "s/^distributionVersion=.*/distributionVersion=$version/" "$restdocs_properties"

echo "Built $zip_file"
echo "Installed to $install_dir"
echo "Updated $restdocs_properties to distributionVersion=$version"
