# Peer Assessment Application

*Written with 💕 using Play+Scala*

This is a small web application I have developed during my first summer at the University

Mainly this repo will serve as an exercise in maintaining legacy code and a portfolio project to show my web dev skills

## Installation
1. Clone the repository and open the directory
```sh
$ git clone https://github.com/bekzatbaubek/peer-assessment.git
$ cd peer-assessment
```
2. Start the application in debug mode using [sbt](https://www.scala-sbt.org/)
```sh
$ sbt run
```
3. Visit [localhost:9000](http://localhost:9000) to open the home page

## Docker Image
After setting up Docker and sbt-native-packager, all the future commits trigger the Continous Integration through GitHub Actions (check out the Actions tab) - that compiles, tests, builds, and publishes the containerized version of the app

[Docker Repo](https://hub.docker.com/r/bekzatbaubek/peer-assessment)

### Run the container
```sh
$ docker pull bekzatbaubek/peer-assessment
$ docker run -p 9000:9000 bekzatbaubek/peer-assessment
```
Visit [localhost:9000](http://localhost:9000) to open the home page

## Tech Stack
* [Play Framework](https://www.playframework.com/)
* [SQLite Database](https://www.sqlite.org/index.html) through [Play Slick](https://www.playframework.com/documentation/2.8.x/PlaySlick)
* [Bootswatch Darkly theme](https://bootswatch.com/darkly/)
