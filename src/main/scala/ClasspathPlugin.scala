import scala.annotation.tailrec

import sbt._

import Keys.Classpath
import Keys.TaskStreams
import Project.Initialize
import classpath.ClasspathUtilities

object ClasspathPlugin extends Plugin {
	private val outputName	= "classpath"
	private val cacheName	= "classpath"	// classpathAssets.key.label
	
	//------------------------------------------------------------------------------
	
	case class ClasspathAsset(
		main:Boolean,
		updated:Boolean,
		jar:File
	) {
		val name:String	= jar.getName
	}
	
	// library jars and jarred directories from the classpath 
	val classpathAssets	= TaskKey[Seq[ClasspathAsset]]("classpath-assets")
	// where to put jar files
	val classpathOutput	= SettingKey[File]("classpath-output")
		
	// NOTE these need to be imported in build.sbt
	lazy val classpathSettings:Seq[Def.Setting[_]]	=
			Vector(
				classpathAssets	<<= assetsTask,
				classpathOutput	<<= Keys.crossTarget { _ / outputName }
			)
	
	//------------------------------------------------------------------------------
	
	// BETTER use dependencyClasspath and products/exportedProducts instead of fullClasspath?
	// BETTER use exportedProducts instead of products?
	//	that's a Classpath aka Seq[Attributed[File]] instead of Seq[File]
	//	Classpath#files and Build.data can extract the data
	private def assetsTask:Def.Initialize[Task[Seq[ClasspathAsset]]]	= (
		Keys.streams,
		Keys.name,
		Keys.products in Runtime,
		Keys.fullClasspath in Runtime,
		classpathOutput
	) map assetsTaskImpl
		
	private def assetsTaskImpl(
		streams:TaskStreams,
		name:String,
		products:Seq[File],
		fullClasspath:Classpath,
		outputDirectory:File
	):Seq[ClasspathAsset]	= {
		// BETTER warn about non-existing?
		val (directories, archives)	=
				fullClasspath.files.distinct filter { _.exists } partition { _.isDirectory }
		
		streams.log info s"copying classpath library jars to ${outputDirectory}"
		val archiveAssets	=
				archives map { source =>
					val main	= products contains source
					val	target	= outputDirectory / source.getName 
					val fresh	= source newerThan target
					if (fresh) {
						IO copyFile (source, target) 
					}
					ClasspathAsset(main, fresh, target)
				}
		
		// to find out about name clashes
		val archiveTargets	= archiveAssets map { _.jar } toSet;
		
		streams.log info s"creating classpath directory jars in ${outputDirectory}"
		val directoryAssets	=
				directories.zipWithIndex map { case (source, index) =>
					val main	= products contains source
					val cache	= streams.cacheDirectory / cacheName / index.toString
					// ensure the jarfile does not clash with any of the archive assets from above 
					@tailrec
					def newTarget(resolve:Int):File	= {
						// BETTER use the name of the project the classes really come from
						val candidate	=
								outputDirectory /
								(name + "-" + index + (if (resolve != 0) "-" + resolve else "") + ".jar")
						if (archiveTargets contains candidate)	newTarget(resolve+1)
						else									candidate
					}
					val target	= newTarget(0)
					val fresh	= ClasspathJarUtil jarDirectory (source, cache, target)
					ClasspathAsset(main, fresh, target)
				}
				
		val assets	= archiveAssets ++ directoryAssets
		val (changed, unchanged)	= assets partition { _.updated }
		streams.log info s"classpath jars: ${changed.size} new/changed, ${unchanged.size} unchanged"
		
		assets
	}
}
