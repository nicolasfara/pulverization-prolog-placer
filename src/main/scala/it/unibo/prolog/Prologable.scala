package it.unibo.prolog

trait Prologable {
  def toProlog: String
  val name: String
}
