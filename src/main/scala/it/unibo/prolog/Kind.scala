package it.unibo.prolog

sealed trait Kind
case object Infrastructural extends Kind
case object Application extends Kind
