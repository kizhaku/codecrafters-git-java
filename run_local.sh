#!/bin/bash

set -e

execDir="/tmp/testing/git"
scriptPath="/Users/arunk/Documents/Git/codecrafters-git-java/your_program.sh"

# Run locally in /tmp/testing/git folder to prevent override of repo .git
if [ ! -d "$execDir" ]; then
   mkdir -p "$execDir"
fi

cd "$execDir"
"$scriptPath" "$@"



