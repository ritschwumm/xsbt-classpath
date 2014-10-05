import scala.annotation.tailrec

import sbt._

import Keys.Classpath
import Keys.TaskStreams
import Project.Initialize
import classpath.ClasspathUtilities

import xsbtUtil._

object ClasspathPlugin extends Plugin {
	// classpathAssets.key.label
	private val jarDirName	= "classpath"
	private val cacheName	= "classpath"
	
	//------------------------------------------------------------------------------
	
	case class ClasspathAsset(
		main:Boolean,
		jar:File
	) {
		val name:String					= jar.getName
		def flatPathMapping:PathMapping	= (jar, name)
	}
	
	val classpathAssets	= taskKey[Seq[ClasspathAsset]]("library jars and jarred directories from the classpath as ClasspathAsset items")
	val classpathJarDir	= settingKey[File]("where to store jars made from directories in the classpath")
		
	// NOTE these need to be imported in build.sbt
	lazy val classpathSettings:Seq[Def.Setting[_]]	=
			Vector(
				classpathJarDir	:= Keys.crossTarget.value / jarDirName,
				classpathAssets	:= 
						assetsTask(
							streams			= Keys.streams.value,
							name			= Keys.name.value,
							products		= (Keys.products in Runtime).value,
							fullClasspath	= (Keys.fullClasspath in Runtime).value,
							jarDir			= classpathJarDir.value
						)
			)
	
	//------------------------------------------------------------------------------
	
	// BETTER use dependencyClasspath and products/exportedProducts instead of fullClasspath?
	// BETTER use exportedProducts instead of products?
	//	that's a Classpath aka Seq[Attributed[File]] instead of Seq[File]
	//	Classpath#files and Build.data can extract the data
	private def assetsTask(
		streams:TaskStreams,
		name:String,
		products:Seq[File],
		fullClasspath:Classpath,
		jarDir:File
	):Seq[ClasspathAsset]	= {
		// BETTER warn about non-existing?
		val (directories, archives)	=
				fullClasspath.files.distinct filter { _.exists } partition { _.isDirectory }
		
		val archiveAssets	=
				archives map { source =>
					val main	= products contains source
					ClasspathAsset(main, source)
				}
		
		streams.log info s"creating classpath directory jars in $jarDir}"
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
								jarDir /
								(name + "-" + index + (if (resolve != 0) "-" + resolve else "") + ".jar")
						if (archives contains candidate)	newTarget(resolve+1)
						else								candidate
					}
					val target	= newTarget(0)
					val fresh	= ClasspathJarUtil jarDirectory (source, cache, target)
					(ClasspathAsset(main, target), fresh)
				}
				.unzip
				
		val assets					= archiveAssets ++ directoryAssets
		val (changed, unchanged)	= freshFlags partition identity
		streams.log info s"classpath directory jars: ${changed.size} new/changed, ${unchanged.size} unchanged"
		
		assets
	}
}
