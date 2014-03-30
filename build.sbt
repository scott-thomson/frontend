name := """CADigital"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  // Select Play modules
  //jdbc,      // The JDBC connection pool and the play.api.db API
  //anorm,     // Scala RDBMS Library
  //javaJdbc,  // Java database API
  //javaEbean, // Java Ebean plugin
  //javaJpa,   // Java JPA plugin
  //filters,   // A set of built-in filters
  javaCore,  // The core Java API
  // WebJars pull in client-side web libraries
  "org.webjars" %% "webjars-play" % "2.2.0",
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" % "jquery" % "1.8.3",  
  "org.webjars" % "jquery-ui" % "1.10.3"    
  // Add your own project dependencies in the form:
  // "group" % "artifact" % "version"
)

//libraryDependencies ++= Seq(
//  "org.cddcore" %% "website" % "1.8.5.14",
//  "org.cddcore" %% "legacy" % "1.8.5.14"  
//)

play.Project.playScalaSettings