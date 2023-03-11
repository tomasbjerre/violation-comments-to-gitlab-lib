# Violation Comments to GitLab Lib
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.bjurr.violations/violation-comments-to-gitlab-lib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.bjurr.violations/violation-comments-to-gitlab-lib)

This is a library that adds violation comments from static code analysis to GitLab.

It uses [Violation Comments Lib](https://github.com/tomasbjerre/violation-comments-lib) and supports the same formats as [Violations Lib](https://github.com/tomasbjerre/violations-lib).

## Usage
This software can be used:
 * With a [Jenkins plugin](https://github.com/jenkinsci/violation-comments-to-gitlab-plugin).
 * With a [Gradle plugin](https://github.com/tomasbjerre/violation-comments-to-gitlab-gradle-plugin).
 * With a [Maven plugin](https://github.com/tomasbjerre/violation-comments-to-gitlab-maven-plugin).
 * From [Command Line](https://github.com/tomasbjerre/violation-comments-to-gitlab-command-line).

## Developer instructions

To build the code, have a look at `.travis.yml`.

To do a release you need to do `./gradlew release` and release the artifact from [staging](https://oss.sonatype.org/#stagingRepositories). More information [here](http://central.sonatype.org/pages/releasing-the-deployment.html).
