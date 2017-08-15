#!/bin/sh
# Execution of the StaTIX

# Old manual execution
#LD_LIBRARY_PATH=. java -cp daoc.jar:. CosinSim.main ../datasets/museum.rdf ../datasets/museum_s0.25.rdf

# Execution of the classes
LD_LIBRARY_PATH=lib:. java -cp lib/\*:classes info.exascale.SimWeighted.main $@

## Execution of the jar (TOFIX)
# Note: execution from the jar causes some issues with the Common CLI linking
#LD_LIBRARY_PATH=lib:. java -cp lib/\*:./\*:. -jar statix.jar $@
