package xsbtClasspath

import scala.annotation.tailrec

import sbt._
import Keys.Classpath
import Keys.TaskStreams
import classpath.ClasspathUtilities
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
							products		= (Keys.products in Runtime).value,
							fullClasspath	= (Keys.fullClasspath in Runtime).value,
							buildDir		= classpathBuildDir.value
						),
						
				classpathBuildDir	:= Keys.crossTarget.value / "classpath"
			)
	
	//------------------------------------------------------------------------------
	//## tasks
	
	// BETTER use dependencyClasspath and products/exportedProducts instead of fullClasspath?
	// BETTER use exportedProducts instead of products?
	//	that's a Classpath aka Seq[Attributed[File]] instead of Seq[File]
	//	Classpath#files and Build.data can extract the data
	private def assetsTask(
		streams:TaskStreams,
		name:String,
		products:Seq[File],
		fullClasspath:Classpath,
		buildDir:File
	):Seq[Asset]	= {
		// BETTER warn about non-existing?
		val (directories, archives)	=
				fullClasspath.files.distinct filter { _.exists } partition { _.isDirectory }
		
		val archiveAssets	=
				archives map { source =>
					val main	= products contains source
					Asset(main, source)
				}
		
		streams.log info s"creating classpath directory jars in ${buildDir}"
		val (directoryAssets, freshFlags)	=
				directories.zipWithIndex
				.map { case (source, index) =>
					val main	= products contains source
					val cache	= streams.cacheDirectory / cacheName / index.toString
					// ensure the jarfile does not clash with any of the archive assets from above
					@tailrec
					def newTarget(resolve:Int):File	= {
						// BETTER use the name of the project the classes really come from
						val candidate	=
								buildDir /
								(name + "-" + index + (if (resolve != 0) "-" + resolve else "") + ".jar")
						if (archives contains candidate)	newTarget(resolve+1)
						else								candidate
					}
					val target	= newTarget(0)
					val fresh	= JarUtil jarDirectory (source, cache, target)
					(Asset(main, target), fresh)
				}
				.unzip
				
		val assets					= archiveAssets ++ directoryAssets
		val (changed, unchanged)	= freshFlags partition identity
		streams.log info s"classpath directory jars: ${changed.size} new/changed, ${unchanged.size} unchanged"
		
		assets
	}
}
