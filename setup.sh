#!/bin/bash

#apt-get -y update && apt-get -y upgrade

###

cat << EOF > /home/vagrant/some.config
asdf
asdf
asdf
asdf
EOF

###

cat /home/vagrant/some.config

###