#!/bin/sh
# Build of the StaTIX
# The only optional parameter is the jar output dir
#
# ./build.sh [<jardir>]

CLSDIR=classes  # Classes output directory
JARDIR=${1:-.}  # Jar output directory

# Compile, exit on error
echo Compiling the classes in the \"$CLSDIR\"...
javac -cp lib/\*:src -d $CLSDIR src/info/exascale/SimWeighted/*.java
if [ $? -ne 0 ]
then
	exit $?
fi

# Silently create the jar output directory if required
if [ ! -d $JARDIR ]
then
	echo Creating the target dir
	mkdir -p $JARDIR 2> /dev/null
fi

## Make the jarfile
echo Building the jar in the \"$JARDIR\"...
jar -c -e info.exascale.SimWeighted.main.class -f ${JARDIR}/statix.jar -C $CLSDIR .
