#!/bin/sh
# Build of the StaTIX
# The only optional parameter is the jar output dir
#
# ./build.sh [<outdir>]

CLSDIR=classes  # Classes output directory
OUTDIR=${1:-.}  # Jar output directory
APP=statix  # App name

# Compile, exit on error
echo Compiling the classes in the \"$CLSDIR\"...
javac -cp lib/\*:src -d $CLSDIR src/info/exascale/SimWeighted/*.java
# Manual compilation of the specific class:
# $ javac -cp lib/\*:src -d classes/ src/info/exascale/SimWeighted/main.java
if [ $? -ne 0 ]
then
	exit $?
fi

# Silently create the jar output directory if required
if [ ! -d $OUTDIR ]
then
	echo Creating the target dir
	mkdir -p $OUTDIR 2> /dev/null
fi

### Make the jarfile
#echo Building the jar in the \"$OUTDIR\"...
#jar -c -e info.exascale.SimWeighted.main -f ${OUTDIR}/${APP}.jar -C $CLSDIR .

echo Building the tarball in the \"$OUTDIR\"...
tar -czf ${APP}.tar.gz ${APP}.jar lib/ run.sh
