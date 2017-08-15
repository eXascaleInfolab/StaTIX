#!/bin/sh
# Execution of the StaTIX
#
# ./run.sh [APPOPTS...]

# Old manual execution
#LD_LIBRARY_PATH=. java -cp daoc.jar:. CosinSim.main ../datasets/museum.rdf ../datasets/museum_s0.25.rdf

## Execution of the classes
#LD_LIBRARY_PATH=${BASEDIR}/lib java -cp ${BASEDIR}/lib/\*:${BASEDIR}/classes info.exascale.SimWeighted.main $@

# Execution of the jar
# Note: execution from the jar causes some issues with the Common CLI linking
LD_LIBRARY_PATH=lib java -cp lib/\*:./\* info.exascale.SimWeighted.main $@
