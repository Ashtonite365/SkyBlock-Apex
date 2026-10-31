package dev.ashtonite365.apex.javaplugin;

import net.fabricmc.stitch.commands.tinyv2.TinyClass;
import net.fabricmc.stitch.commands.tinyv2.TinyFile;
import net.fabricmc.stitch.commands.tinyv2.TinyMethod;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public class MappingTree {

	private final @Nullable Map<String, TinyClass> classLookup;
	private final int targetIndex;
	private final int sourceIndex;

	public MappingTree(@Nullable TinyFile tinyV2File, String sourceNamespace, String targetNamespace) {
		if (tinyV2File == null) {
			classLookup = null;
			targetIndex = sourceIndex = -1;
			return;
		}
		sourceIndex = tinyV2File.getHeader().getNamespaces().indexOf(sourceNamespace);
		if (sourceIndex < 0)
			throw new RuntimeException("Could not find source namespace " + sourceNamespace + " in mappings file.");
		this.classLookup = tinyV2File
			.getClassEntries()
			.stream()
			.collect(Collectors.toMap(it -> it.getClassNames().get(sourceIndex), it -> it));
		targetIndex = tinyV2File.getHeader().getNamespaces().indexOf(targetNamespace);
		if (targetIndex < 0)
			throw new RuntimeException("Could not find target namespace " + targetNamespace + " in mappings file.");
	}

	public record MethodCoordinate(
		String interClassName,
		String interMethodName
	) {
	}

	public MethodCoordinate resolveMethodToIntermediary(String className, String methodName) {
		if (classLookup == null) return new MethodCoordinate(className, methodName);
		var classData = classLookup.get(className.replace(".", "/"));
		TinyMethod candidate = null;
		for (TinyMethod method : classData.getMethods()) {
			if (method.getMethodNames().get(sourceIndex).equals(methodName)) {
				if (candidate != null) {
					throw new RuntimeException("Found two candidates for method " + className + "." + methodName);
				}
				candidate = method;
			}
		}
		if (candidate == null)
			throw new RuntimeException("Couldd not find candidate for method " + className + "." + methodName);
		return new MethodCoordinate(
			classData.getClassNames().get(targetIndex),
			candidate.getMethodNames().get(targetIndex));
	}

	public String resolveClassToIntermediary(String className) {
		if (classLookup == null) return className;
		var cls = classLookup.get(className.replace(".", "/"));
		if (cls == null) {
			return null;
		}
		return cls.getClassNames().get(targetIndex)
			.replace("/", ".");
	}
}
