#!/usr/bin/env bash
set -euo pipefail

KNOWN_SERVICES=(
  gateway-service
  user-service
  auth-service
  cat-service
  storage-service
  notification-service
  adoption-service
  form-analysis-service
  organization-service
  chat-service
  schedule-service
)

declare -A SERVICE_PORTS=(
  [gateway-service]=8080
  [user-service]=8081
  [auth-service]=8082
  [storage-service]=8083
  [cat-service]=8084
  [notification-service]=8085
  [adoption-service]=8086
  [form-analysis-service]=8087
  [organization-service]=8088
  [chat-service]=8089
  [schedule-service]=8090
)

LOG_DIR="logs"
RUN_E2E=false
declare -A SERVICE_PIDS=()
declare -A SVC_STATE=()

if [[ -t 1 ]]; then
  IS_TTY=true
  C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else
  IS_TTY=false
  C_GREEN=; C_RED=; C_DIM=; C_RST=
fi

cleanup() {
  echo ""
  echo "Stopping services..."
  for pid in "${SERVICE_PIDS[@]}"; do
    kill -TERM "$pid" 2>/dev/null || true
  done
  for pid in "${SERVICE_PIDS[@]}"; do
    wait "$pid" 2>/dev/null || true
  done
  echo "All services stopped."
  exit 0
}

trap cleanup SIGINT SIGTERM

is_known_service() {
  local svc="$1"
  for known in "${KNOWN_SERVICES[@]}"; do
    [[ "$known" == "$svc" ]] && return 0
  done
  return 1
}

is_up() {
  local port="$1" code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:$port/q/health/live" 2>/dev/null)
  [[ "$code" == "200" ]]
}

is_tracked() {
  local svc="$1"
  for s in "${SERVICES[@]}"; do
    [[ "$s" == "$svc" ]] && return 0
  done
  return 1
}

start_service() {
  local svc="$1"
  mvn compile quarkus:dev -pl "$svc" -am < /dev/null > "$LOG_DIR/$svc.log" 2>&1 &
  SERVICE_PIDS[$svc]=$!
  is_tracked "$svc" || SERVICES+=("$svc")
}

stop_service() {
  local svc="$1" pid="${SERVICE_PIDS[$svc]:-}"
  [[ -z "$pid" ]] && return 1
  kill -TERM "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  unset 'SERVICE_PIDS[$svc]'
  SVC_STATE[$svc]=stopped
  return 0
}

restart_service() {
  local svc="$1"
  stop_service "$svc" || true
  start_service "$svc"
  echo "  $svc relanzado (PID ${SERVICE_PIDS[$svc]}). Log: $LOG_DIR/$svc.log"
}

draw_status() {
  local nlines=$(( ${#SERVICES[@]} + 1 ))
  if [[ "$IS_TTY" == true && "${STATUS_DRAWN:-false}" == true ]]; then
    printf '\033[%dA' "$nlines"
  fi
  STATUS_DRAWN=true
  printf '\033[KEstado de servicios (logs en %s/<servicio>.log):\n' "$LOG_DIR"
  for svc in "${SERVICES[@]}"; do
    local port="${SERVICE_PORTS[$svc]}" sym note
    case "${SVC_STATE[$svc]}" in
      up)   sym="${C_GREEN}✓${C_RST}"; note="${C_DIM}listo${C_RST}" ;;
      dead) sym="${C_RED}✗${C_RST}"; note="${C_RED}caído — revisa $LOG_DIR/$svc.log${C_RST}" ;;
      *)    sym="${C_DIM}…${C_RST}"; note="${C_DIM}arrancando${C_RST}" ;;
    esac
    printf '\033[K  %b %-24s :%s  %b\n' "$sym" "$svc" "$port" "$note"
  done
}

monitor() {
  local total=${#SERVICES[@]} elapsed=0 interval=2 timeout=600
  local up=0 dead=0
  while true; do
    up=0; dead=0
    for svc in "${SERVICES[@]}"; do
      if is_up "${SERVICE_PORTS[$svc]}"; then
        SVC_STATE[$svc]=up; up=$((up + 1))
      elif ! kill -0 "${SERVICE_PIDS[$svc]}" 2>/dev/null; then
        SVC_STATE[$svc]=dead; dead=$((dead + 1))
      else
        SVC_STATE[$svc]=booting
      fi
    done
    draw_status
    [[ $((up + dead)) -eq $total ]] && break
    [[ $elapsed -ge $timeout ]] && break
    sleep "$interval"; elapsed=$((elapsed + interval))
  done
  if [[ "$dead" -gt 0 ]]; then
    echo "" >&2
    echo "$dead servicio(s) no arrancaron — revisa los logs marcados arriba." >&2
  elif [[ $((up)) -lt $total ]]; then
    echo "" >&2
    echo "Tiempo de espera agotado; algún servicio sigue arrancando." >&2
  fi
}

refresh_state() {
  for svc in "${SERVICES[@]}"; do
    local pid="${SERVICE_PIDS[$svc]:-}"
    if [[ -z "$pid" ]]; then
      SVC_STATE[$svc]=stopped
    elif is_up "${SERVICE_PORTS[$svc]}"; then
      SVC_STATE[$svc]=up
    elif ! kill -0 "$pid" 2>/dev/null; then
      SVC_STATE[$svc]=dead
    else
      SVC_STATE[$svc]=booting
    fi
  done
}

status_cmd() {
  STATUS_DRAWN=false
  refresh_state
  draw_status
}

require_svc() {
  local svc="$1"
  if [[ -z "$svc" ]]; then
    echo "  Falta el nombre del servicio."
    return 1
  fi
  if ! is_known_service "$svc"; then
    echo "  Servicio desconocido: $svc"
    echo "  Conocidos: ${KNOWN_SERVICES[*]}"
    return 1
  fi
  return 0
}

tail_logs() {
  local svc="$1" f="$LOG_DIR/$svc.log"
  if [[ ! -f "$f" ]]; then
    echo "  No hay log para $svc."
    return 0
  fi
  echo "  tail -f $f  (Ctrl-C vuelve al prompt)"
  trap ':' SIGINT
  tail -f "$f" || true
  trap cleanup SIGINT
}

print_help() {
  cat <<EOF
Comandos:
  rrun|restart <svc>   relanza un servicio ('all' para todos)
  start <svc>          arranca un servicio parado
  stop <svc>           para un servicio
  logs|tail <svc>      sigue su log en vivo (Ctrl-C vuelve al prompt)
  status               redibuja el panel de estado
  help                 muestra esta ayuda
  quit|exit            para todo y sale
EOF
}

repl() {
  local line cmd arg
  local -a parts
  print_help
  while true; do
    if ! read -e -r -p "kitties> " line; then
      echo ""
      cleanup
    fi
    [[ -z "${line//[[:space:]]/}" ]] && continue
    read -ra parts <<< "$line"
    cmd="${parts[0]:-}"
    arg="${parts[1]:-}"
    case "$cmd" in
      rrun|restart)
        if [[ "$arg" == "all" ]]; then
          for svc in "${SERVICES[@]}"; do restart_service "$svc"; done
        elif require_svc "$arg"; then
          restart_service "$arg"
        fi
        ;;
      start)
        if require_svc "$arg"; then
          if [[ -n "${SERVICE_PIDS[$arg]:-}" ]]; then
            echo "  $arg ya está corriendo (PID ${SERVICE_PIDS[$arg]})."
          else
            start_service "$arg"
            echo "  $arg arrancado (PID ${SERVICE_PIDS[$arg]}). Log: $LOG_DIR/$arg.log"
          fi
        fi
        ;;
      stop)
        if require_svc "$arg"; then
          if stop_service "$arg"; then
            echo "  $arg parado."
          else
            echo "  $arg no estaba corriendo."
          fi
        fi
        ;;
      logs|tail)
        require_svc "$arg" && tail_logs "$arg"
        ;;
      status)
        status_cmd
        ;;
      help|h|'?')
        print_help
        ;;
      quit|exit|q)
        cleanup
        ;;
      *)
        echo "  Comando desconocido: $cmd (escribe 'help')"
        ;;
    esac
  done
}

wait_for_stack() {
  local total=${#SERVICES[@]}
  echo "Waiting for all $total services to be ready..."
  for i in $(seq 1 60); do
    local up=0
    for svc in "${SERVICES[@]}"; do
      is_up "${SERVICE_PORTS[$svc]}" && up=$((up + 1))
    done
    echo "  [$i/60] $up/$total ready"
    [[ "$up" -eq "$total" ]] && return 0
    sleep 10
  done
  echo "Timed out waiting for stack." >&2
  return 1
}

# Parse arguments: optional --e2e flag and optional service list
SERVICE_ARG=""
for arg in "$@"; do
  if [[ "$arg" == "--e2e" ]]; then
    RUN_E2E=true
  else
    SERVICE_ARG="$arg"
  fi
done

if [[ -z "$SERVICE_ARG" ]]; then
  SERVICES=("${KNOWN_SERVICES[@]}")
else
  IFS=',' read -ra REQUESTED <<< "$SERVICE_ARG"

  for svc in "${REQUESTED[@]}"; do
    if ! is_known_service "$svc"; then
      echo "Error: unknown service '$svc'." >&2
      echo "Known services: ${KNOWN_SERVICES[*]}" >&2
      exit 1
    fi
  done

  SERVICES=(gateway-service)
  for svc in "${REQUESTED[@]}"; do
    [[ "$svc" != "gateway-service" ]] && SERVICES+=("$svc")
  done
fi

mkdir -p "$LOG_DIR"

# Compile the whole reactor once, with bounded parallelism, before launching
# any dev process. This warms target/ for every module so the parallel
# `quarkus:dev -am` launches find everything up to date instead of all
# recompiling the shared modules at once (the source of the startup contention).
svc_csv=$(IFS=,; echo "${SERVICES[*]}")
echo "Compilando módulos una sola vez (evita la contención al arrancar): ${SERVICES[*]}"
if ! mvn -T1C -pl "$svc_csv" -am compile; then
  echo "La compilación falló — no se arrancó ningún servicio. Revisa la salida de Maven." >&2
  exit 1
fi
echo "Compilación lista."
echo ""

echo "Arrancando ${#SERVICES[@]} servicio(s). Logs en $LOG_DIR/<servicio>.log"
for svc in "${SERVICES[@]}"; do
  start_service "$svc"
done

if $RUN_E2E; then
  wait_for_stack
  echo ""
  echo "Running e2e tests..."
  mvn test -Pe2e -pl e2e-tests
  E2E_EXIT=$?
  cleanup
  exit $E2E_EXIT
fi

monitor
echo ""
if [[ -t 0 ]]; then
  repl
else
  echo "stdin no interactivo: sin consola de comandos."
  echo "Para seguir un servicio en vivo:  tail -f $LOG_DIR/<servicio>.log"
  echo "Ctrl-C para detener todo."
  wait
fi
