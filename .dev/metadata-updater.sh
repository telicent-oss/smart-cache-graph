#!/bin/bash
set -e

# Set the path to this script's directory, which is used to locate the metadata-updater.py script.
SCRIPT_PATH=$(dirname "$(realpath $0)")/metadata-updater.py
GENERATOR_PATH=$(dirname "$(realpath $0)")/readme-generator-for-helm

# Copy a fresh version of the metadata-updater.py script from GitHub.
SCRIPT_URL="https://raw.githubusercontent.com/telicent-oss/shared-workflows/refs/heads/main/.github/actions/helm-metadata-updater/metadata-updater.py"
curl -L -s -o "$SCRIPT_PATH" "$SCRIPT_URL"

# Copy a fresh version of the readme-generator-for-helm binary from GitHub.
if [[ "$@" != *"--ci"* ]]; then
  GENERATOR_URL="https://raw.githubusercontent.com/telicent-oss/shared-workflows/refs/heads/main/.github/actions/helm-metadata-updater/readme-generator-for-helm"
  curl -L -s -o "$GENERATOR_PATH" "$GENERATOR_URL"
  chmod +x $GENERATOR_PATH
fi

# Run the script, passing through any arguments.
python "$SCRIPT_PATH" "$@"
