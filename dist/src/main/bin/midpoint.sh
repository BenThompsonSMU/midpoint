#!/bin/bash
# Portions Copyright (C) 2017-2020 Evolveum and contributors
#
# This work is dual-licensed under the Apache License 2.0
# and European Union Public License. See LICENSE file for details.

set -eu

USE_NOHUP="true"

if [[ -z ${1:-} ]]; then
  echo "Usage: midpoint.sh start|stop [other args...]"
  exit 1
fi

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
BASE_DIR=$(cd "${SCRIPT_DIR}/.." && pwd -P)

# setting default to MIDPOINT_HOME if not set
: "${MIDPOINT_HOME:="${BASE_DIR}/var"}"

mkdir -p "${MIDPOINT_HOME}/log"

JAVA_OPTS="${JAVA_OPTS:-}
-Xms2048M
-Xmx4096M
-Dpython.cachedir=${MIDPOINT_HOME}/tmp
-Djavax.net.ssl.trustStore=${MIDPOINT_HOME}/keystore.jceks
-Djavax.net.ssl.trustStoreType=jceks
-Dmidpoint.home=${MIDPOINT_HOME}"

# apply setenv.sh if it exists. This can be used for -Dmidpoint.nodeId etc.
# the script can either append or overwrite JAVA_OPTS
if [ -r "$SCRIPT_DIR/setenv.sh" ]; then
  echo "Applying setenv.sh from $SCRIPT_DIR directory."
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/setenv.sh"
fi

: "${BOOT_OUT:="${MIDPOINT_HOME}/log/midpoint.out"}"
: "${PID_FILE:="${MIDPOINT_HOME}/log/midpoint.pid"}"

if [ ! -f "${BASE_DIR}/lib/midpoint.war" ]; then
  echo "ERROR: midpoint.war is not in /lib directory"
  exit 1
fi

: "${LOGGING_MANAGER:="-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"}"

# Set UMASK unless it has been overridden
: "${UMASK:="0027"}"
umask $UMASK

if [[ ! -v JAVA_HOME ]]; then
  _RUNJAVA=java
else
  _RUNJAVA="${JAVA_HOME}/bin/java"
fi

if [ "$USE_NOHUP" = "true" ]; then
  _NOHUP="nohup"
fi

# ----- Execute The Requested Command -----------------------------------------

if [ "$1" = "start" ]; then
  if ! which "$_RUNJAVA" &>/dev/null; then
    echo "$_RUNJAVA not found (or not executable). Start aborted."
    exit 1
  fi
  if [[ -n "$PID_FILE" ]]; then
    if [ -f "$PID_FILE" ]; then
      if [ -s "$PID_FILE" ]; then
        echo "Existing PID file found during start."
        if [ -r "$PID_FILE" ]; then
          PID=$(cat "$PID_FILE")
          if ps -p "$PID" >/dev/null 2>&1; then
            echo "Midpoint appears to still be running with PID $PID. Start aborted."
            echo "If the following process is not a midpoint process, remove the PID file and try again:"
            ps -f -p "$PID"
            exit 1
          else
            echo "Removing/clearing stale PID file."
            rm -f "$PID_FILE" >/dev/null 2>&1 || {
              if [ -w "$PID_FILE" ]; then
                cat /dev/null >"$PID_FILE"
              else
                echo "Unable to remove or clear stale PID file. Start aborted."
                exit 1
              fi
            }
          fi
        else
          echo "Unable to read PID file. Start aborted."
          exit 1
        fi
      else
        rm -f "$PID_FILE" >/dev/null 2>&1 || {
          if [ ! -w "$PID_FILE" ]; then
            echo "Unable to remove or write to empty PID file. Start aborted."
            exit 1
          fi
        }
      fi
    fi
  fi

  shift
  touch "$BOOT_OUT"

  echo "Starting midPoint..."
  echo "MIDPOINT_HOME=$MIDPOINT_HOME"

  # shellcheck disable=SC2086
  eval $_NOHUP "\"$_RUNJAVA\"" $LOGGING_MANAGER $JAVA_OPTS \
    -jar "${BASE_DIR}/lib/midpoint.war" "$@" \
    "&" >>"$BOOT_OUT" 2>&1
  # using loader.path produces strange failures on schema validation during start on closed networks
  #  eval $_NOHUP "\"$_RUNJAVA\"" \
  #    $LOGGING_MANAGER $JAVA_OPTS \
  #    -cp "${BASE_DIR}/lib/midpoint.war" \
  #    -Dloader.path=WEB-INF/classes,WEB-INF/lib,WEB-INF/lib-provided,$MIDPOINT_HOME/lib/ \
  #    org.springframework.boot.loader.PropertiesLauncher \
  #    "$@" \
  #    "&" >>"$BOOT_OUT" 2>&1

  if [[ -n "$PID_FILE" ]]; then
    echo $! >"$PID_FILE"
  fi

elif [ "$1" = "stop" ]; then

  shift

  FORCE=0
  if [ "${1:-}" = "-force" ]; then
    shift
    FORCE=1
  fi

  if [ -f "$PID_FILE" ]; then
    if [ -s "$PID_FILE" ]; then
      PID=$(cat "$PID_FILE")
      kill -0 "$PID" >/dev/null 2>&1 || {
        echo "PID file found but no matching process was found. Stop aborted."
        exit 1
      }
    else
      echo "PID file '${PID_FILE}' is empty?"
      exit 1
    fi
  else
    echo "PID file '${PID_FILE}' does not exist. MidPoint is probably stopped."
    exit 1
  fi

  echo "Stopping midPoint"
  kill -TERM "$PID" >/dev/null 2>&1

  SLEEP=10
  if [ -f "$PID_FILE" ]; then
    while [ $SLEEP -ge 0 ]; do
      kill -0 "$PID" >/dev/null 2>&1 || {
        rm -f "$PID_FILE" >/dev/null 2>&1 || {
          if [ -w "$PID_FILE" ]; then
            cat /dev/null >"$PID_FILE"
            # If MidPoint has stopped don't try and force a stop with an empty PID file
            FORCE=0
          else
            echo "The PID file could not be removed or cleared."
          fi
        }
        echo "Midpoint stopped."
        break
      }
      if [ $SLEEP -gt 0 ]; then
        sleep 1
      fi

      if [ $SLEEP -eq 0 ]; then
        echo "Midpoint did not stop in time."
        if [ $FORCE -eq 0 ]; then
          echo "PID file was not removed."
        fi
        echo "To aid diagnostics a thread dump has been written to standard out."
        kill -3 "$PID"
      fi
      SLEEP=$((SLEEP - 1))
    done
  fi

  KILL_SLEEP_INTERVAL=5
  if [ $FORCE -eq 1 ]; then
    if [ -f "$PID_FILE" ]; then
      echo "Killing midPoint with the PID: $PID"
      kill -9 "$PID"
      while [ $KILL_SLEEP_INTERVAL -ge 0 ]; do
        kill -0 "$PID" >/dev/null 2>&1 || {
          rm -f "$PID_FILE" >/dev/null 2>&1 || {
            if [ -w "$PID_FILE" ]; then
              cat /dev/null >"$PID_FILE"
            else
              echo "The PID file could not be removed."
            fi
          }
          echo "The MidPoint process has been killed."
          break
        }
        if [[ $KILL_SLEEP_INTERVAL -gt 0 ]]; then
          sleep 1
        fi
        KILL_SLEEP_INTERVAL=$((KILL_SLEEP_INTERVAL - 1))
      done
      if [ $KILL_SLEEP_INTERVAL -lt 0 ]; then
        echo "MidPoint has not been killed completely yet. The process might be waiting on some system call or might be UNINTERRUPTIBLE."
      fi
    fi
  fi
fi
