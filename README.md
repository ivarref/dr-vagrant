# Dr Vagrant

The idea of this project is to add Docker-style building to Vagrant VMs using VirtualBox snapshots.

Usage

    $ vboxmanage import ~/.vagrant.d/boxes/ubuntu-VAGRANTSLASH-trusty64/14.04/virtualbox/box.ovf

    $ mvn clean compile exec:java -Dexec.mainClass=RunMe
    ... wait ...
    >>> Exec ls -l /home/vagrant
    total 16
    -rw-rw-r-- 1 vagrant vagrant 17 Aug 15 16:05 cmd-snap-001-1081155467.sh
    -rw-rw-r-- 1 vagrant vagrant 18 Aug 15 16:12 cmd-snap-002--1806679111.sh
    -rw-rw-r-- 1 vagrant vagrant 60 Aug 15 16:15 cmd-snap-003-905095321.sh
    -rw-r--r-- 1 root    root     6 Aug 15 16:15 random-2bf2f567-7ca8-41d9-a309-cfacf0869f5b.txt
    >>> Command exited with code 0

    # The random filenames are generated, run it again and you will see:

    $ mvn clean compile exec:java -Dexec.mainClass=RunMe
    ...
    >>> Exec ls -l /home/vagrant
    total 16
    -rw-rw-r-- 1 vagrant vagrant 17 Aug 15 16:05 cmd-snap-001-1081155467.sh
    -rw-rw-r-- 1 vagrant vagrant 18 Aug 15 16:12 cmd-snap-002--1806679111.sh
    -rw-rw-r-- 1 vagrant vagrant 60 Aug 15 16:16 cmd-snap-003--1550698184.sh
    -rw-r--r-- 1 root    root     6 Aug 15 16:16 random-51fa4c52-37e8-4c2d-969d-d19ebb0f86a5.txt
    >>> Command exited with code 0

Note that the disk / VM here have been reverted to an earlier snapshot state because the commands changed.
Thus the previous random file is gone, and a new one is created.
This is the whole point of the project / PoC.

