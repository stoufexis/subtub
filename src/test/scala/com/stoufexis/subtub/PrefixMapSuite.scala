package com.stoufexis.subtub

import weaver.*

import com.stoufexis.subtub.data.PositionedPrefixMap
import com.stoufexis.subtub.data.PrefixMap

object PrefixMapSuite extends SimpleIOSuite:
  val stringMap: PrefixMap[String, String, Int] =
    PrefixMap.empty

  pureTest("simple insertion works"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("abc", "C", 3)
        .updateAt("a", "D", 4)
        .updateAt("ab", "E", 5)
        .updateAt("abc", "F", 6)

    expect.all(
      inserted.getMatching("") == List(1, 4, 2, 5, 3, 6),
      inserted.getMatching("a") == List(1, 4, 2, 5, 3, 6),
      inserted.getMatching("ab") == List(2, 5, 3, 6),
      inserted.getMatching("abc") == List(3, 6),
      inserted.getMatching("abcd") == Nil
    )

  pureTest("simple removal works"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("abc", "C", 3)

    expect.all(
      inserted.getMatching("") == List(1, 2, 3),
      inserted.removeAt("a", "B").getMatching("") == List(1, 2, 3),
      inserted.removeAt("ab", "B").getMatching("") == List(1, 3)
    )

    val removedAll: PrefixMap[String, String, Int] =
      inserted
        .removeAt("a", "A")
        .removeAt("ab", "B")
        .removeAt("abc", "C")

    expect(removedAll.getMatching("") == Nil && removedAll.isEmpty)

  pureTest("positioned prefix map works"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("abc", "C", 3)

    expect.all(
      PositionedPrefixMap(inserted, "").getMatching == List(1, 2, 3),
      PositionedPrefixMap(inserted, "a").getMatching == List(1, 2, 3),
      PositionedPrefixMap(inserted, "ab").getMatching == List(2, 3),
      PositionedPrefixMap(inserted, "abc").getMatching == List(3),
      PositionedPrefixMap(inserted, "abcd").getMatching == Nil
    )

  pureTest("isEmpty works"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)

    expect.all(
      inserted.isEmpty("") == false,
      inserted.isEmpty("a") == false,
      inserted.isEmpty("ab") == false,
      inserted.isEmpty("abc") == true
    )

  pureTest("replaceNodeAt and nodeAt works"):
    val replaced: PrefixMap[String, String, Int] =
      stringMap
        .replaceNodeAt("a", Map("A" -> 1, "B" -> 2))
        .replaceNodeAt("ab", Map("C" -> 3, "D" -> 4))

    expect.all(
      replaced.nodeAt("") == Map.empty,
      replaced.nodeAt("a") == Map("A" -> 1, "B" -> 2),
      replaced.nodeAt("ab") == Map("C" -> 3, "D" -> 4),
      replaced.nodeAt("abc") == Map.empty,
    )
