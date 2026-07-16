#! /bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

if [ "$RDBMS" == 'neo4j' ]; then
  bash $DIR/../db.sh neo4j
elif [ "$RDBMS" == 'milvus_2_6' ]; then
  bash $DIR/../db.sh milvus_2_6
elif [ "$RDBMS" == 'milvus_3_0' ]; then
  bash $DIR/../db.sh milvus_3_0
fi