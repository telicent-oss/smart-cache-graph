#!/bin/bash
docker buildx build --platform linux/amd64 \
  -t 098669589541.dkr.ecr.eu-west-2.amazonaws.com/scg-deletion-worker:core-1191 \
  -f scg-deletion-worker/Dockerfile \
  --build-arg PROJECT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl scg-deletion-worker) \
  --target scg-deletion-worker \
  --push \
  .
