# Sample mean demo

This project demonstrates a simple application using <a href="https://github.com/alexandrainst/fresco-stat">fresco-stat</a>. 
The application takes input from three different parties, computes the mean of the inputs and reveals it to all parties.

The application is built using the command <code>mvn package</code>. After this is done, the demo
 may be run in three terminals on the same computer using these commands:

```
java -jar target/compute-mean.jar 1 [INPUT_1]
java -jar target/compute-mean.jar 2 [INPUT_2]
java -jar target/compute-mean.jar 3 [INPUT_3]
```
The program should take less than a second to run and output the mean, <code>(INPUT_1 + INPUT_2 + INPUT_3) / 3</code>. 

