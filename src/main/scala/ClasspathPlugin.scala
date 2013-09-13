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
			Seq(
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
		val (archives, directories)	= fullClasspath.files.distinct partition ClasspathUtilities.isArchive
		
		streams.log info ("copying classpath library jars to " + outputDirectory)
		val archiveAssets	= archives map { source =>
			val main	= products contains source
			val	target	= outputDirectory / source.getName 
			val fresh	= copyArchive(source, target)
			ClasspathAsset(main, fresh, target)
		}
		
		// to find out about name clashes
		val archiveTargets	= archiveAssets map { _.jar } toSet;
		
		streams.log info ("creating classpath directory jars in " + outputDirectory)
		val directoryAssets	= directories.zipWithIndex map { case (source, index) =>
			val main	= products contains source
			val cache	= streams.cacheDirectory / cacheName / index.toString
			// ensure the jarfile does not clash with any of the archive assets from above 
			def newTarget(resolve:Int):File	= {
				val candidate	= mkTarget(resolve)
				if (archiveTargets contains candidate)	newTarget(resolve+1)
				else									candidate
			}
			// BETTER use the name of the project the classes really come from
			def mkTarget(resolve:Int):File	= 
					outputDirectory / (name + "-" + index + (if (resolve != 0) "-" + resolve else "") + ".jar")
			val target	= newTarget(0)
			val fresh	= jarDirectory(source, cache, target)
			ClasspathAsset(main, fresh, target)
		}
		
		val assets	= archiveAssets ++ directoryAssets
		val (updatedAssets, unchangedAssets)	= assets partition { _.updated }
		streams.log info ("classpath jars: " + updatedAssets.size + " new/changed, " + unchangedAssets.size + " unchanged")
		
		assets
	}
	
	/** true if the jar has been created or overwritten because it was changed */
	private def jarDirectory(sourceDir:File, cacheDir:File, targetFile:File):Boolean	= {
		ClasspathJarUtil jarDirectory (sourceDir, cacheDir, targetFile)
	}
	
	/** true if the jar has been created or overwritten because it was changed */
	private def copyArchive(sourceFile:File, targetFile:File):Boolean	= {
		val fresh	= sourceFile newerThan targetFile
		if (fresh) { IO copyFile (sourceFile, targetFile) }
		fresh
	}
}
