package com.stoufexis.subtub

import cats.data.Chain
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

    val matchedByAll  = Chain(1, 2, 3)
    val matchedBySome = Chain(4)
    val matchedByOne  = Chain(5)

    expect.all(
      inserted.getMatching("") == matchedByAll ++ matchedBySome ++ matchedByOne,
      inserted.getMatching("a") == matchedByAll ++ matchedBySome,
      inserted.getMatching("ab") == matchedByAll,
      inserted.getMatching("abc") == matchedByAll
    )

  pureTest("updateAt at the same spot aggregates elements"):
    val inserted: PrefixMap[String, String, Int] =
      stringMap
        .updateAt("a", "A", 1)
        .updateAt("ab", "B", 2)
        .updateAt("a", "C", 3)
        .updateAt("ab", "D", 4)

    val matchedByAll = Chain(1, 3, 2, 4)

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
      inserted.getMatching("") == Chain(1, 2, 3),                    // sanity check
      inserted.removeAt("a", "B").getMatching("") == Chain(1, 2, 3), // should remove nothing
      inserted.removeAt("ab", "B").getMatching("") == Chain(1, 3)    // should successfully remove
    )

    val removedAll: PrefixMap[String, String, Int] =
      inserted
        .removeAt("a", "A")
        .removeAt("ab", "B")
        .removeAt("abc", "C")

    expect(
      removedAll.getMatching("") == Chain.empty && removedAll.isEmpty
    ) // also verifies that simple isEmpty works
