#!/bin/bash

# Increment a version string using Semantic Versioning (SemVer) terminology.

# Parse command line options.

scheme=$1
version=$2

# Build array from version string.

a=( ${version//./ } )

# If the scheme does not match major or minor or match, show usage message.
allowed_schemes=(major minor patch)
case "${allowed_schemes[@]}" in
    *"$scheme"*)
        ;;
    *)
        echo "usage: $(basename $0) <major|minor|patch> major.minor.patch"
        exit 1
        ;;
esac

# If version string is missing or has the wrong number of members, show usage message.

if [ ${#a[@]} -ne 3 ]
then
  echo "usage: $(basename $0) <major|minor|patch> major.minor.patch"
  exit 1
fi

# Increment version numbers as requested.

if [ "major" == "$scheme" ]
then
  ((a[0]++))
  a[1]=0
  a[2]=0
fi

if [ "minor" == "$scheme" ]
then
  ((a[1]++))
  a[2]=0
fi

if [ "patch" == "$scheme" ]
then
  ((a[2]++))
fi

echo "${a[0]}.${a[1]}.${a[2]}"


# Last pulled: Sat 26 Oct 2019 22:41:05 UTC
