#!/usr/bin/env bash

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
SCRIPT_DIR=$(cd "${SCRIPT_DIR}" && pwd)

if [ ! command -v mmdc ] >/dev/null 2>&1; then
  echo "Required mmdc command not found, please install by following instructions from https://github.com/mermaid-js/mermaid-cli"
  exit 1
fi

IMAGE_DIR=${SCRIPT_DIR}/images
echo "Processing Mermaid diagrams in directory ${SCRIPT_DIR}/diagrams/"
echo ""
for DIAGRAM in $(ls ${SCRIPT_DIR}/diagrams/*.mmd); do
  FILENAME=$(basename ${DIAGRAM})
  FILENAME=${FILENAME%%\.*}
  SVG_NAME="${IMAGE_DIR}/${FILENAME}.svg"
  PNG_NAME="${IMAGE_DIR}/${FILENAME}.png"
  echo "Generating SVG Image for Diagram $(basename ${DIAGRAM})..."
  mmdc -i ${DIAGRAM} -o ${SVG_NAME}
  echo "Generating PNG Image for Diagram $(basename ${DIAGRAM})..."
  mmdc -i ${DIAGRAM} -o ${PNG_NAME}
  echo ""
done