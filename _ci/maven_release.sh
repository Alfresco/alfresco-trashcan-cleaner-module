  #!/usr/bin/env bash
set -ev

# Use full history for release
git checkout -B "${TRAVIS_BRANCH}"
# Add email to link commits to user
git config user.email "${GIT_EMAIL}"

mvn -B \
    -DskipTests \
    -Darguments="-DskipTests -Dmaven.javadoc.skip=true" \
    -DscmCommentPrefix="[maven-release-plugin][skip ci] " \
    -Dusername="${GIT_USERNAME}" \
    -Dpassword="${GIT_PASSWORD}" \
    release:clean release:prepare release:perform
    