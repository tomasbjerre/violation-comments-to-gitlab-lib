# Violation Comments to GitLab Lib
[![Maven Central](https://img.shields.io/maven-central/v/se.bjurr.violations/violation-comments-to-gitlab-lib.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/se.bjurr.violations/violation-comments-to-gitlab-lib)

This is a library that adds violation comments from static code analysis to GitLab.

It uses [Violation Comments Lib](https://github.com/tomasbjerre/violation-comments-lib) and supports the same formats as [Violations Lib](https://github.com/tomasbjerre/violations-lib).

## Usage
This software can be used:
 * With a [Jenkins plugin](https://github.com/jenkinsci/violation-comments-to-gitlab-plugin).
 * With a [Gradle plugin](https://github.com/tomasbjerre/violation-comments-to-gitlab-gradle-plugin).
 * With a [Maven plugin](https://github.com/tomasbjerre/violation-comments-to-gitlab-maven-plugin).
 * From [Command Line](https://github.com/tomasbjerre/violation-comments-to-gitlab-command-line).

## Developer instructions

To build the code, have a look at `.github/workflows/gradle-ci.yaml`.

To do a release you need to do `./gradlew updateVersion && ./gradlew release`. More information [here](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/main/docs/central.md#secrets).
