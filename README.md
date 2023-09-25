# (Almost no) Dependency Injection for Scala

`anodi` is a Scala micro library for initialization of application components ("beans")
and dependency injection. It can be summarized as "lazy vals on steroids" and features:

* defining application components in plain Scala (e.g. constructor invocations)
* automatic resolution of dependency graph and initialization order
* **parallel** initialization of independent components
* opt-in dependency injection with Scala implicits/givens (compile-time "autowiring")

## Project setup (sbt)

```scala
libraryDependencies += "com.github.ghik" %% "anodi" % "<version>"
```

## Compatibility

`anodi` is currently available for Scala 2.13 and Scala 3

## Defining components

An application is made of _components_. In this tutorial, a _component_ is defined as an object
that usually has at least one of the following properties:

* it represents a "service" within your system, e.g. HTTP server, database access layer, etc.
* it has a lifecycle: initialization and destruction logic (with side effects)
* it acquires and releases resources
* it is stateful
* it depends on other components

Components are defined in an object or class that extends `Components` trait which provides `component`
and `singleton` methods (macros) which interpret a plain Scala expression (e.g. constructor invocation) into
a `Component` definition.

```scala
import scala.concurrent._
import scala.concurrent.duration._
import com.github.ghik.anodi._

class Database {
  // DB initialization goes here...
}
class Server(database: Database) {
  // server initialization goes here...
  
  def join(): Unit = { /* wait for shutdown */ }
}

object MyApp extends Components {
  def server: Component[Server] =
    singleton(new Server(database.ref))

  def database: Component[Database] =
    singleton(new Database)
    
  def main(args: Array[String]): Unit = {
    // use appropriate execution context of your choice for application initialization
    import ExecutionContext.Implicits.global
    val srv = Await.result(server.init, Duration.Inf)
    srv.join()
  }
}
```

## `Component`

The `Component` type can be summarized as an enhanced version of Scala's built-in `lazy val` construct.
It is associated with a lazy-evaluated expression that creates and initializes the component (often a
simple constructor invocation, but it may be an arbitrary piece of code).

Wrapping component definitions into `Component` instances relieves the programmer from manually figuring out
the initialization order of multiple interdependent components. Note that simply using `lazy val`s would already
do that job, but `Component` does this better because:

* in case of failures, exceptions are wrapped in a way that lets you track the initialization path that
  leads to the faulty component (i.e. a stack trace but nicer to read than the raw one)
* dependency cycles are detected before any initialization is done - a nice error message is displayed that lets
  you quickly see where the cycle is

`Component` instances are aware of their source position. If they are assigned to a `val` or `def`, the name of
this `val` or `def` is interpreted as the component name. This name may be later useful for debugging.
Here's an example of a dependency cycle error that uses component names and source positions:

```
Exception in thread "main" com.github.ghik.anodi.DependencyCycleException: component dependency cycle detected:
  server(MyApp.scala:13) ->
  service(MyApp.scala:16) ->
  database(MyApp.scala:19) ->
  server(MyApp.scala:13)
```

## Entry points

In order to start our application in the previous example, we explicitly requested initialization of the
`server` component. This way we assumed `server` to be an _entry point_ of our application - the primary
service that exposes core system functionality (e.g. an HTTP server).
All the other components are initialized only as direct or indirect dependencies of this toplevel entry point.

If your application consists of multiple such entry points, e.g. an UI server, REST API server, FTP server, etc.
then it is recommended to create a single toplevel component whose purpose is solely to aggregate all the
"real" entry points into one.

```scala
import com.github.ghik.anodi._

class UiServer
class ApiServer
class FtpServer

class FullApplication(ui: UiServer, api: ApiServer, ftp: FtpServer)

object MyApp extends Components {
  def ui: Component[UiServer] = singleton(new UiServer)
  def api: Component[ApiServer] = singleton(new ApiServer)
  def ftp: Component[FtpServer] = singleton(new FtpServer)
  
  def fullApplication: Component[FullApplication] =
    singleton(new FullApplication(ui.ref, api.ref, ftp.ref))
}
```

This way your application always has a single entry point component which makes it easier to navigate
through code and understand its structure (knowing where to start).

## Explicit dependency references

A `Component` may refer to another `Component` using `.ref`, e.g.

```scala
class Database
class Server(db: Database)

object MyApp extends Components {
  def database: Component[Database] = singleton(new Database)
  def server: Component[Server] = singleton(new Server(database.ref))
}
```

`.ref` is not a real method. It exists only during compilation and is interpreted by the `component` or `singleton` macro.
A dependency reference is extracted by the macro out of the component initialization expression.
This way the macro separates initialization of a component from initialization of its dependencies.
This technique makes it possible to inspect the dependency graph before initializing any components.
This allows early cycle detection and lets us parallelize component initialization.

**NOTE**: If you're familiar with `sbt`, then `.ref` works somewhat similarly to `.value` in `sbt` settings & task definitions.

## Implicit dependency injection

`.ref` is used to refer to dependencies _explicitly_. This isn't necessarily bad because it gives you the most control and often produces
the most readable code (it's the least "magic" way). However, passing a lot of constructor parameters manually may be tedious.
That's why most DI frameworks feature some form of "autowiring" that figures out dependencies automatically by their type.
In traditional Java DI frameworks, this autowiring is done with **runtime reflection**. In Scala, we can leverage **implicits** for this purpose
and have everything wired in **compile time**.

In order for your `Component` to be implicitly injectable, simply declare it as `implicit`/`given`. Then you can declare your dependencies
(e.g. constructor parameters) also as implicits. The framework takes care of the `implicit Component[Thing] => implicit Thing` conversion.

```scala
class Database
class Server(implicit db: Database)

object MyApp extends Components {
  implicit def database: Component[Database] = singleton(new Database)
  def server: Component[Server] = singleton(new Server))
}
```

## Explicitly-declared implicit dependencies

A middle ground between implicit and explicit dependencies is a situation where a component definition is
declared as `implicit`/`given` but a dependency to that component is expressed with an _explicit_ parameter.
This is how it looks in code:

```scala
class Database
class Server(db: Database)

object MyApp extends Components {
  implicit def database: Component[Database] = singleton(new Database)
  def server: Component[Server] = singleton(fromImplicits[Server])
}
```

This is a nice sweet spot between fully implicit and fully explicit components that combines its
advantages:

* dependency injection is handled for you by the compiler, using standard implicit search
* implicit search is used **only** when explicitly requested using `fromImplicits`
* constructor parameters do not need to be `implicit` so you avoid unintended consequences 
  of that (i.e. params being seen as implicits within the class body).

## Complete example

See [ComponentsExample.scala](https://github.com/ghik/anodi/blob/main/src/test/scala/com/github/ghik/anodi/ComponentsExample.scala)
