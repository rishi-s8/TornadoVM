#!/usr/bin/env python

#
# This file is part of Tornado: A heterogeneous programming framework: 
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2020, APT Group, Department of Computer Science,
# The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#

import os
import re

class Colors:
	RED   = "\033[1;31m"  
	BLUE  = "\033[1;34m"
	CYAN  = "\033[1;36m"	
	GREEN = "\033[0;32m"
	RESET = "\033[0;0m"
	BOLD    = "\033[;1m"
	REVERSE = "\033[;7m"

def updateMavenSettingsFile():

    f = open("scripts/templates/settings.xml", "r")
    settingsXML = f.read()
    
    javaHome = os.environ["JAVA_HOME"]
    settingsXML = settingsXML.replace("$$JDKPATH$$", javaHome)
    print Colors.BLUE + "JAVA_HOME    : " + Colors.GREEN +  javaHome  + Colors.RESET

    javaVersion = re.search(r"1\.(?:([\d._])+)", javaHome).group(0)
    if javaVersion:
        print Colors.BLUE + "JVM_VERSION  : " + Colors.GREEN +  javaVersion  + Colors.RESET
    settingsXML = settingsXML.replace("$$JDKVERSION$$", javaVersion)    
    
    ## Store the file in ~/.m2/settings.xml
    home = os.environ["HOME"]
    fout = open(home + "/.m2/settings.xml", "w")
    fout.write(settingsXML)
    fout.close()

if __name__ == "__main__":
    updateMavenSettingsFile()
