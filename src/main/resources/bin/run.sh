#!/bin/bash
set -euo pipefail

# ================== 필수: 서비스 홈 ==================
# 이 값만 인스턴스마다 다르게 두면 다중 실행 가능(폴더 분리)
SERVICE_HOME="${SERVICE_HOME:-/home/ec2-user/capshome/job-system/job-worker-app}"
CONFIG_PATH=${SERVICE_HOME}/config/

# ================== 기본 설정 ==================
PROFILES_ACTIVE="${PROFILES_ACTIVE:-local}"
MAIN_CLASS_NAME="${MAIN_CLASS_NAME:-JobWorkerAppDemoApplication}"

JAR_FILE_NAME="${JAR_FILE_NAME:-job-worker-app-demo-0.0.1-SNAPSHOT.jar}"
JAR_FILE_PATH="$SERVICE_HOME/libs"
PATH_TO_JAR="$JAR_FILE_PATH/$JAR_FILE_NAME"

RUN_DIR="$SERVICE_HOME/run"
PID_FILE="$RUN_DIR/app.pid"

# 표준출력/에러는 버림(요청: 로그 건드리지 않기)
STD_REDIRECT="/dev/null"

# JVM 옵션 (필요 시 JAVA_OPT 환경변수로 추가 가능)
JAVA_OPT="${JAVA_OPT:-}"
JAVA_OPT="$JAVA_OPT -XX:+UseG1GC -XX:NewRatio=2 -XX:SurvivorRatio=6 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=500 -Xms512m -Xmx512m"
JAVA_OPT="$JAVA_OPT -Xlog:gc=debug:file=$SERVICE_HOME/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=100m"
JAVA_OPT="$JAVA_OPT -Dspring.config.location=$CONFIG_PATH"
JAVA_OPT="$JAVA_OPT -Dspring.profiles.active=$PROFILES_ACTIVE"

# ================== 공통 함수 ==================
usage() {
  cat <<EOF
Usage:
  $0 start | stop | restart | status

Notes:
  - 홈 경로 인풋은 없습니다. 오직 SERVICE_HOME 변수만 사용합니다.
  - 구조 예:
      \$SERVICE_HOME/
        ├─ libs/$JAR_FILE_NAME
        └─ run/           # PID 파일 저장 위치
EOF
}

ensure_dirs() { mkdir -p "$RUN_DIR"; }

is_running() {
  if [[ -f "$PID_FILE" ]]; then
    local pid; pid=$(cat "$PID_FILE" || true)
    if [[ -n "${pid:-}" ]] && ps -p "$pid" > /dev/null 2>&1; then
      local cmdline
      cmdline="$(tr '\0' ' ' < /proc/$pid/cmdline 2>/dev/null || ps -o args= -p "$pid" || true)"
      if echo "$cmdline" | grep -q "$PATH_TO_JAR"; then
        return 0
      fi
    fi
  fi
  return 1
}

cleanup_stale_pid() {
  if [[ -f "$PID_FILE" ]]; then
    local pid; pid=$(cat "$PID_FILE" || true)
    if [[ -n "${pid:-}" ]] && ! ps -p "$pid" > /dev/null 2>&1; then
      echo "[$SERVICE_HOME] 이전 PID 파일 정리"
      rm -f "$PID_FILE"
    fi
  fi
}

# ================== 액션 ==================
start() {
  ensure_dirs
  cleanup_stale_pid

  if is_running; then
    echo "[$SERVICE_HOME] 이미 실행 중입니다. (PID=$(cat "$PID_FILE"))"
    return 0
  fi

  if [[ ! -f "$PATH_TO_JAR" ]]; then
    echo "JAR 파일을 찾을 수 없습니다: $PATH_TO_JAR"
    exit 1
  fi

  echo "[$SERVICE_HOME] 시작 중..."
  java $JAVA_OPT -jar "$PATH_TO_JAR" "$MAIN_CLASS_NAME" > "$STD_REDIRECT" 2>&1 &

  local new_pid=$!
  echo "$new_pid" > "$PID_FILE"

  sleep 2
  if is_running; then
    echo "[$SERVICE_HOME] 시작 완료 (PID=$(cat "$PID_FILE"))"
  else
    echo "[$SERVICE_HOME] 시작 확인 실패"
    rm -f "$PID_FILE"
    exit 1
  fi
}

stop() {
  if ! is_running; then
    echo "[$SERVICE_HOME] 실행 중이 아닙니다."
    cleanup_stale_pid
    return 0
  fi

  local pid; pid=$(cat "$PID_FILE")
  echo "[$SERVICE_HOME] 중지 시도(PID=$pid, TERM)..."
  kill "$pid" || true

  for _ in {1..10}; do
    if ! ps -p "$pid" > /dev/null 2>&1; then
      echo "[$SERVICE_HOME] 정상 중지 완료"
      rm -f "$PID_FILE"
      return 0
    fi
    sleep 1
  done

  echo "[$SERVICE_HOME] 강제 종료(KILL)..."
  kill -9 "$pid" || true
  rm -f "$PID_FILE"
  echo "[$SERVICE_HOME] 중지 완료"
}

status() {
  if is_running; then
    local pid; pid=$(cat "$PID_FILE")
    echo "[$SERVICE_HOME] 실행 중 (PID=$pid)"
    ps -o pid,ppid,cmd -p "$pid"
  else
    echo "[$SERVICE_HOME] 실행 중 아님"
    cleanup_stale_pid
  fi
}

restart() { stop; start; }

# ================== 진입점 ==================
ACTION="${1:-}"; [[ -z "$ACTION" ]] && { usage; exit 1; }

case "$ACTION" in
  start)   start   ;;
  stop)    stop    ;;
  restart) restart ;;
  status)  status  ;;
  *) usage; exit 1 ;;
esac