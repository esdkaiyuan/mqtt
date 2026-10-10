#!/bin/sh
# Maven 包装：解决 Git Bash 下 classworlds 找不到的路径问题
# 用法: sh scripts/mvnw.sh <maven args...>
set -e
MVN_HOME_WIN='C:/Users/28916/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f'
MVN_HOME_POS='/c/Users/28916/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f'

export MAVEN_HOME="$MVN_HOME_WIN"
export M2_HOME="$MVN_HOME_WIN"
export MAVEN_OPTS="${MAVEN_OPTS:--Xms256m -Xmx1024m}"

cd "$(dirname "$0")/../backend"
exec "$MVN_HOME_POS/bin/mvn.cmd" -Dmaven.multiModuleProjectDirectory="$(pwd -W 2>/dev/null || pwd)" "$@"
