package xsbtClasspath

import scala.annotation.tailrec

import sbt._
import Keys.Classpath
import Keys.TaskStreams
//import classpath.ClasspathUtilities
import plugins.JvmPlugin

object Import {
	val classpathAssets		= taskKey[Seq[Asset]]("library jars and jarred directories from the classpath as ClasspathAsset items")

	val classpathBuildDir	= settingKey[File]("where to store jars made from directories in the classpath")
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
							streams			= Keys.streams.value,
							name			= Keys.name.value,
							version			= Keys.version.value,
							products		= (Keys.products in Runtime).value,
							fullClasspath	= (Keys.fullClasspath in Runtime).value,
							buildDir		= classpathBuildDir.value
						),
						
				classpathBuildDir	:= Keys.crossTarget.value / "classpath"
			)
	
	//------------------------------------------------------------------------------
	//## tasks
	
	private def assetsTask(
		streams:TaskStreams,
		name:String,
		version:String,
		products:Seq[File],
		fullClasspath:Classpath,
		buildDir:File
	):Seq[Asset]	= {
		case class RawAsset(file:File, name:String, main:Boolean, archive:Boolean)
		
		def rawAsset(attrd:Attributed[File]):Option[RawAsset]	= {
			val source	= attrd.data
			val archive	= !source.isDirectory
			if (source.exists) {
				Some(
					RawAsset(
						file	= source,
						name	= if (archive) source.getName else syntheticName(attrd),
						main	= products contains source,
						archive	= archive
					)
				)
			}
			else {
				streams.log warn s"classpath component $source does not exist"
				None
			}
		}
		
		def syntheticName(dir:Attributed[File]):String	= {
			val moduleId	= dir get	Keys.moduleID.key
			val artifact	= dir get	Keys.artifact.key
			val aName		= moduleId map { _.name			} getOrElse name
			val aVersion	= moduleId map { _.revision		} getOrElse version
			val aExtension	= artifact map { _.extension	} getOrElse "jar"
			aName + "-" + aVersion + "." + aExtension
		}
		
		val (archives, directories)	=
				fullClasspath flatMap rawAsset partition { _.archive }
		
		val archiveAssets:Seq[Asset]	=
				archives map { raw =>
					Asset(raw.file, raw.name, raw.main)
				}
		
		streams.log info s"creating classpath directory jars in ${buildDir}"
		val (directoryAssets, freshFlags)	=
				directories.zipWithIndex
				.map { case (raw, index) =>
					val cache	= streams.cacheDirectory / cacheName / index.toString
					val target	= buildDir / (index.toString + ".jar")
					val fresh	= JarUtil jarDirectory (raw.file, cache, target)
					val asset	= Asset(target, raw.name, raw.main)
					asset -> fresh
				}
				.unzip
				
		val (changed, unchanged)	= freshFlags partition identity
		streams.log info s"classpath directory jars: ${changed.size} new/changed, ${unchanged.size} unchanged"
		
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
		unclash(archiveAssets ++ directoryAssets, Set.empty)
	}
}
