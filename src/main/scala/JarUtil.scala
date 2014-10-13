package xsbtClasspath

import sbt._

import Predef.{conforms => _, _}
import collection.JavaConversions._
import Types.:+:

import sbinary.{DefaultProtocol,Format}
import DefaultProtocol.{FileFormat, immutableMapFormat, StringFormat, UnitFormat}
import Cache.{defaultEquiv, hConsCache, hNilCache, streamFormat, wrapIn}
import Tracked.{inputChanged, outputChanged}
import FileInfo.exists
import FilesInfo.lastModified

import xsbtUtil.util.find

// @see sbt.Package.apply
object JarUtil {
	/** true when the jar has been created or overwritten because it was changed */
	def jarDirectory(sourceDir:File, cacheDir:File, targetFile:File):Boolean	= {
		implicit def stringMapEquiv:Equiv[Map[File,String]]	= defaultEquiv
		
		val sources	= find filesMapped sourceDir
		
		def makeJar(sources:Seq[(File,String)], jar:File) {
			IO delete jar
			IO zip (sources, jar)
		}
		
		val cachedMakeJar	=
				inputChanged(cacheDir / "inputs") { (inChanged, inputs:(Map[File,String] :+: FilesInfo[ModifiedFileInfo] :+: HNil)) =>
					val sources :+: _ :+: HNil = inputs
					outputChanged(cacheDir / "output") { (outChanged, jar:PlainFileInfo) =>
						val fresh	= inChanged || outChanged
						if (fresh) {
							makeJar(sources.toVector, jar.file)
						}
						fresh
					}
				}
		val sourcesMap		= sources.toMap
		val inputs			= sourcesMap :+: lastModified(sourcesMap.keySet.toSet) :+: HNil
		val fresh:Boolean	= cachedMakeJar(inputs) { () => exists(targetFile) }
		fresh
	}
}
