package com.stoufexis.subtub

import weaver.*

import com.stoufexis.subtub.data.*

object PrefixMapSuite extends SimpleIOSuite:
  val stringMap: PrefixMap[String, String, Int] =
    PrefixMap.empty

  pureTest("getMatching returns all values matching any prefix"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("", "A", 1)
        .updateAt("a", "B", 2)
        .updateAt("ab", "C", 3)
        .updateAt("ad", "D", 4)
        .updateAt("fgh", "E", 5)

    val matchedByAll  = List(1, 2, 3)
    val matchedBySome = List(4)
    val matchedByOne  = List(5)

    expect.all(
      inserted.getMatching("") == matchedByAll ++ matchedBySome ++ matchedByOne,
      inserted.getMatching("a") == matchedByAll ++ matchedBySome,
      inserted.getMatching("ab") == matchedByAll,
      inserted.getMatching("abc") == matchedByAll,
      // Identical cases for PositionedPrefixMap
      PositionedPrefixMap(inserted, "").getMatching == matchedByAll ++ matchedBySome ++ matchedByOne,
      PositionedPrefixMap(inserted, "a").getMatching == matchedByAll ++ matchedBySome,
      PositionedPrefixMap(inserted, "ab").getMatching == matchedByAll,
      PositionedPrefixMap(inserted, "abc").getMatching == matchedByAll
    )

  pureTest("updateAt at the same spot aggregates elements"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("a", "C", 3)
        .updateAt("ab", "D", 4)

    val matchedByAll = List(1, 3, 2, 4)

    expect.all(
      inserted.getMatching("") == matchedByAll,
      inserted.getMatching("a") == matchedByAll,
      inserted.getMatching("ab") == matchedByAll,
      inserted.getMatching("abc") == matchedByAll,
      inserted.nodeAt("") == Map.empty,
      inserted.nodeAt("a") == Map("A" -> 1, "C" -> 3),
      inserted.nodeAt("ab") == Map("B" -> 2, "D" -> 4),
      inserted.nodeAt("abc") == Map.empty
    )

  pureTest("simple removal works"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("abc", "C", 3)

    expect.all(
      inserted.getMatching("") == List(1, 2, 3), // sanity check
      inserted.removeAt("a", "B").getMatching("") == List(1, 2, 3), // should remove nothing
      inserted.removeAt("ab", "B").getMatching("") == List(1, 3) // should successfully remove
    )

    val removedAll: PrefixMap[String, String, Int] =
      inserted
        .removeAt("a", "A")
        .removeAt("ab", "B")
        .removeAt("abc", "C")

    expect(removedAll.getMatching("") == Nil && removedAll.isEmpty) // also verifies that simple isEmpty works

  pureTest("replaceNodeAt works"):
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
