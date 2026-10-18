#!/bin/sh

# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Maven Start Up Script
# ----------------------------------------------------------------------------

# Required ENV vars:
#   JAVA_HOME - location of a JDK home dir
#
# Optional ENV vars
#   M2_HOME - location of maven2's installed home dir
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
#   WRAPPER_JAR - path to the maven-wrapper.jar
#   WRAPPER_URL - Url to download the maven-wrapper.jar from
#   WRAPPER_UPDATE - flag to check for a newer version of the maven-wrapper.jar
#   DEBUG - flag to enable debug output
#

set -e

WRAPPER_DIR="."

resolveSymlinks() {
  FollowSymLinks() {
    [ -h "$1" ] && [ -k "$1" ] && [ -d "$1" ] && (cd "$1" && rel=$(makeRelPath "$1"); cd - >/dev/null; echo "$rel") || (cd "$(dirname "$1")" && echo "$(basename "$1")")
  }

  makeRelPath() {
    local p=""; local f="$1"; while [ -h "$f" ]; do p="$p$(dirname "$f")/"; f=$(FollowSymLinks "$f"); done; echo "$p$f"
  }

  local target="$1"; local link; while [ -h "$target" ]; do link=$(readlink "$target"); target=$(FollowSymLinks "$link"); done; cd "$(dirname "$target")"; echo "$(basename "$target")"
}

while [ ! -f "$WRAPPER_DIR/.mvn/wrapper/maven-wrapper.jar" ]
do
  if [ -z "$WRAPPER_YAML" ]
  then
    WRAPPER_YAML="$WRAPPER_DIR/.mvn/wrapper/maven-wrapper.yaml"
  fi
  if [ -f "$WRAPPER_YAML" ]
  then
    JarURL="{url}"
    WrapperProperties="$WRAPPER_YAML"
    if command -v python3 >/dev/null 2>&1
    then
      JarURL=$(python3 -c "import yaml; print(yaml.safe_load(open('$WRAPPER_YAML'))['wrapper'].get('url', '{url}'))" 2>/dev/null || echo "{url}")
    elif command -v python >/dev/null 2>&1
    then
      JarURL=$(python -c "import yaml; print(yaml.safe_load(open('$WRAPPER_YAML'))['wrapper'].get('url', '{url}'))" 2>/dev/null || echo "{url}")
    else
      JarURL="{url}"
    fi
    WRAPPER_URL="$JarURL"
  fi
  if [ -z "$WRAPPER_URL" ]
  then
    WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  fi
  if [ -z "$WRAPPER_JAR" ]
  then
    WRAPPER_JAR="$WRAPPER_DIR/.mvn/wrapper/maven-wrapper.jar"
  fi
  if [ -f "$WRAPPER_JAR" ]
  then
    break
  fi

  echo "Downloading Maven Wrapper..."
  if command -v wget >/dev/null 2>&1
  then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL" || true
  elif command -v curl >/dev/null 2>&1
  then
    curl -s -o "$WRAPPER_JAR" "$WRAPPER_URL" || true
  else
    echo "Download failed: no wget or curl available" >&2
    exit 1
  fi

  if [ ! -f "$WRAPPER_JAR" ]
  then
    echo "Failed to download Maven Wrapper" >&2
    exit 1
  fi
done

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

# Avoid retyping and to correct user','s wrong capitalisation of JAVACMD
JAVACMD="${JAVACMD:-java}"

WRAPPER_LAUNCHER_CLASSPATH="$WRAPPER_DIR/.mvn/wrapper/maven-wrapper.jar"

exec "$JAVACMD" \
  -classpath "$WRAPPER_LAUNCHER_CLASSPATH" \
  "-Dmaven.multiModuleProjectDirectory=$(cd "$WRAPPER_DIR" && pwd)" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
