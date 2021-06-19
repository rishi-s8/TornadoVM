#!/bin/bash
if [[ $1 == "-c" ]]
then
  javac -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../com.microsoft.z3.jar RunLoopPar.java DepTest.java getBCI.java PurityInfo.java PurityAnalysis.java && \
  LD_LIBRARY_PATH=. java -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../z3/build/com.microsoft.z3.jar RunLoopPar "$2" "$3" && \
  mv annotationMap annotationMap.bak && cp annotationMapEmpty annotationMap && cd .. && printf "\n================\nStarting TornadoVM in serial mode\n================\n\n" && \
  time tornado -m "tornado.examples/$3" "$4" && \
  mv auto-tornado/annotationMap{.bak,} && printf "\n================\nStarting TornadoVM in parallel mode\n================\n\n" && \
  time tornado -m "tornado.examples/$3" "$4"
elif [[ $1 == "-s" ]]
then
  javac -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../com.microsoft.z3.jar RunLoopPar.java DepTest.java getBCI.java PurityInfo.java PurityAnalysis.java && \
  LD_LIBRARY_PATH=. java -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../z3/build/com.microsoft.z3.jar RunLoopPar "$2" "$3" && \
  mv annotationMap annotationMap.bak && cp annotationMapEmpty annotationMap && cd .. && printf "\n================\nStarting TornadoVM in serial mode\n================\n\n" && \
  tornado -m "tornado.examples/$3" "$4"
elif [[ $1 == "-p" ]]
then
  javac -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../com.microsoft.z3.jar RunLoopPar.java DepTest.java getBCI.java PurityInfo.java PurityAnalysis.java && \
  LD_LIBRARY_PATH=. java -cp .:soot.jar:../tornado-annotation/target/classes:../runtime/target/classes/:../../z3/build/com.microsoft.z3.jar RunLoopPar "$2" "$3" && \
  cd .. && printf "\n================\nStarting TornadoVM in parallel mode\n================\n\n" && \
  tornado -m "tornado.examples/$3" "$4"
else
  printf "Please provide an appropriate mode of operation\n"
  printf "  -c  Compare the running times of serial and parallel modes\n"
  printf "  -s  Run in serial mode\n"
  printf "  -p  Run in parallel mode\n"
fi

