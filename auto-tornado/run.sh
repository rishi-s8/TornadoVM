#!/bin/bash
# $1: ../examples/target/classes
# $2: uk.ac.manchester.tornado.examples.Saxpy
javac -cp .:soot.jar:../tornado-annotation/target/classes LoopPar.java getBCI.java
java -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/ LoopPar $1 $2