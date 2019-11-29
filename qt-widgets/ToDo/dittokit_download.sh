#!/bin/bash

set -euxo pipefail

PROJECT_ROOT_DIR=`git rev-parse --show-toplevel`

DITTOKIT_VERSION=0.1.0
echo "Downloading DittoKit version: $DITTOKIT_VERSION"

DITTOKIT_FILENAME=DittoKit.tar.gz

rm -f $PROJECT_ROOT_DIR/qt-widgets/ToDo/DittoKit.tar.gz
rm -f $PROJECT_ROOT_DIR/qt-widgets/ToDo/libditto.a
rm -f $PROJECT_ROOT_DIR/qt-widgets/ToDo/DittoKit.h

wget "https://software.ditto.live/cpp-ios/DittoKit/$DITTOKIT_VERSION/dist/$DITTOKIT_FILENAME" -P $PROJECT_ROOT_DIR/qt-widgets/ToDo
tar xvfz $PROJECT_ROOT_DIR/qt-widgets/ToDo/$DITTOKIT_FILENAME -C $PROJECT_ROOT_DIR/qt-widgets/ToDo

echo "Successfully downloaded DittoKit version: $DITTOKIT_VERSION"
