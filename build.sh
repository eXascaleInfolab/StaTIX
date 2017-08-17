#!/bin/sh
# Build of the StaTIX
# The only optional parameter is the jar output dir

USAGE="$0 [-p] [-c] [<outdir>]
  -p,--pack - build the tarball besides the executables
  -c,--classes  - retain classes after the build, useful for the frequent
    modification and recompilation of some files.
    
    Compilation or the single file (.java to .class):
    $ javac -cp lib/\*:src -d classes/ src/info/exascale/SimWeighted/main.java
"
# Extract the leading "-" if any:  ${1%%[^-]*}
# Process input options
TARBALL=0  # Make tarball
DELCLS=1  # Delete the classes after the jar building

while [ $1 ]
do
	case $1 in
	-p|--pack)
		TARBALL=1
		shift  # Shift the arguments
		;;
	-c|--classes)
		DELCLS=0
		shift
		;;
	-*)
		printf "Error: Invalid option specified.\n\n$USAGE"
		exit 1
		;;
	*)
		# Check that only one output directory is specified
		if [ -n "${2}"  ]
		then
			printf "Error: Too many parameters specified.\n\n$USAGE"
			exit 1
		fi
		break
		;;
	esac
done

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
if [ $DELCLS -ne 0 ]
then
	echo Removing the \"$CLSDIR\"
	rm -rf "$CLSDIR"
fi

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
