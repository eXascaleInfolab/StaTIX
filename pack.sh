#!/bin/sh
# Back built jar together with requirement into the archive
#
# ./run.sh [<outdir>] [<basedir>]
# basedir  - directory where the executables located

OUTDIR=${1:-.}  # Tarball output directory
BASEDIR=${2:-.}  # Base directory
APP=statix

# Silently create the jar output directory if required
if [ ! -d $OUTDIR ]
then
	echo Creating the output dir \"$OUTDIR\"
	mkdir -p $OUTDIR 2> /dev/null
fi

# Do not create "." dir in the tarball
if [ "$BASEDIR" != "." ]  # Non-empty
then
	BASEDIR="${BASEDIR}/"
else
	BASEDIR=""
fi
tar -czf "$OUTDIR"/${APP}.tar.gz "$BASEDIR"${APP}.jar "$BASEDIR"lib/ "$BASEDIR"run.sh
