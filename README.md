# BitVerify
Group Project for document sharing and verification based on the bitcoin blockchain and a distributed network

**Team Charlie**

Computer Science, Part IB

University of Cambridge, 2016

### Instructions to build jar
Run the Gradle assemble task. (Gradle wrapper included in repository)

In a command line, point your working directory to the root of the cloned repository.

On Windows, run
```
gradlew.bat assemble
```
On Linux, run
```
./gradlew assemble
```
You can of course also use your IDE with a Gradle extension.

### Instructions to run the jar/application

_(Requirements: Java 8)_

From a command line, use (e.g.)
```
java -jar bitverify-0.1.0.jar
```
The application will start using the GUI.

##### Options
To use the Command Line Interface, use the option --cli, so:
```
java -jar bitverify-0.1.0.jar --cli
```

For backwards compatibility, an option for the GUI is also provided:
```
java -jar bitverify-0.1.0.jar --gui
```

