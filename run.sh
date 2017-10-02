#!/bin/sh
# Execution of the StaTIX
#
# ./run.sh [APPOPTS...]

# Old manual execution
#LD_LIBRARY_PATH=. java -cp daoc.jar:. CosinSim.main ../datasets/museum.rdf ../datasets/museum_s0.25.rdf
#LD_LIBRARY_PATH=lib java -cp lib/\*:classes info.exascale.statix.main -f -g  ../../datasets/input/kenza-based/acsds_s0.25/museum_s0.25.rdf -o  ../../tests/results/museum_gs0.25.cnl -n  ../../tests/results/museum.idm  ../../datasets/input/kenza-based/museum.rdf

## Execution of the classes
#LD_LIBRARY_PATH=${BASEDIR}/lib java -cp ${BASEDIR}/lib/\*:${BASEDIR}/classes info.exascale.statix.main $@

# Execution of the jar
# Note: execution from the jar causes some issues with the Common CLI linking
# Note: -ea enables asserts, -da - disables (default)
#LD_LIBRARY_PATH=lib java -ea:info.exascale.statix... -cp lib/\*:./\* info.exascale.statix.main $@
LD_LIBRARY_PATH=lib java -cp lib/\*:./\* info.exascale.statix.main $@
