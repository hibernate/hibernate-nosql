#! /bin/bash

goal=
if [ "$RDBMS" == "h2" ] || [ "$RDBMS" == "" ]; then
  # This is the default.
  #   - special check for Jenkins CI jobs where we don't want to run releasePrepare
  if [[ "$CI_SYSTEM" != "jenkins" ]]; then
    goal="releasePrepare"
    # Settings needed for `releasePrepare` execution - for asciidoctor doc rendering
    export GRADLE_OPTS=-Dorg.gradle.jvmargs='-Dlog4j2.disableJmx -Xmx4g -XX:MaxMetaspaceSize=768m -XX:+HeapDumpOnOutOfMemoryError -Duser.language=en -Duser.country=US -Duser.timezone=UTC -Dfile.encoding=UTF-8'
  fi
elif [ "$RDBMS" == "neo4j" ]; then
  goal="-Pdb=neo4j"
elif [ "$RDBMS" == "milvus_2_6" ]; then
  goal="-Pdb=milvus -PdbVersion=2.6"
elif [ "$RDBMS" == "milvus_3_0" ]; then
  goal="-Pdb=milvus -PdbVersion=3.0"
else
  echo "Invalid value for RDBMS: $RDBMS"
  exit 1
fi

function logAndExec() {
  echo 1>&2 "Executing:" "${@}"
  exec "${@}"
}
