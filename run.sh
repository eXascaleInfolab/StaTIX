#!/bin/sh
# Execution of the StaTIX

#LD_LIBRARY_PATH=. java -cp daoc.jar:. CosinSim.main ../datasets/museum.rdf ../datasets/museum_s0.25.rdf
LD_LIBRARY_PATH=. java -cp lib/\*:bin info.exascale.SimWeighted.main $@
