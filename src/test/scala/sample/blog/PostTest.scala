package sample.blog

import org.scalatest.{ShouldMatchers, FunSuiteLike}
import akka.testkit.{ImplicitSender, TestProbe, TestKit}
import akka.actor.ActorSystem
import sample.blog.Post.PostContent
import akka.persistence.ConfirmablePersistent
import sample.blog.AuthorListing.PostSummary

class PostTest extends TestKit(ActorSystem("ClusterSystem")) with FunSuiteLike with ImplicitSender with ShouldMatchers {

  test("test post") {
    val authorListing = TestProbe()
    /*
        ClusterSharding(system).start(
          typeName = Post.shardName,
          entryProps = Some(Post.props(authorListing.ref)),
          idExtractor = Post.idExtractor,
          shardResolver = Post.shardResolver)
    */

    //val postActor = TestActorRef(Post.props(authorListing.ref))
    val postActor = system.actorOf(Post.props(authorListing.ref))
    //val postActor = ClusterSharding(system).shardRegion(Post.shardName)
    println(s"postActor=$postActor")
    postActor ! Post.AddPost("12345", PostContent(List("benoit"), "title", "content"))
    postActor ! Post.GetContent("12345")

    expectMsg(PostContent(List("benoit"), "title", "content"))
  }

  test("test publish") {
    val authorListing = TestProbe()
    val postActor = system.actorOf(Post.props(authorListing.ref))
    println(s"postActor=$postActor")
    postActor ! Post.AddPost("12345", PostContent(List("benoit"), "title", "content"))
    postActor ! Post.Publish("12345")

    val cp = authorListing.expectMsgPF() {
      case p@ConfirmablePersistent(s: PostSummary, _, _) =>
        p.confirm()
        s
    }
    cp should be(PostSummary("benoit", "12345", "title"))
  }
}
