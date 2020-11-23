package xsbtClasspath

import scala.annotation.tailrec

import sbt._
import Keys.Classpath
import Keys.TaskStreams
import plugins.JvmPlugin

object Import {
	val classpathAssets		= taskKey[Seq[Asset]]("library jars and jarred directories from the classpath as ClasspathAsset items")
}

object ClasspathPlugin extends AutoPlugin {
	//------------------------------------------------------------------------------
	//## constants

	// classpathAssets.key.label
	private val cacheName	= "classpath"

	//------------------------------------------------------------------------------
	//## exports

	override val requires:Plugins		= JvmPlugin

	override val trigger:PluginTrigger	= allRequirements

	lazy val autoImport	= Import
	import autoImport._

	override lazy val projectSettings:Seq[Def.Setting[_]]	=
		Vector(
			classpathAssets	:=
				assetsTask(
					streams				= Keys.streams.value,
					name				= Keys.name.value,
					version				= Keys.version.value,
					exportedProductJars	= (Runtime / Keys.exportedProductJars).value,
					fullClasspathAsJars	= (Runtime / Keys.fullClasspathAsJars).value,
				),
		)

	//------------------------------------------------------------------------------
	//## tasks

	private def assetsTask(
		streams:TaskStreams,
		name:String,
		version:String,
		exportedProductJars:Classpath,
		fullClasspathAsJars:Classpath,
	):Seq[Asset]	= {
		def rawAsset(attrd:Attributed[File]):Option[Asset]	= {
			val source	= attrd.data
			if (source.exists) {
				Some(
					Asset(
						file	= source,
						name	= source.getName,
						main	= exportedProductJars contains attrd,
					)
				)
			}
			else {
				streams.log warn s"classpath component $source does not exist"
				None
			}
		}

		val archiveAssets	= fullClasspathAsJars flatMap rawAsset

		def disambiguate(name:String, used:Set[String]):String	= {
			def loop(index:Int):String	= {
				val prefix	= if (index == 0) "" else index.toString + "-"
				val cand	= prefix + name
				if (used contains cand)	loop(index+1)
				else					cand
			}
			loop(0)
		}

		def unclash(assets:Seq[Asset], used:Set[String]):Seq[Asset]	=
			assets match {
				case head +: tail	=>
					val newName		= disambiguate(head.name, used)
					val newAsset	= head copy (name = newName)
					val newUsed		= used + newName
					newAsset +: unclash(tail, newUsed)
				case _				=>
					Vector.empty
			}

		// ensure asset names are unambiguous
		unclash(archiveAssets, Set.empty)
	}
}
