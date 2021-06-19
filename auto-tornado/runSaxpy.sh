#!/bin/bash
javac -cp .:soot.jar:../tornado-annotation/target/classes LoopPar.java getBCI.java
java -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/ LoopPar ../examples/target/classes uk.ac.manchester.tornado.examples.Saxpy