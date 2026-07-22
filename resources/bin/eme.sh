#!/bin/bash -x 

set -e

## Download and run this script to create a new eme server instance
## curl -o eme.sh -jL get-eme.eme.world
## 1. Local Developer Instance (Don't run as root)
##    bash eme.sh developer <server-path> 
## 2. Docker Instance 
##    sudo bash eme.sh dockercreate <server-path> <nodenumber> <ownedby>

CMD="${1:-help}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "$CMD" = "version" ]; then
    echo "eme-lib version: 0.1.0"
    exit 0
fi

case "$CMD" in
  branchinit | developer | dockercreate)
    #Clone the eme-server-client repo to the specified path

    #verify is not running as root
    if [[ $(id -u) -eq 0 ]]; then
        echo "Don't run this script as root."
        exit 1
    fi

    SERVERHOME="$2"
    SERVERNAME="$(basename "$SERVERHOME")"

    if [ -d "$SERVERHOME" ]; then
        echo "Server path already exists: ${SERVERHOME}"
        exit 1
    fi
    
    git clone https://github.com/entermedia-community/${SERVERNAME}.git ${SERVERHOME}
    cd $SERVERHOME
    git remote add upstream https://github.com/entermedia-community/eme-server.git
    git config --global init.defaultBranch main
    git pull upstream main

    git submodule update --init --recursive --remote

    git push -u origin main

  
  ;;&

  developer)
    ## Opens default workspace in VS Code for development
    echo "Opening default workspace in VS Code for development"
    code eme-server.code-workspace

  ;;

  branchupdate | branchpush)
    ## Updates the eme-server-client repo to the latest version
    echo "Updating eme-server-client repo to the latest version"
    SERVERHOME="$2"
    cd $SERVERHOME

    git stash
    git submodule update --remote --merge
    git add -A .
    git commit -a -m "Update submodules"
    git pull
    git push
    #git fetch upstream
    #git rebase upstream/main
    git stash pop
    
   ;;&

  branchpush)
    ## Pushes the eme-server-client repo to the remote repository
    echo "Pushing eme-server-client repo to the remote repository"
    SERVERHOME="$2"
    cd $SERVERHOME
    COMMITMESSAGE="${3:-Update from Client}"
   
    git add -A .
    git commit -m "$COMMITMESSAGE" || true
    git pull
    git push
  ;;
  
  dockercreate)
    ## eme.sh dockercreate <server-path> <nodenumber>

    ##if missing $2, $3 or $4 exit
    if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
        echo "Usage: sudo eme.sh dockercreate <server-path> <nodenumber> <ownedby>"
        exit 1
    fi

    echo "Creating Docker instance for $SERVERHOME"
    SERVERHOME="$2"
    SERVERNAME="$(basename "$SERVERHOME")"
    NODENUMBER="$3"
    OWNEDBY="$4"
    #check if .env file exists
    
    mkdir -p "$SERVERHOME"
    echo "INSTANCE=$SERVERNAME$NODENUMBER" > "$SERVERHOME/.env"
    echo "SCRIPTROOT=${SERVERHOME}/bin" >> "$SERVERHOME/.env"
    echo "SITE=$SERVERNAME" >> "$SERVERHOME/.env"
    echo "NODENUMBER=$NODENUMBER" >> "$SERVERHOME/.env"
    ##echo "IP_ADDR=$IP_ADDR" >> "$SERVERHOME/.env"
    sudo chown -R "$OWNEDBY:$OWNEDBY" "$SERVERHOME"
    
    curl -s https://raw.githubusercontent.com/entermedia-community/eme-plugin-system/refs/heads/main/resources/docker/scripts/eme-docker-init.sh | sudo bash -s -- "$SERVERNAME" "$NODENUMBER" "$OWNEDBY"
  ;;

  dockerstart)
    #Verify if $USERID is passed in
    if [ -z "$USERID" ]; then
       echo "USERID not set"
       exit 1 
    fi
    #make sure $USERID doees not already exist as a user, if not create entermedia user with $USERID and $GROUPID

    if [[ ! $(id $USERID 2>/dev/null) ]]; then
        groupadd -g $GROUPID entermedia
        useradd -ms /bin/bash entermedia -g entermedia -u $USERID
        echo "entermedia ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/entermedia
        chmod 0440 /etc/sudoers.d/entermedia
    fi

    JAVA_HOME="/usr/lib/jvm/java-18-openjdk-amd64"
    export JAVA_HOME

    truncate -s 0 /etc/resolv.conf
    echo 'nameserver 1.1.1.1' >>/etc/resolv.conf
    echo 'nameserver 8.8.8.8' >>/etc/resolv.conf
    echo 'options ndots:0' >>/etc/resolv.conf

    sudo -u entermedia /usr/bin/eme start "$2"
    ;;

  init | start)    
    if [[ $(id -u) -eq 0 ]]; then
        echo "Don't run this script as root."
        exit 1
    fi

    ##JAVA_HOME is not set throw an error if JAVA_HOME is not set
    if [ -z "$JAVA_HOME" ]; then
        #checi if there is a jre path
        if [ -d "/usr/lib/jvm/jre" ]; then
            JAVA_HOME="/usr/lib/jvm/jre"
        elif [ -d "/usr/lib/jvm/default-java" ]; then
            JAVA_HOME="/usr/lib/jvm/default-java"
        else
            echo "JAVA_HOME is not set and /usr/lib/jvm/jre does not exist. Please set JAVA_HOME to a valid JRE path."
            echo "Please run with your local java version: sudo update-alternatives --install /usr/lib/jvm/jre jre /usr/lib/jvm/java-X.XX.X-openjdk-amd64 20000"
            exit 1
        fi
    fi  
    
    #Set USERID and GROUPID to the current user if not running in Docker
    USERID="$(id -un)"
    if GROUPNAME=$(id -gn "$USERID" 2>/dev/null); then
        GROUPID="$GROUPNAME"
    else
        GROUPID="$USERID"
    fi

    SERVERHOME="$2"

    echo "**** Starting server from: $SERVERHOME"

    if [ ! -d "$SERVERHOME/.git" ]; then
        sudo mkdir -p "$SERVERHOME"
        sudo chown "$USERID:$GROUPID" "$SERVERHOME"
        cd "$SERVERHOME"
        git init
        git remote add origin https://github.com/entermedia-community/eme-server.git
        git pull origin main --depth 1
        
        # Allow users fork 
        #git remote add upstream https://github.com/entermedia-community/eme-server.git

        #Setup submodules
        git submodule update --init --recursive --depth 1
        
    fi  
    
    cd "$SERVERHOME"

    #Compile the eme-lib if it has not been compiled yet
    $SERVERHOME/plugins/system/resources/bin/compile.sh
    
    #$USER is the user running the container

    if [ ! -d "$SERVERHOME/tomcat" ]; then
        # Copy tomcat conf and webapp templates from eme-lib deploy
        # Create directory structure
        mkdir -p "$SERVERHOME/tomcat" "$SERVERHOME/tomcat/conf" "$SERVERHOME/tomcat/logs" "$SERVERHOME/tomcat/webapps" "$SERVERHOME/tomcat/work"
        #cp -rn "$EMELIB/tomcat/conf/." "$SERVERHOME/tomcat/conf/" 2>/dev/null || true
        #cp -rpn "$EMELIB/tomcat/bin" "$SERVERHOME/tomcat/" 2>/dev/null || true
        echo "export CATALINA_BASE=\"$SERVERHOME/tomcat\"" >>"$SERVERHOME/tomcat/bin/setenv.sh"
        sudo chown -R $USERID:$GROUPID "$SERVERHOME/tomcat" 
        #chmod 755 "$SERVERHOME/tomcat/bin/*.sh"
    fi

 #   sudo chown ${USERID}:${GROUPID} "$SERVERHOME/webapp/"
    if [ ! -L "$SERVERHOME/data" ]; then
        mkdir -p "./webapp/WEB-INF/data"
        ln -nsf "./webapp/WEB-INF/data" "./data" 
        sudo chown -R $USERID:$GROUPID "$SERVERHOME/data"
    fi

    if [ ! -d "$SERVERHOME/data/system" ]; then
         #mkdir -p "$SERVERHOME/webapp/WEB-INF/data/system/"
         cp -rp "$EMELIB/plugins/system/defaultdata" "$SERVERHOME/webapp/WEB-INF/data/system"
         sudo chown -R $USERID:$GROUPID "$SERVERHOME/webapp/WEB-INF/data/system/"
    fi

    # symbolically link users plugins to webapp first!
    for plugin in "$SERVERHOME/plugins"/*/; do
        pluginname="$(basename "$plugin")"
        if [ -d "${plugin}html" ]; then
            if [ ! -L "../webapp/$pluginname" ]; then
                ln -nsf "../plugins/${pluginname}/html" "./webapp/$pluginname"
            fi
        fi
    done

    # symbolically link built-in plugins from emelib to webapp, but only if they don't already exist in the target plugins directory (i.e. user overwrote them)
    #only do this if the emelib is relative

    #for plugin in "$(get_relative_emelib 1)/plugins"/*/; do
    #    pluginname="$(basename "$plugin")"

    #    if [ -d "${plugin}html" ]; then
    #        ##if its an invalid symbolic link then remove it and create a new one
    #        echo "Adding plugin: $SERVERHOME/webapp/$pluginname"
    #        if [ -L "$SERVERHOME/webapp/$pluginname" ]; then
    #            echo "Removing invalid symbolic link: $SERVERHOME/webapp/$pluginname"
    #            rm "$SERVERHOME/webapp/$pluginname"
    #        fi
    #        ln -nsf "$(get_relative_emelib 2)/plugins/${pluginname}/html"  "$SERVERHOME/webapp/$pluginname"
    #    fi
    #done

    export EMSERVER="${2:-$SCRIPT_DIR}"
    export EMSERVER="$(cd "$EMSERVER" && pwd)"
    export EMSERVER_NAME="$(basename "$EMSERVER")"

    ARGS_TEMPLATE="$SERVERHOME/plugins/system/resources/bin/tomcat.args"

    echo "**** Starting $EMSERVER_NAME using JAVA_HOME  = $JAVA_HOME"


    if [ ! -f "$ARGS_TEMPLATE" ]; then
        echo "ERROR: $ARGS_TEMPLATE not found. Run: eme.sh init <server-path>" >&2
        exit 1
    fi

    if( $CMD = "init" ); then
        echo "Initialization complete. Run: eme.sh start <server-path> to start the server."
        exit 0
    fi  

    # Java @argfile does not expand shell variables, so expand them here
    mkdir -p "$SERVERHOME/tomcat/work"
    EXPANDED_ARGS=$( mktemp $SERVERHOME/tomcat/work/tomcat-args.XXXXXX)
    sudo chmod 600 "$EXPANDED_ARGS"
    trap " rm -f $EXPANDED_ARGS" EXIT
    sed -e "s|\$SERVERHOME|$SERVERHOME|g" "$ARGS_TEMPLATE" > "$EXPANDED_ARGS"

    JAVA="$JAVA_HOME/bin/java"

    echo "$JAVA $(cat "$EXPANDED_ARGS") org.apache.catalina.startup.Bootstrap start"

    #Run Tomcat as entermedia user
    #  "$JAVA" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start 

    
    CATALINA_BASE="$SERVERHOME/tomcat"
    export CATALINA_BASE
    #SIGTERM-handler
    term_handler() {
        
        pid=$(pgrep -f "$CATALINA_BASE/conf/logging.properties")
        echo "SIGTERM received, shutting down Tomcat (PID: $pid)"
        if [[ ! -z $pid ]]; then
            if [ $pid -ne 0 ]; then
                echo "Deployment shutdown start"
                 sh -c "$SERVERHOME/tomcat/bin/catalina.sh stop"
                kill -SIGTERM "$catalinapid"
                while [ -e /proc/$pid ]; do
                    printf .
                    sleep 1
                done
            fi
        fi
        echo "Tomcat shutdown complete, exiting (143)"
        exit 143 # 128 + 15 -- SIGTERM
    }

    #SIGKILL
    # setup handlers
    # on callback, kill the last background process, which is `tail -f /dev/null` and execute the specified handler
    trap 'kill ${!}; term_handler' SIGTERM

    #Run application
    # sh -c "$EMTARGET/tomcat/bin/catalina.sh run" &

    "$JAVA" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start 
    
    catalinapid=0
    while [ $catalinapid -eq 0 ]; do
        catalinapid=$(pgrep -f "eme start")
        ##make sure its an integer not a string
        catalinapid=$(echo "$catalinapid" | awk '{print int($0)}')
        echo "Catalina PID: $catalinapid"
        sleep 1
    done

    wait $catalinapid
    echo "Tomcat process $catalinapid exited"
    ;;

    update)
        SERVERHOME="$2"

        if [ ! -d "$SERVERHOME/.git" ]; then
                echo "ERROR: $SERVERHOME is not a git repository."
                exit 1
        fi

        cd "$SERVERHOME"
        CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"

        echo "**** Updating $SERVERHOME (branch: $CURRENT_BRANCH)"
        git fetch origin
        git pull --ff-only origin "$CURRENT_BRANCH"
        git submodule sync --recursive
        git submodule update --init --recursive --depth 1
        echo "Update complete."
        ;;

  help)
        echo -e "Eme Server Management Script\n"
        echo "Setup local dev environment: "
        echo " eme.sh developer <server-path>"
        echo -e "\nUsage in Host: "
        echo " sudo eme.sh dockercreate <server-path> <nodenumber> <ownedby> ]"
        echo -e "\nUsage inside Docker: "
        echo " eme.sh [ version | update [server-path] | dockerstart <server-path> | start [server-path]]" >&2
    exit 1
    ;;
esac
