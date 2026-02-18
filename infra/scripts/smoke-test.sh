#!/usr/bin/env bash
set -euo pipefail

curl -fsS http://localhost:8080/actuator/health | grep -q 'UP'
curl -fsS http://localhost:8081/health | grep -q 'ok'
