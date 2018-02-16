#!/bin/bash

if [ -z ${USER_UID:+x} ]
then
  export USER_UID=1000
  export GROUP_GID=1000
fi

clean () {
  docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle clean
}

install() {
  docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle install publishToMavenLocal
}

publish() {
  if [ -e "?/.gradle" ] && [ ! -e "?/.gradle/gradle.properties" ]
  then
    echo "odeUsername=$NEXUS_ODE_USERNAME" > "?/.gradle/gradle.properties"
    echo "odePassword=$NEXUS_ODE_PASSWORD" >> "?/.gradle/gradle.properties"
    echo "sonatypeUsername=$NEXUS_SONATYPE_USERNAME" >> "?/.gradle/gradle.properties"
    echo "sonatypePassword=$NEXUS_SONATYPE_PASSWORD" >> "?/.gradle/gradle.properties"
  fi
  docker-compose run --rm -u "$USER_UID:$GROUP_GID" gradle gradle publish
}

for param in "$@"
do
  case $param in
    clean)
      clean
      ;;
    install)
      install
      ;;
    publish)
      publish
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done

