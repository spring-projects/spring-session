#!/bin/bash

VERSION=$1
until http -h --check-status --ignore-stdin https://repo1.maven.org/maven2/org/springframework/session/spring-session-core/$VERSION/; do sleep 10; clear; done; spd-say "It is now uploaded"
