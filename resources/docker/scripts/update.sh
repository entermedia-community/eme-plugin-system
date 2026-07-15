#!/bin/bash
#EM10
key="$1"
BUILD_NUMBER=""
VERSION="em12"
case $key in
    -b|--build)
    BUILD_NUMBER="$2"
    ;;
    -dev|--dev|-main|--main|"")
    VERSION="main"
    ;;
esac

if [ -z "$BUILD_NUMBER" ]; then
    if [ $VERSION == "main" ]; then
        ## main branch is dev
        curl -XGET -o /usr/share/eme-lib.tar.gz https://dev.entermediadb.org/jenkins/view/EM12/job/eme-lib/lastSuccessfulBuild/artifact/deploy/eme-lib.tar.gz > /dev/null
    else
        curl -XGET -o /usr/share/eme-lib.tar.gz "http://dev.entermediadb.org/jenkins/view/EM12/job/eme-lib/$VERSION/artifact/deploy/eme-lib.tar.gz" > /dev/null
    fi
  
  status=$?
  if [ $status -ne 0 ]; then
    echo "Cannot download the latest WAR on EM10"
    exit $status
  fi
else
  status=$?
  if [ $status -ne 0 ]; then
    echo "Cannot download the WAR for build #$BUILD_NUMBER on EM10"
    exit $status
  fi
fi

#rm -rf /opt/entermediadb/webapp/WEB-INF/{base,lib}
rm -rf /usr/share/eme-lib
cd /usr/share/ && tar -xzf eme-lib.tar.gz && rm -rf eme-lib.tar.gz 

pid=`pgrep -f "eme"`
kill -SIGTERM $pid
echo "Docker restarting"