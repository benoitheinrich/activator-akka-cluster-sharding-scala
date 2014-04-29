package sample.blog

import java.util.UUID
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSharding
import scala.util.Random

object Bot {

  private case object Tick

}

class Bot extends Actor with ActorLogging {

  import Bot._
  import context.dispatcher

  val tickTask = context.system.scheduler.schedule(3.seconds, 200.milliseconds, self, Tick)

  val postRegion = ClusterSharding(context.system).shardRegion(Post.shardName)
  val listingsRegion = ClusterSharding(context.system).shardRegion(AuthorListing.shardName)

  val from = Cluster(context.system).selfAddress.hostPort

  override def postStop(): Unit = {
    super.postStop()
    tickTask.cancel()
  }

  var n = 0
  val authors = for {
    a <- List("Patrik", "Martin", "Roland", "Björn", "Endre")
    i <- 1 to 100
  } yield a + i

  def currentAuthors = {
    val numberOfAuthors = Random.nextInt(4) + 1
    Random.shuffle(authors).take(numberOfAuthors).toList
  }

  def receive = create

  val create: Receive = {
    case Tick =>
      val postId = UUID.randomUUID().toString
      n += 1
      val postAuthors = currentAuthors
      val title = s"Post $n from node $from, with authors $postAuthors"
      postRegion ! Post.AddPost(postId, Post.PostContent(postAuthors, title, "..."))
      context.become(edit(postId, postAuthors))
  }

  def edit(postId: String, postAuthors: List[String]): Receive = {
    case Tick =>
      postRegion ! Post.ChangeBody(postId, "Something very interesting ...")
      context.become(publish(postId, postAuthors))
  }

  def publish(postId: String, postAuthors: List[String]): Receive = {
    case Tick =>
      postRegion ! Post.Publish(postId)
      context.become(list(postAuthors))
  }

  def list(postAuthors: List[String]): Receive = {
    case Tick =>
      listingsRegion ! AuthorListing.GetPosts(postAuthors.head)

    case AuthorListing.Posts(summaries) =>
      log.info("Posts by {}: {}", postAuthors.head, summaries.map(_.title).mkString("\n\t", "\n\t", ""))
      if (postAuthors.tail.isEmpty) context.become(create)
      else context.become(list(postAuthors.tail))
  }
}
