
package de.fraunhofer.aisec.analysis.utils;

import de.fraunhofer.aisec.analysis.scp.ConstantResolver;
import de.fraunhofer.aisec.cpg.graph.FunctionDeclaration;
import de.fraunhofer.aisec.cpg.graph.MethodDeclaration;
import de.fraunhofer.aisec.cpg.graph.Node;
import de.fraunhofer.aisec.cpg.graph.RecordDeclaration;
import de.fraunhofer.aisec.crymlin.CrymlinQueryWrapper;
import de.fraunhofer.aisec.crymlin.connectors.db.OverflowDatabase;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

public class Utils {
	private static final Logger log = LoggerFactory.getLogger(Utils.class);

	// do not instantiate
	private Utils() {
	}

	/**
	 * Returns the fully qualified signature of a method within a record declaration (e.g., a Java class).
	 */
	public static String toFullyQualifiedSignature(RecordDeclaration r, MethodDeclaration m) {
		return r.getName() + "." + m.getSignature();
	}

	/**
	 * Return a unified type String (i.e. changeing cpp-type-separators to Java-type-separators)
	 *
	 * @param name input type
	 * @return a type string which is separated via "."
	 */
	public static String unifyType(@NonNull String name) {
		return name.replace("::", ".");
	}

	public static String extractMethodName(String opName) {
		if (opName.contains("::")) {
			opName = opName.substring(opName.lastIndexOf("::") + 2);
		} else if (opName.contains("->")) {
			opName = opName.substring(opName.lastIndexOf("->") + 2);
		} else if (opName.contains(".")) {
			opName = opName.substring(opName.lastIndexOf('.') + 1);
		}
		return opName;
	}

	public static String extractType(String opName) {
		if (opName.contains("::")) {
			opName = opName.substring(0, opName.lastIndexOf("::"));
		} else if (opName.contains("->")) {
			opName = opName.substring(0, opName.lastIndexOf("->"));
		} else if (opName.contains(".")) {
			opName = opName.substring(0, opName.lastIndexOf('.'));
		} else {
			opName = "";
		}
		return opName;
	}

	public static String stripQuotedString(String s) {
		if (s.startsWith("\"") && s.endsWith("\"")) {
			s = s.substring(1, s.length() - 1);
		}
		return s;
	}

	public static String stripQuotedCharacter(String s) {
		if (s.startsWith("\'") && s.endsWith("\'")) {
			// there should be only a single character here
			s = s.substring(1, s.length() - 1);
		}
		return s;
	}

	public static void dumpVertices(Collection<Vertex> vertices) {
		log.debug("Dumping vertices: {}", vertices.size());

		int i = 0;
		for (Vertex v : vertices) {
			log.debug("Vertex {}: {}", i++, v);
		}
	}

	public static void dumpPaths(Collection<Path> paths) {
		log.debug("Number of paths: {}", paths.size());

		for (Path p : paths) {
			log.debug("Path of length: {}", p.size());
			for (Object o : p) {
				log.debug("Path step: {}", o);
			}
		}
	}

	/**
	 * Returns true if the given vertex has a label that equals to the given CPG class or any of its subclasses.
	 *
	 * That is, hasLabel(v, Node.class) will always return true.
	 *
	 * @param v 			A vertex with a label.
	 * @param cpgClass		Any class from the CPG hierarchy.
	 * @return
	 */
	public static boolean hasLabel(@NonNull Vertex v, @NonNull Class<? extends Node> cpgClass) {
		String label = v.label();
		Set<String> subClasses = Set.of(OverflowDatabase.getSubclasses(cpgClass));
		return label.equals(cpgClass.getSimpleName()) || subClasses.contains(label);
	}

	/**
	 * Returns true if a type from the source language is equal to a type in MARK.
	 *
	 * Namespaces are ignored.
	 *
	 * For instance:
	 *
	 * isSubTypeOf("uint8", "uint8")  -> true
	 * isSubTypeOf("string", "std.string")  -> true
	 * isSubTypeOf("std::string", "std.string")  -> true
	 * isSubTypeOf("random::string", "std.string")  -> true
	 *
	 * @param sourceType
	 * @param markType
	 * @return
	 */
	public static boolean isSubTypeOf(String sourceType, String markType) {
		String uniSource = unifyType(sourceType);
		if (uniSource.contains(".") && !uniSource.endsWith(".")) {
			uniSource = uniSource.substring(uniSource.lastIndexOf('.') + 1);
		}
		// TODO Currently, the "type" property may contain modifiers such as "const", so we must remove them here. Will change in CPG.
		if (uniSource.contains(" ") && !uniSource.endsWith(" ")) {
			uniSource = uniSource.substring(uniSource.lastIndexOf(' ') + 1);
		}

		String uniMark = unifyType(markType);
		if (uniMark.contains(".") && !uniMark.endsWith(".")) {
			uniMark = uniMark.substring(uniMark.lastIndexOf('.') + 1);
		}

		// TODO We do not consider type hierarchies here but simply match for equality plus a few manual mappings
		boolean result = uniSource.equals(uniMark);
		// There are various representations of "string" and we map them manually here.
		if (uniMark.equals("string")) {
			switch (uniSource) {
				case "QString":
					result = true;
					break;
			}
		}

		// If type could not be determined, we err on the false positive side.
		if (sourceType.equals("UNKNOWN")) {
			result = true;
		}
		log.debug("{} is a subtype of {}: {}", sourceType, markType, result);

		return result;
	}
}
