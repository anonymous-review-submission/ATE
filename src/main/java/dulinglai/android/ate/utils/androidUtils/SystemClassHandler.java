package dulinglai.android.ate.utils.androidUtils;

import soot.RefType;
import soot.Type;

/**
 * Utility class for checking whether methods belong to system classes
 */
public class SystemClassHandler {

	/**
	 * Checks whether the given class name belongs to a system package
	 * 
	 * @param className
	 *            The class name to check
	 * @return True if the given class name belongs to a system package, otherwise
	 *         false
	 */
	public static boolean isClassInSystemPackage(String className) {
		return className.startsWith("android.") || className.startsWith("java.") || className.startsWith("javax.")
				|| className.startsWith("sun.") || className.startsWith("org.omg.")
				|| className.startsWith("org.w3c.dom.") || className.startsWith("com.google.");
	}

	/**
	 * Checks whether the type belongs to a system package
	 * 
	 * @param type
	 *            The type to check
	 * @return True if the given type belongs to a system package, otherwise false
	 */
	public static boolean isClassInSystemPackage(Type type) {
		if (type instanceof RefType)
			return isClassInSystemPackage(((RefType) type).getSootClass().getName());
		return false;
	}

}
