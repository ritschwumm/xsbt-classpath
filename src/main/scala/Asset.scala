package xsbtClasspath

import sbt._

import xsbtUtil._

case class Asset(
	main:Boolean,
	jar:File
) {
	val name:String					= jar.getName
	def flatPathMapping:PathMapping	= (jar, name)
}
