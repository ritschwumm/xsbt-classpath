package xsbtClasspath

import sbt._

final case class Asset(
	file:File,
	name:String,
	main:Boolean
) {
	def flatPathMapping:(File,String)	= (file, name)
}
