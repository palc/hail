package org.broadinstitute.hail.utils.richUtils

class RichMap[K, V](val m: Map[K, V]) extends AnyVal {
  def mapValuesWithKeys[T](f: (K, V) => T): Map[K, T] = m map { case (k, v) => (k, f(k, v)) }

  def force = m.map(identity) // needed to make serializable: https://issues.scala-lang.org/browse/SI-7005

  def outerJoin[V2](other: Map[K, V2]): Map[K, (Option[V], Option[V2])] = {
    (m.keySet ++ other.keySet).map { k => (k, (m.get(k), other.get(k))) }.toMap
  }
}
