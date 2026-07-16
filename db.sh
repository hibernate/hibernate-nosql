#! /bin/bash

###############################################################################
# Detect container cli (Docker/Podman)
# Docker has priority to make CI builds more stable/predictable
# (Jenkins is currently better configured to deal with Docker)
###############################################################################
if command -v docker > /dev/null; then
  CONTAINER_CLI=$(command -v docker)
  if [[ "$(docker version | grep Podman)" == "" ]]; then
    IS_DOCKER_RUNTIME=true
    IS_PODMAN=false
  else
    IS_DOCKER_RUNTIME=false
    IS_PODMAN=true
  fi
elif command -v podman > /dev/null; then
  CONTAINER_CLI=$(command -v podman)
  IS_DOCKER_RUNTIME=false
  IS_PODMAN=true
else
  echo "ERROR: Neither docker nor podman found on PATH"
  exit 1
fi

# Set runtime-specific defaults
if [[ "$IS_DOCKER_RUNTIME" == "true" ]]; then
  HEALTHCHECK_PATH="{{.State.Health.Status}}"
  PRIVILEGED_CLI=""
elif [[ "$IS_PODMAN" == "true" ]]; then
  HEALTHCHECK_PATH="{{.State.Healthcheck.Status}}"
  if command -v sudo > /dev/null; then
    PRIVILEGED_CLI="sudo"
  else
    PRIVILEGED_CLI=""
  fi
fi

# Detect --wait support for compose up (podman-compose doesn't support it)
if $CONTAINER_CLI compose up --help 2>&1 | grep -q -- '--wait'; then
    COMPOSE_WAIT="--wait"
else
    COMPOSE_WAIT=""
fi

DB_COUNT=1
if [[ "$(uname -s)" == "Darwin" ]]; then
  IS_OSX=true
  DB_COUNT=$(sysctl -n hw.physicalcpu)
  # PostGIS images only support amd64, so we force emulation on macOS
  export POSTGRESQL_PLATFORM="linux/amd64"
else
  IS_OSX=false
  DB_COUNT=$(nproc)
fi

if [[ $DB_COUNT -ge 16 ]]; then
  DB_COUNT=$(($DB_COUNT/2))
#elif [[ $DB_COUNT -le 4 ]]; then
  # Only start halving the
#else
#  DB_COUNT=$(($DB_COUNT/2))
fi

###############################################################################
# Helper functions to start/stop a database using compose files
###############################################################################
COMPOSE_PROJECT="hibernate_orm"

compose_up() {
    local compose_file="docker-compose/$1"
    local max_retries="${2:-120}"
    local extra_files=""

    if [[ "$IS_OSX" == "true" ]]; then
        local osx_override="$(dirname "$compose_file")/osx-override.yml"
        if [[ -f "$osx_override" ]]; then
            extra_files="-f $osx_override"
        fi
    fi

    echo "Starting database using $compose_file"

    $CONTAINER_CLI compose -p "$COMPOSE_PROJECT" -f "$compose_file" $extra_files up -d $COMPOSE_WAIT $REMOVE_ORPHANS || {
        echo "Error: Docker compose failed to start."
        exit 1
    }

    # Docker would already wait for a healthy container but with podman there may be issues
    #  in that case we just run an extra check to be caution:
    compose_wait "$max_retries" -p "$COMPOSE_PROJECT" -f "$compose_file"
}

compose_down() {
  if [[ -n "$REMOVE_ORPHANS" ]]; then
    for project in $($CONTAINER_CLI compose ls -q 2>/dev/null | grep "^${COMPOSE_PROJECT}"); do
        $CONTAINER_CLI compose -p "$project" down -v 2>/dev/null || true
    done
    if [[ -n "$1" ]]; then
        $CONTAINER_CLI rm -f "$1" 2>/dev/null || true
    fi
  else
    echo 'INFO: Not stopping any previously started containers. To stop them run db.sh without passing -k to it.'
  fi
}

compose_wait() {
    local max_retries=${1:-120}
    shift
    echo "Waiting for service(s) to become healthy (retries: $max_retries, interval: 5s)..."
    local containers
    containers=$($CONTAINER_CLI compose "$@" ps -q)
    for container in $containers; do
        local retries=0
        local health=""
        while [[ $retries -lt $max_retries ]]; do
            health=$($CONTAINER_CLI inspect --format "$HEALTHCHECK_PATH" "$container" 2>/dev/null || true)
            if [[ "$health" == "healthy" ]]; then
                break
            fi
            if [[ -z "$health" ]]; then
                break
            fi
            sleep 5
            retries=$((retries + 1))
        done
        if [[ -n "$health" && "$health" != "healthy" ]]; then
            echo "Error: Container $container did not become healthy after $((max_retries * 5))s"
            exit 1
        else
          echo "Info: Container $container became healthy and should be ready to use"
        fi
    done
}

###############################################################################

milvus() {
  milvus_3_0
}

milvus_3_0() {
    compose_down "milvus"
    compose_up "latest/milvus/docker-compose.yaml"
}

milvus_2_6() {
    compose_down "milvus"
    compose_up "versioned/milvus-2.6/docker-compose.yaml"
}

###############################################################################

neo4j() {
  neo4j_2026_6
}

neo4j_2026_6() {
    compose_down "neo4j"
    compose_up "latest/neo4j/docker-compose.yaml"
}

neo4j_5_26() {
    compose_down "neo4j"
    compose_up "versioned/neo4j-5.26/docker-compose.yaml"
}

###############################################################################
# Script args handling:
###############################################################################
REMOVE_ORPHANS="--remove-orphans"
while [[ "${1:-}" == -* ]]; do
    case "$1" in
        -k|--keep-orphans)
            REMOVE_ORPHANS=""
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [ -z ${1} ]; then
    echo "No db name provided"
    echo "Provide one of:"
    echo -e "\tmilvus"
    echo -e "\tmilvus_3_0"
    echo -e "\tmilvus_2_6"
    echo -e "\tneo4j"
    echo -e "\tneo4j_2026_6"
    echo -e "\tneo4j_5_26"
else
    ${1}
fi
