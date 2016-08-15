import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.twitter.util.{Future => TwitterF}
import scala.concurrent.{Future => ScalaF, Promise => ScalaP}
import language.implicitConversions

object Utils {
  object Implicits {
    implicit def twitterFutureToScalaFuture[T](twitterF: TwitterF[T]): ScalaF[T] = {
      val scalaP = ScalaP[T]
      twitterF.onSuccess { r: T =>
        val _ = scalaP.success(r)
      }
      twitterF.onFailure { e: Throwable =>
        val _ = scalaP.failure(e)
      }
      scalaP.future
    }
  }

  def time[A](name: String) (f: => Unit) = {
    val s = System.nanoTime
    f
    println(s"$name: ${((System.nanoTime - s) / 1e6).toLong} elapsed.")
  }

  def timeAsync[A](f: => Future[A])(implicit ctx: ExecutionContext): Future[(A, Long)] = {
    val s = System.nanoTime
    f map { x => (x, ((System.nanoTime - s) / 1e6).toLong) }
  }
}

