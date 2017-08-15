#!/bin/sh
# Build of the StaTIX
# The only optional parameter is the jar output dir
#
# ./build.sh [-p] [<outdir>]
# -p,--pack - build the tarball besides the executables

# Process input options
TARBALL=0
case $1 in
-p|--pack)
	TARBALL=1
	shift  # Shift the arguments
	;;
-*)
	echo "Invalid option specified, usage:"\
		"\n  ./build.sh [-p] [<outdir>=\".\"]"\
		"-p,--pack - build the tarball besides the executables"
	exit 1
	;;
esac

OUTDIR=${1:-.}  # Output directory for the executable package
CLSDIR="$OUTDIR"/classes  # Classes output directory
APP=statix  # App name

# Compile, exit on error
echo Compiling the classes in the \"$CLSDIR\"...
javac -cp lib/\*:src -d "$CLSDIR" src/info/exascale/SimWeighted/*.java
# Manual compilation of the specific class:
# $ javac -cp lib/\*:src -d classes/ src/info/exascale/SimWeighted/main.java
if [ $? -ne 0 ]
then
	echo Build failed, errcode: $?
	exit $?
fi

# Make the jar file ------------------------------------------------------------
echo Building the jar in the \"$OUTDIR\"...
# Note: other jars are not included to this one for the easier substitution of the components
# and to avoid specification of the explicit manifest file (class path)
jar -c -e info.exascale.SimWeighted.main -f "$OUTDIR"/${APP}.jar -C "$CLSDIR" .
if [ $? -ne 0 ]
then
	echo Build failed, errcode: $?
	exit $?
fi
# Remove the compiled classes
echo Removing the \"$CLSDIR\"
rm -rf "$CLSDIR"

# Copy requirements to the output dir
if [ "$OUTDIR" != "." ]
then
	cp -a lib/ run.sh "$OUTDIR"
fi

# Build the tarball ------------------------------------------------------------
if [ $TARBALL -ne 0 ]
then
	echo Building the tarball in the \"$OUTDIR\"...
	./pack.sh "$OUTDIR" "$OUTDIR"
fi
