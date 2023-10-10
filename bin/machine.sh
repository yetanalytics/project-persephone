#!/bin/sh

# TODO: Does not yet support Windows

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=linux;;
    *)          machine=macos;;
esac

echo ${machine}
