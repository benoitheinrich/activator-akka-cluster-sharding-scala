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
    val postActor = system.actorOf(Post.props(authorListing.ref))
    println(s"postActor=$postActor")
    postActor ! Post.AddPost("12345", PostContent(List("benoit"), "title", "content"))
    postActor ! Post.GetContent("12345")

    expectMsg(PostContent(List("benoit"), "title", "content"))
  }

  test("test publish to a single author") {
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

  test("test publish to two authors") {
    val authorListing = TestProbe()
    val postActor = system.actorOf(Post.props(authorListing.ref))
    println(s"postActor=$postActor")
    postActor ! Post.AddPost("12345", PostContent(List("benoit", "fred"), "title", "content"))
    postActor ! Post.Publish("12345")

    val cp1 = authorListing.expectMsgPF() {
      case p@ConfirmablePersistent(s: PostSummary, _, _) =>
        p.confirm()
        s
    }
    val cp2 = authorListing.expectMsgPF() {
      case p@ConfirmablePersistent(s: PostSummary, _, _) =>
        p.confirm()
        s
    }
    cp1 should be(PostSummary("benoit", "12345", "title"))
    cp2 should be(PostSummary("fred", "12345", "title"))
  }
}
