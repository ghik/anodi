package com.github.ghik.anodi

import scala.concurrent.Await

package purescala {
  class DatabaseDriver

  class UserDao(db: DatabaseDriver)

  class ProductDao(db: DatabaseDriver)

  class ProductService(
    userDao: UserDao,
    productDao: ProductDao
  )

  class RestServer(
    productService: ProductService
  )

  object Application extends App {
    val databaseDriver = new DatabaseDriver
    val userDao = new UserDao(databaseDriver)
    val productDao = new ProductDao(databaseDriver)
    val productService = new ProductService(userDao, productDao)
    val restServer = new RestServer(productService)
  }
}

package lazyscala {
  class DatabaseDriver

  class UserDao(db: DatabaseDriver)

  class ProductDao(db: DatabaseDriver)

  class ProductService(
    userDao: UserDao,
    productDao: ProductDao,
  )

  class RestServer(
    productService: ProductService,
  )

  object Application extends App {
    lazy val userDao: UserDao = new UserDao(databaseDriver)
    lazy val productDao: ProductDao = new ProductDao(databaseDriver)
    lazy val databaseDriver: DatabaseDriver = new DatabaseDriver
    lazy val productService: ProductService = new ProductService(userDao, productDao)
    lazy val restServer: RestServer = new RestServer(productService)
  }
}

package implicits {
  class DatabaseDriver
  class UserDao(implicit db: DatabaseDriver)
  class ProductDao(implicit db: DatabaseDriver)
  class ProductService(implicit userDao: UserDao, productDao: ProductDao)
  class RestServer(implicit productService: ProductService)

  object Application extends App {
    implicit lazy val userDao: UserDao = new UserDao
    implicit lazy val productDao: ProductDao = new ProductDao
    implicit lazy val databaseDriver: DatabaseDriver = new DatabaseDriver
    implicit lazy val productService: ProductService = new ProductService
    implicit lazy val restServer: RestServer = new RestServer
  }

}

package components.fullyImplicit {

  class DatabaseDriver
  class UserDao(implicit db: DatabaseDriver)
  class ProductDao(implicit db: DatabaseDriver)
  class ProductService(implicit userDao: UserDao, productDao: ProductDao)
  class RestServer(implicit productService: ProductService)

  object AppComponents extends Components {
    implicit def databaseDriver: Component[DatabaseDriver] = singleton(new DatabaseDriver)
    implicit def userDao: Component[UserDao] = singleton(new UserDao)
    implicit def productDao: Component[ProductDao] = singleton(new ProductDao)
    implicit def productService: Component[ProductService] = singleton(new ProductService)
    implicit def restServer: Component[RestServer] = singleton(new RestServer)
  }

  object Application extends App {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      _ <- AppComponents.restServer.init
      _ = println("application initialized")
      _ <- Component.destroyAll(Seq(AppComponents.restServer))
      _ = println("application destroyed")
    } yield ()
  }

}

package components.fromImplicits {
  import scala.concurrent.duration.Duration

  class DatabaseDriver
  class UserDao(db: DatabaseDriver)
  class ProductDao(db: DatabaseDriver)
  class ProductService(userDao: UserDao, productDao: ProductDao)
  class RestServer(productService: ProductService)
  class Gen[A, B](a: A, b: B)

  object AppComponents extends Components {
    implicit def databaseDriver: Component[DatabaseDriver] = singletonFromImplicits[DatabaseDriver]
    implicit def userDao: Component[UserDao] = singletonFromImplicits[UserDao]
    implicit def productDao: Component[ProductDao] = singletonFromImplicits[ProductDao]
    implicit def productService: Component[ProductService] = singletonFromImplicits[ProductService]
    implicit def restServer: Component[RestServer] = singletonFromImplicits[RestServer]
    implicit def gen: Component[Gen[UserDao, ProductDao]] = singletonFromImplicits[Gen[UserDao, ProductDao]]
  }

  object Application extends App {
    import scala.concurrent.ExecutionContext.Implicits.global

    val fut = for {
      _ <- AppComponents.restServer.init
      _ = println("application initialized")
      _ <- Component.destroyAll(Seq(AppComponents.restServer))
      _ = println("application destroyed")
    } yield ()

    Await.result(fut, Duration.Inf)
  }

}

package components.explicit {
  import org.scalatest.funsuite.AnyFunSuite
  class DatabaseDriver(ps: ProductService)
  class UserDao(db: DatabaseDriver)
  class ProductDao(db: DatabaseDriver)
  class ProductService(userDao: UserDao, productDao: ProductDao)
  class RestServer(productService: ProductService)

  object AppComponents extends Components {
    def databaseDriver: Component[DatabaseDriver] =
      singleton(new DatabaseDriver(productService.ref))

    def userDao: Component[UserDao] =
      singleton(new UserDao(databaseDriver.ref))

    def productDao: Component[ProductDao] =
      singleton(new ProductDao(databaseDriver.ref))

    def productService: Component[ProductService] =
      singleton(new ProductService(userDao.ref, productDao.ref))

    def restServer: Component[RestServer] =
      singleton(new RestServer(productService.ref))
  }

  class DependencyGraphTest extends AnyFunSuite {
    ignore("dependency graph is acyclic") {
      Component.validateAll(Seq(AppComponents.restServer))
    }
  }

  object Application extends App {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      _ <- AppComponents.restServer.init
      _ = println("application initialized")
      _ <- Component.destroyAll(Seq(AppComponents.restServer))
      _ = println("application destroyed")
    } yield ()
  }

}

package components.modularized {
  class DatabaseDriver

  class UserDao(db: DatabaseDriver)

  class ProductDao(db: DatabaseDriver)

  class ProductService(
    userDao: UserDao,
    productDao: ProductDao,
  )

  class RestServer(
    productService: ProductService,
  )

  trait DbComponents extends Components {
    def databaseDriver: Component[DatabaseDriver] =
      singleton(new DatabaseDriver)
  }

  trait UserComponents extends Components {
    def databaseDriver: Component[DatabaseDriver]

    def userDao: Component[UserDao] =
      singleton(new UserDao(databaseDriver.ref))
  }

  trait ProductComponents extends UserComponents with DbComponents {
    def productDao: Component[ProductDao] =
      singleton(new ProductDao(databaseDriver.ref))

    def productService: Component[ProductService] =
      singleton(new ProductService(userDao.ref, productDao.ref))
  }

  object AppComponents extends UserComponents with ProductComponents {
    def restServer: Component[RestServer] =
      singleton(new RestServer(productService.ref))
  }

  object Application extends App {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      _ <- AppComponents.restServer.init
      _ = println("application initialized")
      _ <- Component.destroyAll(Seq(AppComponents.restServer))
      _ = println("application destroyed")
    } yield ()
  }
}
