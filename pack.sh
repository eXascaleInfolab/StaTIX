#!/bin/sh
# Back built jar together with requirement into the archive
#
# ./build.sh [<outdir>]

APP=statix

tar -czf ${APP}.tar.gz ${APP}.jar lib/ run.sh
