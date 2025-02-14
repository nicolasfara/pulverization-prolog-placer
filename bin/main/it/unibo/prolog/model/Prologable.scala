package it.unibo.prolog.model

trait Prologable {
  def toProlog: String
  val name: String
}
