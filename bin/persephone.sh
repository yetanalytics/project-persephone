#!/bin/sh

MACHINE=`bin/machine.sh`

runtimes/$MACHINE/bin/java -server -jar cli.jar $@
