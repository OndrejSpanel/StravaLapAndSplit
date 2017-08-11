package com.github.opengrabeso.collections

class CyclicVectorTest extends org.scalatest.FlatSpec {
  "An empty container" should "be empty" in {
    val empty = CyclicVector.empty[String]
    assert(empty.size == 0)
    assert(empty.isEmpty)
    assertThrows[NoSuchElementException](empty.head)
  }

  "Adding element" should "add it" in {
    val empty = CyclicVector.empty[String]
    val e = "E"
    val added = empty :+ e
    assert(!added.isEmpty)
    assert(added.size == 1)
    assert(added.head == e)
  }

  "Removing 1st element" should "make one-element container empty" in {
    val empty = CyclicVector.empty[String]
    val e = "E"
    val added = empty :+ e
    val removed = added.tail
    assert(removed.isEmpty)
    assertThrows[NoSuchElementException](removed.head)
  }

  it should "make 2nd element accessible" in {
    val empty = CyclicVector.empty[String]
    val e1 = "1st"
    val e2 = "2nd"
    val added2 = empty :+ e1 :+ e2
    val removed = added2.tail
    assert(removed.head == e2)
  }

  "Element added after removing some" should "be accessible" in {
    val empty = CyclicVector.empty[String]
    val e1 = "1st"
    val e2 = "2nd"
    val c = (empty :+ e1).tail :+ e2
    assert(c.head == e2)

  }
}
