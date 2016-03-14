package xsbtClasspath

import sbt._

import xsbtUtil.types._

case class Asset(
	file:File,
	name:String,
	main:Boolean
) {
	def flatPathMapping:PathMapping	= (file, name)
}
