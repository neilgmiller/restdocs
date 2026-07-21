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

dest_dir="$allego_server_repo_dir/doc/api-docs"

if [[ ! -d "$dest_dir" ]]; then
  echo "Error: destination directory '$dest_dir' does not exist." >&2
  exit 1
fi

src_file="$repo_root/rest_api_doc.vm"
dest_file="$dest_dir/rest_api_doc.vm"

cp "$src_file" "$dest_file"

echo "Copied $src_file -> $dest_file"
