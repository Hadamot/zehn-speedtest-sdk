#!/usr/bin/env bash
set -euo pipefail

version="$1"
dir=core/build/XCFrameworks/release

./gradlew :rmbt-sdk:assembleRmbtSdkReleaseXCFramework -PsdkVersion="$version"
(cd "$dir" && rm -f RmbtSdk.xcframework.zip && zip -qry RmbtSdk.xcframework.zip RmbtSdk.xcframework)
checksum=$(swift package compute-checksum "$dir/RmbtSdk.xcframework.zip")
sed -i '' \
    -e "s|download/v[^/]*/|download/v$version/|" \
    -e "s|checksum: \"[^\"]*\"|checksum: \"$checksum\"|" \
    Package.swift
echo "$dir/RmbtSdk.xcframework.zip $checksum"
