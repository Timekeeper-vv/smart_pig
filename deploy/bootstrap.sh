#!/usr/bin/env bash
set -Eeuo pipefail
REPO_URL="${REPO_URL:-${1:-}}"
APP_DIR="${APP_DIR:-/opt/smart_pig}"
BRANCH="${BRANCH:-main}"
[ -n "$REPO_URL" ] || { echo "用法: REPO_URL=https://... bash bootstrap.sh，或 bash bootstrap.sh https://仓库地址"; exit 1; }
if [ "$(id -u)" = 0 ]; then SUDO=""; else SUDO="sudo"; fi
command -v git >/dev/null 2>&1 || { $SUDO apt-get update 2>/dev/null && $SUDO apt-get install -y git || $SUDO yum install -y git; }
if [ -d "$APP_DIR/.git" ]; then git -C "$APP_DIR" fetch --all; git -C "$APP_DIR" checkout "$BRANCH"; git -C "$APP_DIR" pull --ff-only origin "$BRANCH"; else $SUDO mkdir -p "$(dirname "$APP_DIR")"; $SUDO chown "$(id -u):$(id -g)" "$(dirname "$APP_DIR")"; git clone --branch "$BRANCH" "$REPO_URL" "$APP_DIR"; fi
cd "$APP_DIR"
bash scripts/aliyun-start.sh install-deps
[ -f .env ] || { cp deploy/env.example .env; echo; echo "环境已安装。请编辑 $APP_DIR/.env 后执行："; echo "cd $APP_DIR && vim .env && bash scripts/aliyun-start.sh production"; exit 0; }
bash scripts/aliyun-start.sh production
