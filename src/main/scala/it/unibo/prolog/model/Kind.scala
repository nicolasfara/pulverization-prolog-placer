package it.unibo.prolog.model

sealed trait Kind
case object Infrastructural extends Kind
case object Application extends Kind
