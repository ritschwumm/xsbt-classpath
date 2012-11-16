import sbt._

import Keys.Classpath
import Keys.TaskStreams
import Project.Initialize
import classpath.ClasspathUtilities

object ClasspathPlugin extends Plugin {
	private val outputName	= "classpath"
	private val cacheName	= "classpath"	// classpathAssets.key.label
	
	case class Asset(main:Boolean, fresh:Boolean, jar:File) {
		val name:String	= jar.getName
	}
	
	//------------------------------------------------------------------------------
	
	// library jars and jarred directories from the classpath 
	val classpathAssets	= TaskKey[Seq[Asset]]("classpath-assets")
	// where to put jar files
	val classpathOutput	= SettingKey[File]("classpath-output")
		
	// NOTE these need to be imported in build.sbt
	lazy val classpathSettings:Seq[Project.Setting[_]]	= Seq(
		classpathAssets	<<= assetsTask,
		classpathOutput	<<= Keys.crossTarget { _ / outputName }
	)
	
	//------------------------------------------------------------------------------
	
	// BETTER use dependencyClasspath and products instead of fullClasspath?
	// BETTER use exportedProducts instead of products?
	private def assetsTask:Initialize[Task[Seq[Asset]]]	= (
		Keys.streams,
		Keys.name,
		Keys.products in Runtime,
		Keys.fullClasspath in Runtime,
		Keys.cacheDirectory,
		classpathOutput
	) map assetsTaskImpl
		
	private def assetsTaskImpl(
		streams:TaskStreams,
		name:String,
		products:Seq[File],
		fullClasspath:Classpath,
		cacheDirectory:File,
		outputDirectory:File
	):Seq[Asset]	= {
		val (archives, directories)	= fullClasspath.files.distinct partition ClasspathUtilities.isArchive
		
		streams.log info ("creating classpath directory jars")
		val directoryAssets	= directories.zipWithIndex map { case (source, index) =>
			val main	= products contains source
			// TODO goes wrong if a dependency exists with the wrong name
			val cache	= cacheDirectory / cacheName / index.toString
			// BETTER use the name of the project the classes really come from
			val target	= outputDirectory / (name + "-" + index + ".jar")
			val fresh	= jarDirectory(source, cache, target)
			Asset(main, fresh, target)
		}
		
		streams.log info ("copying classpath library jars")
		val archiveAssets	= archives map { source =>
			val main	= products contains source
			val	target	= outputDirectory / source.getName 
			val fresh	= copyArchive(source, target)
			Asset(main, fresh, target)
		}
		
		val assets	= archiveAssets ++ directoryAssets
		val (freshAssets,unchangedAssets)	= assets partition { _.fresh }
		streams.log info (freshAssets.size + " fresh jars, " + unchangedAssets.size + " unchanged jars")
		
		assets
	}
	
	/** true if the jar has been created or overwritten because it was changed */
	private def copyArchive(sourceFile:File, targetFile:File):Boolean	= {
		val fresh	= sourceFile newerThan targetFile
		if (fresh) { IO copyFile (sourceFile, targetFile) }
		fresh
	}
	
	/** true if the jar has been created or overwritten because it was changed */
	private def jarDirectory(sourceDir:File, cacheDir:File, targetFile:File):Boolean	= {
		ClasspathJarUtil jarDirectory (sourceDir, cacheDir, targetFile)
	}
}
