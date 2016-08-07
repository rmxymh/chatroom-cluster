Chatroom Cluster
========

A remote version chatroom project implemented with akka with Scala.

## Dependency

The following dependency version are under development environment:

* Scala: 2.11.8
* akka: 2.4.8
* sbt: 0.13

## Usage

* Use a Terminal: (Chatroom node 1)
  * Port: 6051

```bash
$ cd Chatroom
$ sbt compile
$ sbt run
```

* Use another Terminal: (Chatroom node 1)
  * Port: 6052

```bash
$ cd Chatroom
$ sbt compile
$ sbt
> run 6052
```

* Use another Terminal: (User Console)

```bash
$ cd UserConsole
$ sbt compile
$ sbt run
```

Please use terminal User Console as your input console.

