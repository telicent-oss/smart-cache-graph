#!/usr/bin/env bash
#
# Copyright (C) 2022 Telicent Limited
#

function abort() {
  echo "$@" 1>&2
  exit 255
}

function echorun() {
  echo "$@"
  "$@"
}

function detectBranch() {
  if [ -n "${BRANCH}" ]; then
    echo "${BRANCH}"
  else
    local CURRENT_BRANCH
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
    if [ -z "${CURRENT_BRANCH}" ]; then
      CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
    fi
    echo "${CURRENT_BRANCH}"
  fi
}

function selectTag() {
  local USER_TAG=$1
  if [ -n "${USER_TAG}" ]; then
    echo "${USER_TAG//\//-}"
  elif [ "${BRANCH}" == "master" ]; then
    echo "latest"
  else
    echo "${BRANCH//\//-}"
  fi
}

command -v docker >/dev/null 2>&1 || abort "This script requires the docker command on your PATH"

SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
SCRIPT_DIR=$(cd "${SCRIPT_DIR}" && pwd)

BRANCH=$(detectBranch)
DOCKER_TAG=$(selectTag "$1")
DOCKER_REPO=$2

echo "Docker Tag is ${DOCKER_TAG}"
if [ -n "${DOCKER_REPO}" ]; then
  echo "Docker Registry is ${DOCKER_REPO}, image will be pushed to this repository"
else
  echo "No Docker Registry defined, image will be built locally only"
fi
if [ -n "${BRANCH}" ]; then
  echo "Current Branch is ${BRANCH}"
fi

PROJECT_VERSION=
if command -v mvn >/dev/null 2>&1; then
  PROJECT_VERSION=$(cd "${SCRIPT_DIR}" && mvn help:evaluate --batch-mode -Dexpression=project.version 2>/dev/null | grep -v "\[")
else
  PROJECT_VERSION=$(grep "<version>" "${SCRIPT_DIR}/pom.xml" 2>/dev/null | head -n 1 | awk -F "[><]" '{print $3}')
fi
if [ -z "${PROJECT_VERSION}" ]; then
  abort "Failed to detect Project Version"
fi
echo "Detected Project Version is ${PROJECT_VERSION}"
echo ""

export DOCKER_BUILDKIT=1

function buildImage() {
  local IMAGE_NAME="smart-cache-graph"
  local BUILD_TARGET="smart-cache-graph"
  if [ -n "${DOCKER_REPO}" ]; then
    IMAGE_NAME="${DOCKER_REPO}/${IMAGE_NAME}"
  fi

  local DOCKER_ARGS=(
    "docker"
  )
  if [ -n "${TARGET_PLATFORMS}" ]; then
    DOCKER_ARGS+=(
      "buildx"
      "build"
      "--platform"
      "${TARGET_PLATFORMS}"
    )
    if [ -n "${DOCKER_REPO}" ]; then
      DOCKER_ARGS+=("--push")
      if [ "${DOCKER_TAG}" != "latest" ] && [ "${BRANCH}" == "main" ]; then
        DOCKER_ARGS+=(
          "-t"
          "${IMAGE_NAME}:latest"
        )
      fi
    fi
  else
    DOCKER_ARGS+=("build")
  fi

  DOCKER_ARGS+=(
    "--no-cache"
    "--target"
    "${BUILD_TARGET}"
    "-t"
    "${IMAGE_NAME}:${DOCKER_TAG}"
    "-f"
    "${SCRIPT_DIR}/scg-docker/Dockerfile"
    "--build-arg"
    "PROJECT_VERSION=${PROJECT_VERSION}"
  )

  if [ -n "${EXTRA_BUILD_ARGS}" ]; then
    DOCKER_ARGS+=( ${EXTRA_BUILD_ARGS} )
  fi

  DOCKER_ARGS+=(
    "."
  )

  echo "Building Docker Image ${IMAGE_NAME}:${DOCKER_TAG}..."
  echorun "${DOCKER_ARGS[@]}" || abort "Docker build failed"
}

function pushImage() {
  local IMAGE_NAME="smart-cache-graph"
  if [ -n "${DOCKER_REPO}" ]; then
    IMAGE_NAME="${DOCKER_REPO}/${IMAGE_NAME}"
    echo "Pushing image ${IMAGE_NAME}:${DOCKER_TAG}..."
    echorun docker push "${IMAGE_NAME}:${DOCKER_TAG}" || abort "Docker push failed"
    if [ "${DOCKER_TAG}" != "latest" ] && [ "${BRANCH}" == "main" ]; then
      echorun docker tag "${IMAGE_NAME}:${DOCKER_TAG}" "${IMAGE_NAME}:latest" || abort "Docker tag failed"
      echorun docker push "${IMAGE_NAME}:latest" || abort "Docker push failed"
    fi
    echo ""
  fi
}

buildImage
if [ -z "${TARGET_PLATFORMS}" ]; then
  pushImage
fi
