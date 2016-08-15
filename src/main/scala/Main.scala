import java.util.UUID

import cats.data.Xor
import com.twitter.finagle.Http
import com.twitter.finagle.http.Request
import com.twitter.finagle.loadbalancer.{Balancers, LoadBalancerFactory}
import com.twitter.util.{Await, Future}
import concurrent.duration._
import language.postfixOps
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import Utils.Implicits._
import scala.util.{Failure, Success}
import concurrent.ExecutionContext.Implicits.global

trait Message
final case class Person(name: String, age: Int) extends Message
final case class Envelope[A <: Message](payload: A, server: String, appId: String)

object Envelope {
  private val id = UUID.randomUUID()
  def apply[A <: Message](payload: A): Envelope[A] =
    Envelope(payload, java.net.InetAddress.getLocalHost.toString, id.toString)
}

object Main {
  def main(reqCount: Int) = {
    val balancer: LoadBalancerFactory = Balancers.heap()

    val service =
      Http.client
        .withLoadBalancer(balancer)
        .withSessionPool.maxSize(10)
        .newService((192 to 195) map (x => s"10.70.16.$x:35000") mkString ",")

    val res = Utils.timeAsync {
      val req = Request("/person/kot")
      Future
        .collect((1 to reqCount).map(_ => service(req)))
        .map { resps =>
          resps
            .map(resp => decode[Envelope[Person]](resp.contentString))
            .collect { case Xor.Right(x) => x }
            .groupBy(x => x.server)
            .map { case (s, xs) => (s, xs.length) }
        }
    }

    res.onComplete {
      case Success((m, elapsedMillis)) =>
        println(s"$elapsedMillis ms elapsed, ${reqCount * 1000 / elapsedMillis} req/s")
        m foreach { case (server, n) => println(s"$server: $n") }
      case Failure(e) => println(e)
    }
  }
}